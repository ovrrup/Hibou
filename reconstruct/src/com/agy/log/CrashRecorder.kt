package com.agy.log

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import org.json.JSONArray
import org.json.JSONObject

/**
 * Records core crash diagnostics (exit code, uptime, memory/storage, log tail)
 * into `logs/crashes/crash_<ts>.json` and exposes the latest one to the
 * gateway.
 *
 * Ported 1:1 from decompiled `com/agy/log/CrashRecorder.java`. A `null` error
 * string is omitted from the JSON, matching `JSONObject.put(name, null)`
 * removal semantics of the original.
 */
object CrashRecorder {
    private const val TAG = "CrashRecorder"

    @Volatile
    private var latestCrash: JSONObject? = null

    @JvmStatic
    @Synchronized
    fun recordCrash(
        context: Context,
        exitCode: Int,
        uptimeMs: Long,
        error: String?,
        logBuffer: LogRingBuffer?,
    ): JSONObject {
        val report = JSONObject()
        try {
            val now = System.currentTimeMillis()
            report.put("timestamp", now)
            report.put("exit_code", exitCode)
            report.put("uptime_ms", uptimeMs)
            val redactedError = LogRedactor.redact(error)
            if (redactedError != null) {
                report.put("error", redactedError)
            }
            val runtime = Runtime.getRuntime()
            val memory = JSONObject()
            memory.put("free_mb", runtime.freeMemory() / 1048576)
            memory.put("total_mb", runtime.totalMemory() / 1048576)
            memory.put("max_mb", runtime.maxMemory() / 1048576)
            report.put("memory", memory)
            val filesDir = context.filesDir
            val storage = JSONObject()
            storage.put("usable_mb", filesDir.usableSpace / 1048576)
            storage.put("total_mb", filesDir.totalSpace / 1048576)
            report.put("storage", storage)
            if (logBuffer != null) {
                val tail = JSONArray()
                for (entry in logBuffer.getRecent(50)) {
                    tail.put(LogRedactor.redact(entry.text))
                }
                report.put("log_tail", tail)
            }
            latestCrash = report
            persistReport(context, report, now)
        } catch (e: Exception) {
            Log.w(TAG, "failed to record crash: " + e.message)
        }
        return report
    }

    @JvmStatic
    fun getLatestCrash(): JSONObject? = latestCrash

    private fun persistReport(context: Context, report: JSONObject, timestamp: Long) {
        try {
            val dir = File(context.filesDir, "logs/crashes")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val file = File(dir, "crash_$timestamp.json")
            val out = FileOutputStream(file)
            try {
                out.write(report.toString(2).toByteArray(StandardCharsets.UTF_8))
                out.close()
                Log.i(TAG, "Crash report persisted: " + file.absolutePath)
            } finally {
            }
        } catch (e: Exception) {
            Log.w(TAG, "failed to persist crash report: " + e.message)
        }
    }
}
