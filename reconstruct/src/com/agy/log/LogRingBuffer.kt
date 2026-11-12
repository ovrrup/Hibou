package com.agy.log

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.LinkedList

/**
 * In-memory ring buffer of the last N core log lines, surfaced through the
 * system gateway (`/logs/tail`) and crash reports.
 *
 * Ported 1:1 from decompiled `com/agy/log/LogRingBuffer.java`; the level
 * heuristic (ERROR > WARN > DEBUG > INFO from line markers) matches the
 * original precedence.
 */
class LogRingBuffer {
    class LogEntry(timestamp: Long, channel: String?, level: String?, text: String?) {
        val timestamp: Long = timestamp
        val channel: String = channel ?: "core"
        val level: String = level ?: "INFO"
        val text: String = text ?: ""

        fun toJson(): JSONObject {
            val json = JSONObject()
            try {
                json.put("time", timestamp)
                json.put("channel", channel)
                json.put("level", level)
                json.put("msg", text)
            } catch (_: JSONException) {
            }
            return json
        }
    }

    private val buffer = LinkedList<LogEntry>()
    private val capacity: Int

    constructor() : this(DEFAULT_CAPACITY)

    constructor(capacity: Int) {
        this.capacity = maxOf(50, capacity)
    }

    @Synchronized
    fun append(channel: String?, level: String?, text: String?) {
        if (buffer.size >= capacity) {
            buffer.removeFirst()
        }
        buffer.addLast(LogEntry(System.currentTimeMillis(), channel, level, text))
    }

    @Synchronized
    fun appendLine(line: String?) {
        if (line != null && line.trim().isNotEmpty()) {
            val level = when {
                line.contains("[ERROR]") || line.contains("ERROR:") || line.startsWith("E ") -> "ERROR"
                line.contains("[WARN]") || line.contains("WARNING:") || line.startsWith("W ") -> "WARN"
                line.contains("[DEBUG]") || line.startsWith("D ") -> "DEBUG"
                else -> "INFO"
            }
            append("core", level, line)
        }
    }

    @Synchronized
    fun getRecent(count: Int): List<LogEntry> {
        val recent = minOf(maxOf(1, count), buffer.size)
        return buffer.toList().takeLast(recent)
    }

    @Synchronized
    fun getSince(timestamp: Long): List<LogEntry> = buffer.filter { it.timestamp > timestamp }

    companion object {
        const val DEFAULT_CAPACITY = 500

        @JvmStatic
        fun toJsonArray(entries: List<LogEntry>?): JSONArray {
            val array = JSONArray()
            if (entries != null) {
                for (entry in entries) {
                    array.put(entry.toJson())
                }
            }
            return array
        }
    }
}
