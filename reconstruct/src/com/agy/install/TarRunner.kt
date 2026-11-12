package com.agy.install

import android.util.Log
import com.agy.util.ProcessUtils
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Extracts archives with `/system/bin/toybox tar` (falling back to
 * `/system/bin/tar`), 120s timeout.
 *
 * Ported 1:1 from decompiled `com/agy/install/TarRunner.java`.
 */
object TarRunner {
    private const val TAG = "TarRunner"

    @JvmStatic
    fun runToyboxTar(archive: File, targetDir: File, gzip: Boolean): Boolean {
        if (runTar(EnvPaths.TOYBOX, true, archive, targetDir, gzip)) {
            return true
        }
        return runTar(EnvPaths.TAR, false, archive, targetDir, gzip)
    }

    private fun runTar(binary: String, isToybox: Boolean, archive: File, targetDir: File, gzip: Boolean): Boolean {
        if (!File(binary).isFile) {
            return false
        }
        val argv = ArrayList<String>()
        argv.add(binary)
        if (isToybox) {
            argv.add("tar")
        }
        argv.add(if (gzip) "-xzf" else "-xf")
        argv.add(archive.absolutePath)
        argv.add("-C")
        argv.add(targetDir.absolutePath)
        return try {
            val processBuilder = ProcessBuilder(argv)
            processBuilder.redirectErrorStream(true)
            val process = processBuilder.start()
            if (!ProcessUtils.drainAndWait(process, 120L, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                Log.w(TAG, "tar timed out: $argv")
                return false
            }
            val exitCode = process.exitValue()
            if (exitCode != 0) {
                Log.w(TAG, "tar exit $exitCode: $argv")
            }
            exitCode == 0
        } catch (e: Exception) {
            Log.w(TAG, "tar exec failed $argv: $e")
            false
        }
    }
}
