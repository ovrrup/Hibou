package com.agy.runtime

import android.content.Context
import android.util.Log
import com.agy.install.EnvInstaller
import com.agy.ru.HibouConnect
import com.agy.util.AssetUnpacker
import com.agy.util.AtomicFileWriter
import com.agy.util.FileModeUtil
import com.agy.util.IoUtil
import com.agy.util.Sha256
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream

/**
 * Extracts `assets/runtime` (the glibc engine bundle) and `assets/web` into the
 * app files dir, verifies integrity, then wires shell bridges, network config
 * and onboarding state.
 *
 * Ported 1:1 from decompiled `com/agy/runtime/CoreInstaller.java`.
 */
object CoreInstaller {
    private const val TAG = "CoreInstaller"

    @JvmStatic
    fun installCore(runtimeManager: RuntimeManager) {
        val appContext = runtimeManager.appContext()
        val runtimeDir = runtimeManager.getRuntimeDir()
        val manifestHash = getAssetManifestHash(appContext)
        if (!isRuntimeUpToDate(runtimeManager, manifestHash)) {
            Log.i(TAG, "extracting assets/runtime (first launch or runtime update)")
            try {
                wipe(runtimeManager, runtimeDir)
                AssetUnpacker.unpackTree(appContext.assets, RuntimePaths.RUNTIME_ASSET, runtimeDir)
                FileModeUtil.recursiveSetReadableExecutable(runtimeManager.getBinDir())
                FileModeUtil.recursiveSetReadableExecutable(runtimeManager.getLibDir())
                val verifyError = ManifestVerifier.verifyError(appContext, runtimeDir)
                if (verifyError != null) {
                    throw IllegalStateException("runtime integrity check failed: $verifyError")
                }
                writeMarker(runtimeManager.getRuntimeDir(), ".installed", manifestHash)
            } catch (e: IOException) {
                throw IllegalStateException(
                    "cannot unpack assets/runtime into $runtimeDir: " + e.message, e
                )
            }
        } else {
            Log.i(TAG, "runtime up to date (manifest hash matched); skipping extract")
        }
        if (!isWebExtracted(runtimeManager)) {
            try {
                AssetUnpacker.unpackTree(appContext.assets, RuntimePaths.WEB_ASSET, runtimeManager.getWebDir())
                writeMarker(runtimeManager.getWebDir(), RuntimePaths.WEB_MARKER)
            } catch (e: IOException) {
                throw IllegalStateException(
                    "cannot unpack assets/web into " + runtimeManager.getWebDir() + ": " + e.message, e
                )
            }
        }
        runtimeManager.writeNetworkConfig()
        runtimeManager.startNetworkMonitor()
        OnboardingSeeder.seed(appContext, runtimeManager.getAppDataDir(), runtimeManager.getProjectsDir())
        ShellBridgeInstaller.installFallbackBridges(runtimeManager.getBinDir(), appContext)
        ShellBridgeInstaller.installOpenUrlShims(runtimeManager.getBinDir(), appContext)
        if (EnvInstaller.isInstalled(appContext)) {
            ShellBridgeInstaller.install(runtimeManager)
        }
    }

    private fun isRuntimeUpToDate(runtimeManager: RuntimeManager, manifestHash: String): Boolean {
        if (manifestHash.isEmpty()) return false
        val marker = File(runtimeManager.getRuntimeDir(), ".installed")
        val core = File(runtimeManager.getBinDir(), RuntimePaths.CORE_NAME)
        val loader = File(runtimeManager.getBinDir(), RuntimePaths.LOADER_NAME)
        val compat = File(runtimeManager.getLibDir(), "liblse_compat.so")
        if (!marker.isFile || !core.isFile || !loader.isFile || !compat.isFile) {
            return false
        }
        return try {
            manifestHash == IoUtil.readAll(FileInputStream(marker), "UTF-8").trim()
        } catch (_: Exception) {
            false
        }
    }

    private fun getAssetManifestHash(context: Context): String {
        return try {
            val input: InputStream = context.assets.open(RuntimePaths.MANIFEST_ASSET)
            try {
                val hash = Sha256.of(input)
                input.close()
                hash
            } finally {
            }
        } catch (e: Exception) {
            Log.w(TAG, "cannot hash asset manifest: " + e.message)
            ""
        }
    }

    private fun isWebExtracted(runtimeManager: RuntimeManager): Boolean {
        val marker = File(runtimeManager.getWebDir(), RuntimePaths.WEB_MARKER)
        if (!marker.isFile || !File(runtimeManager.getWebDir(), "main.js").isFile) {
            return false
        }
        return try {
            val lastUpdate = runtimeManager.appContext().packageManager
                .getPackageInfo(runtimeManager.appContext().packageName, 0).lastUpdateTime
            if (marker.lastModified() < lastUpdate) {
                Log.i(TAG, "APK updated; refreshing assets/web")
                false
            } else {
                true
            }
        } catch (_: Exception) {
            true
        }
    }

    private fun writeMarker(dir: File, name: String) {
        writeMarker(dir, name, HibouConnect.PROTOCOL_VERSION)
    }

    private fun writeMarker(dir: File, name: String, content: String) {
        try {
            AtomicFileWriter.writeText(File(dir, name), "$content\n")
        } catch (e: Exception) {
            Log.w(TAG, "cannot write marker $name: " + e.message)
        }
    }

    @Throws(IOException::class)
    private fun wipe(runtimeManager: RuntimeManager, file: File) {
        assertSafeToWipe(runtimeManager, file)
        if (!file.exists()) {
            return
        }
        val children = file.listFiles()
        if (children != null) {
            for (child in children) {
                wipe(runtimeManager, child)
            }
        }
        if (!file.delete()) {
            throw IOException("cannot delete $file")
        }
    }

    @Throws(IOException::class)
    private fun assertSafeToWipe(runtimeManager: RuntimeManager, file: File) {
        val filesDir = runtimeManager.appContext().filesDir
        val filesDirPath = filesDir.canonicalPath
        val geminiPath = File(filesDir, ".gemini").canonicalPath
        val targetPath = file.canonicalPath
        if (!targetPath.startsWith(filesDirPath + File.separator)) {
            throw IOException("refusing to wipe outside filesDir: $file")
        }
        if (targetPath == geminiPath ||
            targetPath.startsWith(geminiPath + File.separator) ||
            geminiPath.startsWith(targetPath + File.separator)
        ) {
            throw IOException("refusing to wipe user data under .gemini: $file")
        }
    }
}
