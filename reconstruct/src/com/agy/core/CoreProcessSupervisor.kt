package com.agy.core

import android.content.Context
import android.text.TextUtils
import android.util.Log
import com.agy.log.CrashRecorder
import com.agy.log.LogRingBuffer
import com.agy.runtime.RuntimeManager
import com.agy.util.CoreUrl
import com.agy.util.ErrorText
import com.agy.util.ProcessUtils
import com.agy.util.SensitiveDataRedactor
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.InputStreamReader
import java.io.Writer

/**
 * Runs the core process in a supervised loop: launch → stream stdout (log +
 * URL watch) → wait for ready (pinned HTTPS) → restart on crash with back-off
 * (3 attempts), giving up with a persisted crash report.
 *
 * Ported 1:1 from decompiled `com/agy/core/CoreProcessSupervisor.java`,
 * including the quirks: readiness failure gives up immediately (no restart),
 * a run that stayed ready ≥60s resets the attempt counter, and the no-op
 * `closeQuietly(null)` after the restart sleep is preserved.
 */
class CoreProcessSupervisor(
    context: Context,
    private val runtime: RuntimeManager,
    private val openUrlWatcher: OpenUrlWatcher?,
    private val logBuffer: LogRingBuffer?,
    private val terminalListener: Runnable?,
) {
    private val appContext: Context = context.applicationContext
    private var worker: Thread? = null

    @Volatile
    private var process: java.lang.Process? = null

    @Volatile
    private var stopping = false

    fun start() {
        stopping = false
        val thread = Thread({ runCore() }, "CoreServerThread")
        worker = thread
        thread.start()
    }

    fun stop() {
        stopping = true
        destroy(process)
        worker?.interrupt()
    }

    private fun runCore() {
        try {
            runCoreLoop()
            if (!stopping) {
                try {
                    terminalListener?.run()
                } catch (e: Exception) {
                    Log.w(TAG, "terminal callback failed", e)
                }
            }
        } catch (t: Throwable) {
            if (!stopping) {
                try {
                    terminalListener?.run()
                } catch (e: Exception) {
                    Log.w(TAG, "terminal callback failed", e)
                }
            }
            throw t
        }
    }

    private fun runCoreLoop() {
        var restarting = false
        var attempts = 0
        while (!stopping) {
            var writer: Writer? = CoreLogWriter.open(appContext.filesDir)
            try {
                CoreRuntimeState.lastError = null
                CoreRuntimeState.restarting = restarting
                val launch = CoreLauncher.launch(runtime, appContext.filesDir)
                process = launch
                val streamWriter = writer
                val streamThread = Thread(
                    { streamLog(launch, streamWriter) },
                    "CoreStreamThread",
                )
                streamThread.isDaemon = true
                streamThread.start()
                if (!waitForReady(launch, writer)) {
                    if (stopping) {
                        CoreLogWriter.closeQuietly(writer)
                        return
                    }
                    if (CoreRuntimeState.lastError == null) {
                        CoreRuntimeState.lastError =
                            "core did not become ready within 60000ms; readiness error: " + CoreReadyProbe.lastProbeError
                        CoreLogWriter.writeLine(writer, CoreRuntimeState.lastError)
                    }
                    destroy(launch)
                    CoreLogWriter.closeQuietly(writer)
                    return
                }
                val startTime = System.currentTimeMillis()
                try {
                    launch.waitFor()
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
                if (stopping) {
                    CoreLogWriter.closeQuietly(writer)
                    return
                }
                val exitCode = ProcessUtils.exitCode(launch)
                if (System.currentTimeMillis() - startTime >= CoreRestartPolicy.SUSTAINED_READY_MS) {
                    attempts = 0
                }
                CoreRuntimeState.serverUrl = null
                attempts += 1
                val tail = getLastLinesFromLog(appContext.filesDir, 15)
                if (attempts > CoreRestartPolicy.MAX_RESTARTS) {
                    CoreRuntimeState.restarting = false
                    CoreRuntimeState.lastError =
                        "core crashed 3 times; giving up (last exit code $exitCode)" +
                        (if (tail.isEmpty()) "" else "\n--- core.log tail ---\n$tail")
                    CoreLogWriter.writeLine(writer, CoreRuntimeState.lastError)
                    CrashRecorder.recordCrash(
                        appContext,
                        exitCode,
                        System.currentTimeMillis() - startTime,
                        CoreRuntimeState.lastError,
                        logBuffer,
                    )
                    CoreLogWriter.closeQuietly(writer)
                    return
                }
                val delay = CoreRestartPolicy.delayFor(attempts)
                CoreRuntimeState.restarting = true
                CoreRuntimeState.lastError =
                    "core exited unexpectedly (code $exitCode); restarting in ${delay / 1000}s (attempt $attempts/3)" +
                    (if (tail.isEmpty()) "" else "\n--- core.log tail ---\n$tail")
                Log.w(TAG, CoreRuntimeState.lastError ?: "")
                CoreLogWriter.writeLine(writer, CoreRuntimeState.lastError)
                CrashRecorder.recordCrash(
                    appContext,
                    exitCode,
                    System.currentTimeMillis() - startTime,
                    CoreRuntimeState.lastError,
                    logBuffer,
                )
                CoreLogWriter.closeQuietly(writer)
                writer = null
                sleepInterruptible(delay)
                CoreLogWriter.closeQuietly(null) // preserved no-op from the original
                restarting = false
            } catch (e: Exception) {
                if (stopping) {
                    CoreLogWriter.closeQuietly(writer)
                    return
                }
                Log.e(TAG, "core start failed", e)
                CoreRuntimeState.lastError = ErrorText.describe(e)
                CoreLogWriter.writeLine(writer, "core start failed: " + CoreRuntimeState.lastError)
                CoreLogWriter.closeQuietly(writer)
                return
            } catch (t: Throwable) {
                CoreLogWriter.closeQuietly(writer)
                throw t
            }
        }
    }

    private fun waitForReady(process: Process, writer: Writer?): Boolean {
        val start = System.currentTimeMillis()
        val csrfToken = runtime.getCsrfToken()
        val port = CoreRuntimeState.httpsPort
        while (System.currentTimeMillis() - start < CoreReadyProbe.READY_TIMEOUT_MS && !stopping) {
            if (!ProcessUtils.isAlive(process)) {
                val exitCode = ProcessUtils.exitCode(process)
                ProcessUtils.sleep(200L)
                val tail = getLastLinesFromLog(appContext.filesDir, 10)
                CoreRuntimeState.lastError =
                    "core process exited before it became ready (exit code $exitCode)" +
                    (if (tail.isEmpty()) "" else "\n--- core.log tail ---\n$tail")
                CoreLogWriter.writeLine(writer, CoreRuntimeState.lastError)
                return false
            }
            if (CoreReadyProbe.probeOnce(appContext, port)) {
                CoreRuntimeState.serverUrl = CoreUrl.startUrl(port, csrfToken)
                CoreRuntimeState.lastError = null
                CoreRuntimeState.restarting = false
                CoreRuntimeState.readyGeneration++
                val ready = "core ready on 127.0.0.1:$port"
                Log.i(TAG, ready)
                CoreLogWriter.writeLine(writer, ready)
                return true
            }
        }
        return false
    }

    private fun streamLog(process: Process, writer: Writer?) {
        try {
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            while (true) {
                val line = reader.readLine() ?: break
                val redacted = SensitiveDataRedactor.redactLine(line)
                Log.d(TAG, redacted)
                logBuffer?.appendLine(redacted)
                CoreLogWriter.writeLine(writer, redacted)
                openUrlWatcher?.handleStreamLine(line)
            }
            reader.close()
        } catch (e: Exception) {
            Log.w(TAG, "log stream ended", e)
        }
    }

    private fun sleepInterruptible(totalMillis: Long) {
        val deadline = System.currentTimeMillis() + totalMillis
        while (!stopping) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) return
            ProcessUtils.sleep(minOf(remaining, 250L))
        }
    }

    private fun destroy(process: Process?) {
        if (process != null) {
            try {
                process.destroy()
            } catch (_: Exception) {
            }
            try {
                process.destroyForcibly()
            } catch (_: Exception) {
            }
        }
        CoreLauncher.killOrphanCoreProcesses()
    }

    private fun getLastLinesFromLog(filesDir: File, maxLines: Int): String {
        val logFile = File(filesDir, "logs/core.log")
        if (!logFile.isFile) return ""
        val lines = ArrayList<String>()
        try {
            val reader = BufferedReader(FileReader(logFile))
            try {
                while (true) {
                    val line = reader.readLine() ?: break
                    lines.add(SensitiveDataRedactor.redactLine(line))
                    if (lines.size > maxLines) {
                        lines.removeAt(0)
                    }
                }
            } finally {
            }
            reader.close()
        } catch (_: Exception) {
        }
        return TextUtils.join("\n", lines)
    }

    private companion object {
        const val TAG = "CoreProcessSupervisor"
    }
}
