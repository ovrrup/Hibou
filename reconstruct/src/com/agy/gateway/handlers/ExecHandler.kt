package com.agy.gateway.handlers

import android.content.Context
import android.util.Log
import com.agy.core.CoreRestartPolicy
import com.agy.log.CommandHistory
import com.agy.log.CommandRecord
import com.agy.log.LogRingBuffer
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.json.JSONObject

/**
 * Gateway command `exec`: runs a shell command (`/system/bin/sh -c`, or the
 * `rish` shell when requested), draining stdout/stderr on separate threads,
 * with a timeout (default [CoreRestartPolicy.RESTART_MAX_MS] = 30000ms).
 *
 * Ported 1:1 from decompiled `com/agy/gateway/handlers/ExecHandler.java`.
 * Response is [CommandRecord.toJson] plus `ok` = (`status == "SUCCESS"`);
 * `history.record` is skipped when no history is wired.
 */
class ExecHandler(
    context: Context,
    private val history: CommandHistory?,
    private val logBuffer: LogRingBuffer?,
) {
    private val context: Context = context.applicationContext

    fun handle(request: JSONObject): JSONObject {
        val cmd = request.optString("cmd", "").trim()
        val source = request.optString("source", "agent")
        val timeoutMs = request.optLong("timeout_ms", CoreRestartPolicy.RESTART_MAX_MS)
        val rish = request.optBoolean("rish", false)
        val cwd = request.optString("cwd", context.filesDir.absolutePath)
        val id = "cmd_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 4)
        val startedAt = System.currentTimeMillis()

        if (cmd.isEmpty()) {
            val response = JSONObject()
            try {
                response.put("ok", false)
                response.put("error", "empty command")
            } catch (_: Exception) {
            }
            return response
        }

        val stdout = StringBuilder()
        val stderr = StringBuilder()
        var exitCode = -1
        var status: String
        val processBuilder: ProcessBuilder
        val home = context.filesDir.absolutePath
        try {
            processBuilder =
                if (rish) {
                    ProcessBuilder(resolveRishPath(), "-c", cmd)
                } else {
                    ProcessBuilder("/system/bin/sh", "-c", cmd)
                }
            val cwdFile = File(cwd)
            if (cwdFile.isDirectory) {
                processBuilder.directory(cwdFile)
            }
        } catch (e: Exception) {
            status = "ERROR"
            stderr.append("\nExecution exception: ").append(e.message)
            Log.w(TAG, "exec error: " + e.message)
            return finish(id, startedAt, cmd, exitCode, stdout, stderr, status, source)
        }
        try {
            processBuilder.environment()["HOME"] = home
            processBuilder.environment()["PATH"] =
                "$home/usr/bin:$home/runtime/bin:/system/bin:${System.getenv("PATH")}"
            processBuilder.environment()["RISH_APPLICATION_ID"] = context.packageName
            processBuilder.environment().remove("LD_LIBRARY_PATH")
            val process = processBuilder.start()
            val stdoutThread = Thread({ drainStream(process.inputStream, stdout) }, "StdoutDrain")
            val stderrThread = Thread({ drainStream(process.errorStream, stderr) }, "StderrDrain")
            stdoutThread.start()
            stderrThread.start()
            if (process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                exitCode = process.exitValue()
                stdoutThread.join(500L)
                stderrThread.join(500L)
                status = if (exitCode == 0) "SUCCESS" else "FAILED"
            } else {
                process.destroy()
                status = "TIMEOUT"
                stderr.append("\n[Process timed out after ").append(timeoutMs).append("ms]")
            }
        } catch (e: Exception) {
            stderr.append("\nExecution exception: ").append(e.message)
            Log.w(TAG, "exec error: " + e.message)
            status = "ERROR"
            return finish(id, startedAt, cmd, exitCode, stdout, stderr, status, source)
        }
        return finish(id, startedAt, cmd, exitCode, stdout, stderr, status, source)
    }

    private fun finish(
        id: String,
        startedAt: Long,
        cmd: String,
        exitCode: Int,
        stdout: StringBuilder,
        stderr: StringBuilder,
        status: String,
        source: String,
    ): JSONObject {
        val record = CommandRecord(
            id,
            startedAt,
            cmd,
            exitCode,
            System.currentTimeMillis() - startedAt,
            stdout.toString().trim(),
            stderr.toString().trim(),
            status,
            source,
        )
        history?.record(record)
        val json = record.toJson()
        json.put("ok", "SUCCESS" == status)
        return json
    }

    private fun drainStream(input: InputStream, target: StringBuilder) {
        try {
            val reader = BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8))
            while (true) {
                val line = reader.readLine() ?: break
                if (target.length > 0) {
                    target.append("\n")
                }
                target.append(line)
            }
            reader.close()
        } catch (_: Exception) {
        }
    }

    private fun resolveRishPath(): String {
        val filesDir = context.filesDir
        val candidates = arrayOf(
            File(filesDir, "usr/bin/rish"),
            File(filesDir, "runtime/bin/rish"),
            File(filesDir, "bin/rish"),
        )
        for (candidate in candidates) {
            if (candidate.exists() && candidate.canExecute()) {
                return candidate.absolutePath
            }
        }
        for (candidate in candidates) {
            if (candidate.exists()) {
                return candidate.absolutePath
            }
        }
        return File(filesDir, "usr/bin/rish").absolutePath
    }

    private companion object {
        const val TAG = "ExecHandler"
    }
}
