package com.agy.core

import android.util.Log
import com.agy.util.SensitiveDataRedactor
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.nio.charset.Charset

/**
 * Writes redacted core stdout to `logs/core.log`.
 *
 * Ported 1:1 from decompiled `com/agy/core/CoreLogWriter.java`.
 */
object CoreLogWriter {
    private const val TAG = "CoreLogWriter"

    @JvmStatic
    fun open(filesDir: File): Writer? {
        return try {
            val logs = File(filesDir, "logs")
            logs.mkdirs()
            OutputStreamWriter(
                FileOutputStream(File(logs, "core.log"), false),
                Charset.forName("UTF-8"),
            )
        } catch (e: Exception) {
            Log.w(TAG, "cannot open core log", e)
            null
        }
    }

    @JvmStatic
    fun writeLine(writer: Writer?, line: String?) {
        if (writer == null || line == null) return
        try {
            writer.write(SensitiveDataRedactor.redactLine(line))
            writer.write(10)
            writer.flush()
        } catch (_: Exception) {
        }
    }

    @JvmStatic
    fun closeQuietly(writer: Writer?) {
        if (writer != null) {
            try {
                writer.close()
            } catch (_: Exception) {
            }
        }
    }
}
