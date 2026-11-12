package com.agy.log

import org.json.JSONException
import org.json.JSONObject

/**
 * One executed shell/gateway command with its outcome, as surfaced by the
 * command-history gateway endpoint.
 *
 * Ported 1:1 from decompiled `com/agy/log/CommandRecord.java`.
 */
class CommandRecord(
    id: String?,
    timestamp: Long,
    cmd: String?,
    exitCode: Int,
    durationMs: Long,
    stdout: String?,
    stderr: String?,
    status: String?,
    source: String?,
) {
    val id: String? = id
    val timestamp: Long = timestamp
    val cmd: String = cmd ?: ""
    val exitCode: Int = exitCode
    val durationMs: Long = durationMs
    val stdout: String = stdout ?: ""
    val stderr: String = stderr ?: ""
    val status: String = status ?: "UNKNOWN"
    val source: String = source ?: "system"

    fun toJson(): JSONObject {
        val json = JSONObject()
        try {
            json.put("id", id)
            json.put("time", timestamp)
            json.put("cmd", cmd)
            json.put("exit_code", exitCode)
            json.put("duration_ms", durationMs)
            json.put("stdout", LogRedactor.redact(stdout))
            json.put("stderr", LogRedactor.redact(stderr))
            json.put("status", status)
            json.put("source", source)
        } catch (_: JSONException) {
        }
        return json
    }
}
