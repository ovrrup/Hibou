package com.agy.install

import android.content.res.AssetManager
import android.util.Log
import com.agy.util.AssetUnpacker
import com.agy.util.IoUtil
import com.agy.util.Sha256
import java.io.File
import java.io.FileInputStream
import java.io.IOException

/**
 * Stages the bundled bootstrap archive to a temp file, verifies its hash,
 * extracts it with the system tar (Java fallback when tar is unavailable or
 * incomplete), then verifies the shell files and symlink count.
 *
 * Ported 1:1 from decompiled `com/agy/install/BootstrapExtractor.java`.
 */
object BootstrapExtractor {
    private const val TAG = "BootstrapExtractor"

    @JvmStatic
    fun extract(assetManager: AssetManager, assetPath: String, targetDir: File) {
        extract(assetManager, assetPath, targetDir, null, 0)
    }

    @JvmStatic
    fun extract(
        assetManager: AssetManager,
        assetPath: String,
        targetDir: File,
        expectedSha256: String?,
        minSymlinks: Int,
    ) {
        var staged: File? = null
        try {
            staged = File.createTempFile("bootstrap", ".tar", targetDir)
            AssetUnpacker.copyFile(assetManager, assetPath, staged)
            if (expectedSha256 != null) {
                val actual = Sha256.of(staged)
                if (expectedSha256 != actual) {
                    throw IOException("bootstrap sha256 mismatch: got $actual want $expectedSha256")
                }
            }
            val gzip = isGzip(staged)
            Log.i(TAG, "bootstrap $assetPath format=" + (if (gzip) "gzip" else "plain-tar"))
            try {
                try {
                    if (TarRunner.runToyboxTar(staged, targetDir, gzip) &&
                        File(targetDir, "bin").isDirectory &&
                        countSymlinks(targetDir) >= minSymlinks
                    ) {
                        verifyRequiredFiles(targetDir, minSymlinks)
                        Log.i(TAG, "bootstrap extracted with system tar")
                        return
                    }
                    Log.w(TAG, "system tar unavailable/incomplete; using path-safe Java fallback")
                    val input = FileInputStream(staged)
                    try {
                        val symlinks = if (gzip) TarExtractor.untarGz(input, targetDir) else TarExtractor.untar(input, targetDir)
                        input.close()
                        if (symlinks < minSymlinks) {
                            throw IOException(
                                "bootstrap symlink count mismatch: extracted $symlinks expected at least $minSymlinks"
                            )
                        }
                        verifyRequiredFiles(targetDir, minSymlinks)
                        Log.i(TAG, "bootstrap extracted with Java fallback; symlinks=$symlinks")
                    } catch (t: Throwable) {
                        try {
                            input.close()
                        } catch (suppressed: Throwable) {
                            t.addSuppressed(suppressed)
                        }
                        throw t
                    }
                } catch (e: IOException) {
                    throw IllegalStateException("bootstrap extraction failed: " + e.message, e)
                }
            } finally {
                IoUtil.deleteQuietly(staged)
            }
        } catch (e: Exception) {
            IoUtil.deleteQuietly(staged)
            throw IllegalStateException("cannot stage/verify $assetPath: " + e.message, e)
        }
    }

    @Throws(IOException::class)
    private fun verifyRequiredFiles(prefix: File, minSymlinks: Int) {
        val bin = File(prefix, "bin")
        val bash = File(bin, "bash")
        val sh = File(bin, "sh")
        if (!bin.isDirectory || !bash.isFile || !sh.isFile) {
            throw IOException("bootstrap is missing required shell files under $bin")
        }
        if (minSymlinks > 0 && !TextFileDetector.isSymlink(sh)) {
            throw IOException("bootstrap bin/sh symlink was not preserved")
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

    private fun isGzip(file: File?): Boolean {
        if (file == null || !file.isFile || file.length() < 2) {
            return false
        }
        return try {
            val input = FileInputStream(file)
            try {
                val magic = input.read() == 31 && input.read() == 139
                input.close()
                magic
            } finally {
            }
        } catch (e: IOException) {
            Log.w(TAG, "cannot read magic of $file: $e")
            false
        }
    }
}
