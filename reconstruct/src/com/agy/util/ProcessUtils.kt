package com.agy.util

import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Process lifecycle helpers shared by the core supervisor and shell executors.
 *
 * Ported 1:1 from decompiled `com/agy/util/ProcessUtils.java`. Swallowed-error
 * fallbacks are preserved deliberately: `exitValue()` probing is the original
 * liveness mechanism and `drainAndWait` must never propagate.
 */
object ProcessUtils {
    @JvmStatic
    fun sleep(millis: Long) {
        try {
            Thread.sleep(millis)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    @JvmStatic
    fun isAlive(process: Process): Boolean = try {
        process.exitValue()
        false
    } catch (_: IllegalThreadStateException) {
        true
    }

    @JvmStatic
    fun exitCode(process: Process): Int = try {
        process.exitValue()
    } catch (_: IllegalThreadStateException) {
        -1
    }

    @JvmStatic
    fun drainAndWait(process: Process, timeout: Long, unit: TimeUnit): Boolean {
        try {
            val input: InputStream = process.inputStream
            try {
                StreamDrain.drain(input)
                input.close()
            } catch (_: Exception) {
            }
        } catch (_: Exception) {
        }
        return try {
            process.waitFor(timeout, unit)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }
}
