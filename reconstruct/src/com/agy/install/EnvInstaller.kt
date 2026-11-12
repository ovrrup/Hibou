package com.agy.install

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import com.agy.util.AssetPicker
import com.agy.util.AtomicFileWriter
import com.agy.util.FileModeUtil
import com.agy.util.PathSafety
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.Properties
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Installs and maintains the bionic env prefix (`files/usr`):
 * fresh atomic install from the bootstrap archive, plus managed-layer
 * refreshes (overlay/pkg) for existing prefixes, with smoke tests and
 * quarantine on failure.
 *
 * Ported 1:1 from decompiled `com/agy/install/EnvInstaller.java`.
 */
object EnvInstaller {
    private const val STATE_SCHEMA = "2"
    private const val TAG = "EnvInstaller"

    @JvmStatic
    fun prefix(context: Context): File {
        return EnvPaths.prefix(context)
    }

    @JvmStatic
    fun isInstalled(context: Context): Boolean {
        val prefix = prefix(context)
        return File(prefix, EnvPaths.MARKER).isFile && File(prefix, EnvPaths.BASH_REL).isFile
    }

    @JvmStatic
    @Synchronized
    fun install(context0: Context?): Boolean {
        var context = context0
        try {
            if (context == null) {
                throw IllegalArgumentException("null context")
            }
            val appContext = context.applicationContext
            if (appContext != null) {
                context = appContext
            }
            val assets = context.assets
            val prefix = prefix(context)
            validatePrefixLocation(context, prefix)
            val manifest = EnvBundleManifest.load(assets)
            val bootstrapAsset = AssetPicker.pick(
                assets,
                EnvPaths.ASSET_BOOTSTRAP,
                EnvPaths.ASSET_BOOTSTRAP_PLAIN,
            )
            if (bootstrapAsset == null) {
                throw IllegalStateException("required asset missing: env/bootstrap/bootstrap.tar.gz")
            }
            if (isInstalled(context)) {
                updateManagedLayers(context, assets, prefix, bootstrapAsset, manifest)
                return false
            }
            installFresh(context, assets, prefix, bootstrapAsset, manifest)
            return true
        } catch (t: Throwable) {
            throw t
        }
    }

    private fun validatePrefixLocation(context: Context, prefix: File) {
        try {
            val filesDir = context.filesDir
            val userData = File(filesDir, EnvPaths.USER_DATA_DIR)
            if (!PathSafety.isInside(filesDir, prefix) ||
                PathSafety.isInside(userData, prefix) ||
                PathSafety.isInside(prefix, userData)
            ) {
                throw IllegalStateException("refusing unsafe env prefix location: $prefix")
            }
        } catch (e: IOException) {
            throw IllegalStateException("cannot resolve prefix $prefix", e)
        }
    }

    private fun installFresh(
        context: Context,
        assets: AssetManager,
        prefix: File,
        bootstrapAsset: String,
        manifest: EnvBundleManifest,
    ) {
        var preserved: File?
        val parent = prefix.parentFile
        if (parent == null || (!parent.isDirectory && !parent.mkdirs() && !parent.isDirectory)) {
            throw IllegalStateException("cannot create prefix parent: $parent")
        }
        val staging = File(parent, ".usr-stage-" + UUID.randomUUID())
        try {
            if (!staging.mkdirs() && !staging.isDirectory) {
                throw IOException("cannot create staging prefix: $staging")
            }
            val prefixPath = prefix.absolutePath
            BootstrapExtractor.extract(
                assets,
                bootstrapAsset,
                staging,
                manifest.bootstrapSha256,
                manifest.bootstrapSymlinkCount,
            )
            OverlayCopier.overlay(assets, EnvPaths.ASSET_OVERLAY, staging)
            PkgInstaller.install(assets, staging, prefixPath)
            prepareTree(assets, staging, prefixPath)
            writeMarker(staging, manifest, manifest.bootstrapSha256, manifest.bootstrapSha256, false, prefixPath)
            validateTree(staging, manifest.bootstrapSymlinkCount)
            if (prefix.exists() && !TextFileDetector.isSymlink(prefix)) {
                preserved = File(parent, ".usr-incomplete-" + UUID.randomUUID())
                if (!prefix.renameTo(preserved)) {
                    throw IOException("cannot preserve incomplete prefix $prefix")
                }
            } else if (!TextFileDetector.isSymlink(prefix)) {
                preserved = null
            } else {
                preserved = File(parent, ".usr-incomplete-" + UUID.randomUUID())
                if (!prefix.renameTo(preserved)) {
                    throw IOException("cannot preserve prefix link $prefix")
                }
            }
            if (!staging.renameTo(prefix)) {
                restoreQuarantine(prefix, preserved)
                throw IOException("cannot atomically publish staging prefix $staging")
            }
            if (!bashSmokeTest(context, prefix)) {
                if (!prefix.renameTo(File(parent, ".usr-failed-" + UUID.randomUUID()))) {
                    Log.e(TAG, "new prefix failed smoke test and could not be quarantined: $prefix")
                }
                restoreQuarantine(prefix, preserved)
                throw IOException("installed bash failed its smoke test; prefix preserved for diagnosis")
            }
            Log.i(TAG, "env prefix installed atomically: $prefix bundle=${manifest.bundleId}")
        } catch (e: Exception) {
            deleteTreeQuietly(staging, context.filesDir)
            throw IllegalStateException("env install failed: " + e.message, e)
        }
    }

    private fun updateManagedLayers(
        context: Context,
        assets: AssetManager,
        prefix: File,
        bootstrapAsset: String,
        manifest: EnvBundleManifest,
    ) {
        val state = readState(File(prefix, EnvPaths.MARKER))
        val installedBootstrap = state.getProperty("bootstrap_sha256")
        val installedOverlay = state.getProperty("overlay_sha256")
        val installedPkg = state.getProperty("pkg_sha256")
        val installedPath = state.getProperty("prefix_path")
        val prefixPath = prefix.absolutePath
        val hasVersionedBase = !(installedBootstrap == null || installedBootstrap == "legacy")
        val bootstrapChanged = manifest.bootstrapSha256 != installedBootstrap
        val baseVersion = if (hasVersionedBase) installedBootstrap else "legacy"
        if (hasVersionedBase && bootstrapChanged) {
            Log.w(
                TAG,
                "bootstrap changed; preserving mutable prefix until an explicit migration is available " +
                    "(installed=$installedBootstrap, bundled=${manifest.bootstrapSha256})"
            )
        } else if (!hasVersionedBase && bootstrapChanged) {
            Log.w(TAG, "legacy prefix has no base version; preserving it and refreshing only AGY-managed overlay/pkg layers")
        }
        val overlayChanged = manifest.overlaySha256 != installedOverlay
        val pkgChanged = manifest.pkgSha256 != installedPkg
        if (overlayChanged) {
            OverlayCopier.overlay(assets, EnvPaths.ASSET_OVERLAY, prefix)
        }
        if (pkgChanged) {
            PkgInstaller.install(assets, prefix, prefixPath)
        }
        if (prefixPath != installedPath || overlayChanged || pkgChanged) {
            TermuxSymlinkFixer.fix(prefix, prefixPath)
            TermuxShebangRewriter.rewrite(prefix, prefixPath)
            TermuxPrefixRewriter.rewrite(prefix, prefixPath)
        }
        if (overlayChanged || pkgChanged) {
            FileModeUtil.recursiveSetExec(File(prefix, "bin"))
        }
        FileModeUtil.setExec(File(prefix, "libexec/pkg/pkg"))
        val fixPaths = File(prefix, EnvPaths.FIX_PATHS_REL)
        if (fixPaths.isFile) {
            FileModeUtil.setExec(fixPaths)
        }
        BusInstaller.install(assets, prefix)
        TermuxHooksRemover.remove(prefix)
        validateTree(prefix, 0)
        writeMarker(prefix, manifest, baseVersion, manifest.bootstrapSha256, bootstrapChanged, prefixPath)
        Log.i(
            TAG,
            "env managed layers ready: overlay=" + (if (overlayChanged) "updated" else "current") +
                " pkg=" + (if (pkgChanged) "updated" else "current") +
                " bootstrap=" + (if (bootstrapChanged) "pending" else "current")
        )
    }

    private fun prepareTree(assets: AssetManager, prefix: File, prefixPath: String) {
        TermuxSymlinkFixer.fix(prefix, prefixPath)
        TermuxShebangRewriter.rewrite(prefix, prefixPath)
        TermuxPrefixRewriter.rewrite(prefix, prefixPath)
        FileModeUtil.recursiveSetExec(File(prefix, "bin"))
        FileModeUtil.setExec(File(prefix, "libexec/pkg/pkg"))
        val fixPaths = File(prefix, EnvPaths.FIX_PATHS_REL)
        if (fixPaths.isFile) {
            FileModeUtil.setExec(fixPaths)
        }
        BusInstaller.install(assets, prefix)
        TermuxHooksRemover.remove(prefix)
    }

    private fun validateTree(prefix: File, minSymlinks: Int) {
        val bin = File(prefix, "bin")
        requireFile(File(bin, "bash"), "bash")
        requireFile(File(bin, "sh"), "sh")
        requireFile(File(bin, "pkg"), "pkg wrapper")
        requireFile(File(prefix, "lib/libtermux-exec.so"), "libtermux-exec.so")
        if (minSymlinks > 0) {
            val symlinks = countSymlinks(prefix)
            if (symlinks < minSymlinks) {
                throw IllegalStateException("bootstrap symlink set incomplete: $symlinks < $minSymlinks")
            }
        }
    }

    private fun requireFile(file: File, label: String) {
        if (!file.isFile) {
            throw IllegalStateException("required env file missing: $label at $file")
        }
    }

    private fun countSymlinks(dir: File): Int {
        val children = dir.listFiles() ?: return 0
        var count = 0
        for (child in children) {
            if (TextFileDetector.isSymlink(child)) {
                count++
            } else if (child.isDirectory) {
                count += countSymlinks(child)
            }
        }
        return count
    }

    private fun readState(file: File): Properties {
        val properties = Properties()
        if (!file.isFile) {
            return properties
        }
        try {
            val input = FileInputStream(file)
            try {
                properties.load(input)
                input.close()
            } finally {
            }
        } catch (e: Exception) {
            Log.w(TAG, "cannot read env state $file: " + e.message)
        }
        return properties
    }

    private fun writeMarker(
        prefix: File,
        manifest: EnvBundleManifest,
        bootstrapSha256: String,
        bootstrapTargetSha256: String,
        bootstrapUpdatePending: Boolean,
        prefixPath: String,
    ) {
        val content = "schema=$STATE_SCHEMA\n" +
            "bundle_id=${manifest.bundleId}\n" +
            "bootstrap_sha256=$bootstrapSha256\n" +
            "bootstrap_target_sha256=$bootstrapTargetSha256\n" +
            "bootstrap_update_pending=$bootstrapUpdatePending\n" +
            "overlay_sha256=${manifest.overlaySha256}\n" +
            "pkg_sha256=${manifest.pkgSha256}\n" +
            "prefix_path=$prefixPath\n"
        val marker = File(prefix, EnvPaths.MARKER)
        AtomicFileWriter.writeText(marker, content)
        if (manifest.bundleId != readState(marker).getProperty("bundle_id")) {
            throw IllegalStateException("cannot persist env state marker: $marker")
        }
    }

    private fun bashSmokeTest(context: Context, prefix: File): Boolean {
        return try {
            val processBuilder = ProcessBuilder(
                File(prefix, EnvPaths.BASH_REL).absolutePath,
                "-c",
                "exit 0",
            )
            processBuilder.directory(context.filesDir)
            processBuilder.redirectErrorStream(true)
            val env = processBuilder.environment()
            env["PREFIX"] = prefix.absolutePath
            env["HOME"] = File(prefix.parentFile, "home").absolutePath
            env["TMPDIR"] = File(prefix, "tmp").absolutePath
            env["PATH"] = File(prefix, "bin").absolutePath + ":/system/bin"
            env["LD_LIBRARY_PATH"] = File(prefix, "lib").absolutePath
            val preload = File(prefix, "lib/libtermux-exec.so")
            if (preload.isFile) {
                env["LD_PRELOAD"] = preload.absolutePath
            }
            env["AGY_APPLICATION_ID"] = context.packageName
            val process = processBuilder.start()
            if (process.waitFor(15L, TimeUnit.SECONDS)) {
                process.exitValue() == 0
            } else {
                process.destroyForcibly()
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "bash smoke test failed: " + e.message)
            false
        }
    }

    private fun restoreQuarantine(prefix: File, preserved: File?) {
        if (preserved != null && preserved.exists() && !prefix.exists() && !preserved.renameTo(prefix)) {
            Log.e(TAG, "cannot restore preserved prefix $preserved -> $prefix")
        }
    }

    private fun deleteTreeQuietly(file: File?, filesDir: File) {
        if (file == null || !file.exists() || TextFileDetector.isSymlink(file)) {
            if (file != null && TextFileDetector.isSymlink(file)) {
                file.delete()
            }
            return
        }
        try {
            if (PathSafety.isInside(filesDir, file) && file != filesDir) {
                val children = file.listFiles()
                if (children != null) {
                    for (child in children) {
                        deleteTreeQuietly(child, filesDir)
                    }
                }
                if (!file.delete()) {
                    Log.w(TAG, "cannot remove failed staging path: $file")
                }
            } else {
                Log.e(TAG, "refusing to clean unsafe staging path: $file")
            }
        } catch (e: IOException) {
            Log.w(TAG, "cannot validate failed staging path: " + e.message)
        }
    }
}
