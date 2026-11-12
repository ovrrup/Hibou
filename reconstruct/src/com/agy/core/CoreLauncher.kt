package com.agy.core

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.agy.runtime.RuntimeManager
import com.agy.util.ProcessUtils
import java.io.File
import java.io.FileInputStream
import java.io.IOException

/**
 * Kills orphaned engine processes and starts the core binary through the
 * glibc loader.
 *
 * Ported 1:1 from decompiled `com/agy/core/CoreLauncher.java`. The 3500ms
 * delayed `installEnvInBackground()` and the orphan-kill markers
 * (`language_server`/`liblanguage_server`) are engine contracts.
 */
object CoreLauncher {
    private const val TAG = "CoreLauncher"
    private const val READ_BUFFER = 8192 // was kotlin.io.ConstantsKt.MINIMUM_BLOCK_SIZE

    @JvmStatic
    fun killOrphanCoreProcesses() {
        val myPid = android.os.Process.myPid()
        val procEntries = File("/proc").listFiles() ?: return
        var killed = false
        for (entry in procEntries) {
            val name = entry.name
            if (!name.matches(Regex("\\d+"))) continue
            try {
                val pid = name.toInt()
                if (pid != myPid) {
                    val cmdline = File(entry, "cmdline")
                    if (cmdline.canRead()) {
                        val buffer = ByteArray(READ_BUFFER)
                        val input = FileInputStream(cmdline)
                        try {
                            val read = input.read(buffer)
                            input.close()
                            if (read > 0) {
                                val command = String(buffer, 0, read)
                                if (command.contains("language_server") || command.contains("liblanguage_server")) {
                                    Log.w(TAG, "Terminating orphan core process (PID $pid)")
                                    android.os.Process.sendSignal(pid, 9)
                                    killed = true
                                }
                            }
                        } catch (t: Throwable) {
                            try {
                                input.close()
                            } catch (suppressed: Throwable) {
                                t.addSuppressed(suppressed)
                            }
                            throw t
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        if (killed) {
            ProcessUtils.sleep(200L)
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun launch(runtimeManager: RuntimeManager, filesDir: File): java.lang.Process {
        com.kestrel.diag.FlightLog.w("LAUNCH", "killOrphan begin")
        killOrphanCoreProcesses()
        com.kestrel.diag.FlightLog.w("LAUNCH", "installCore begin")
        runtimeManager.installCore()
        com.kestrel.diag.FlightLog.w("LAUNCH", "installCore done")
        Handler(Looper.getMainLooper()).postDelayed({
            runtimeManager.installEnvInBackground()
        }, 3500L)
        val command = runtimeManager.coreCommand()
        com.kestrel.diag.FlightLog.w("LAUNCH", "spawn: " + command.joinToString(" "))
        val processBuilder = ProcessBuilder(*command)
        processBuilder.directory(filesDir)
        processBuilder.redirectErrorStream(true)
        runtimeManager.applyEnvironment(processBuilder)
        return processBuilder.start()
    }
}
