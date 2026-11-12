package com.agy.gateway

import com.agy.gateway.handlers.BrowserHandler
import com.agy.gateway.handlers.ClipboardHandler
import com.agy.gateway.handlers.DeviceHandler
import com.agy.gateway.handlers.ExecHandler
import com.agy.gateway.handlers.NotificationHandler
import com.agy.gateway.handlers.PowerHandler
import com.agy.log.CommandHistory
import com.agy.log.CommandRecord
import com.agy.log.CrashRecorder
import com.agy.log.LogRingBuffer
import android.content.Context
import org.json.JSONObject

/**
 * Routes gateway commands to their handlers. Command names are the engine's
 * sidecar API and must not change.
 *
 * Ported 1:1 from decompiled `com/agy/gateway/GatewayDispatcher.java`. The
 * original used JNA `Platform.*` constants as switch-case labels; the plain
 * integers are inlined here.
 */
class GatewayDispatcher(context: Context, logBuffer: LogRingBuffer?) {
    private val notificationHandler = NotificationHandler(context)
    private val browserHandler = BrowserHandler(context)
    private val powerHandler = PowerHandler(context)
    private val clipboardHandler = ClipboardHandler(context)
    private val deviceHandler = DeviceHandler(context)
    private val logBuffer: LogRingBuffer? = logBuffer
    val commandHistory = CommandHistory()
    private val execHandler = ExecHandler(context, commandHistory, logBuffer)

    fun dispatch(command: String?, payload: JSONObject?): JSONObject {
        var request = payload
        if (request == null) {
            request = JSONObject()
        }
        return when (command) {
            "notify" -> notificationHandler.handle(request)
            "open_url" -> browserHandler.handle(request)
            "wakelock" -> powerHandler.handle(request)
            "clipboard", "clipboard_set" -> clipboardHandler.handle(request)
            "system_info" -> deviceHandler.handleSystemInfo()
            "vibrate" -> deviceHandler.handleVibrate(request)
            "toast" -> deviceHandler.handleToast(request)
            "logs" -> {
                val response = JSONObject()
                try {
                    val tail = request.optInt("tail", 50)
                    response.put("ok", true)
                    response.put("logs", LogRingBuffer.toJsonArray(logBuffer?.getRecent(tail) ?: emptyList()))
                } catch (_: Exception) {
                }
                response
            }
            "exec" -> execHandler.handle(request)
            "commands" -> {
                val response = JSONObject()
                try {
                    val limit = request.optInt("limit", 20)
                    response.put("ok", true)
                    response.put("commands", CommandHistory.toJsonArray(commandHistory.getRecent(limit)))
                } catch (_: Exception) {
                }
                response
            }
            "command_get" -> {
                val response = JSONObject()
                try {
                    val record: CommandRecord? = commandHistory.getById(request.optString("id", ""))
                    response.put("ok", record != null)
                    response.put("command", record?.toJson() ?: JSONObject.NULL)
                } catch (_: Exception) {
                }
                response
            }
            "crash" -> {
                val response = JSONObject()
                try {
                    val latest = CrashRecorder.getLatestCrash()
                    response.put("ok", true)
                    response.put("crash", latest ?: JSONObject.NULL)
                } catch (_: Exception) {
                }
                response
            }
            else -> {
                val response = JSONObject()
                try {
                    response.put("ok", false)
                    response.put("error", "unknown command: $command")
                } catch (_: Exception) {
                }
                response
            }
        }
    }

    fun releaseResources() {
        powerHandler.releaseAll()
    }
}
