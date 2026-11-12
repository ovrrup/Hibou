package com.agy.gateway.handlers

import android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.agy.BuildConfig
import org.json.JSONObject

/**
 * Gateway command `notify`: posts/cancels progress and alert notifications on
 * the `agy_tasks`/`agy_alerts` channels (ids are engine contracts).
 *
 * Ported from decompiled `com/agy/gateway/handlers/NotificationHandler.java`.
 * Port delta: channel display names rebranded to Kestrel (user-visible); ids
 * and icon lookup behavior unchanged.
 */
class NotificationHandler(context: Context) {
    private val context: Context = context.applicationContext
    private val manager: NotificationManager? =
        context.applicationContext.getSystemService("notification") as? NotificationManager
    init {
        initChannels()
    }

    private fun initChannels() {
        if (Build.VERSION.SDK_INT >= 26 && manager != null) {
            val tasks = NotificationChannel(CHANNEL_TASKS, "Kestrel Tasks", 3)
            tasks.description = "Notifications for ongoing AI agent and environment tasks"
            manager.createNotificationChannel(tasks)
            val alerts = NotificationChannel(CHANNEL_ALERTS, "Kestrel Alerts", 4)
            alerts.description = "High-priority alerts and task completions"
            manager.createNotificationChannel(alerts)
        }
    }

    fun handle(request: JSONObject): JSONObject {
        val response = JSONObject()
        return try {
            val action = request.optString("action", "post")
            val tag = request.optString("tag", "agy_task")
            val id = request.optInt("id", (System.currentTimeMillis() % 100000).toInt())
            if ("cancel".equals(action, ignoreCase = true)) {
                manager?.cancel(tag, id)
                response.put("ok", true)
                response.put("cancelled", true)
                return response
            }
            val title = request.optString("title", BuildConfig.APP_NAME)
            val message = request.optString("message", "")
            val progress = request.optInt("progress", -1)
            val ongoing = request.optBoolean("ongoing", false)
            val channel = request.optString("channel", CHANNEL_TASKS)
            if (manager != null) {
                var icon = context.resources.getIdentifier("ic_notification", "drawable", context.packageName)
                if (icon == 0) {
                    icon = R.drawable.ic_dialog_info
                }
                val builder = if (Build.VERSION.SDK_INT >= 26) {
                    Notification.Builder(context, channel)
                } else {
                    Notification.Builder(context)
                }
                builder.setContentTitle(title)
                    .setContentText(message)
                    .setSmallIcon(icon)
                    .setOngoing(ongoing)
                    .setAutoCancel(!ongoing)
                if (progress >= 0) {
                    builder.setProgress(100, minOf(100, maxOf(0, progress)), false)
                } else if ("indeterminate".equals(request.optString("progress_mode"), ignoreCase = true)) {
                    builder.setProgress(0, 0, true)
                }
                manager.notify(tag, id, builder.build())
                response.put("ok", true)
                response.put("tag", tag)
                response.put("id", id)
                response
            } else {
                response.put("ok", false)
                response.put("error", "NotificationManager unavailable")
                response
            }
        } catch (e: Exception) {
            Log.w(TAG, "Notification error: " + e.message)
            try {
                response.put("ok", false)
                response.put("error", e.message)
            } catch (_: Exception) {
            }
            response
        }
    }

    companion object {
        const val CHANNEL_ALERTS = "agy_alerts"
        const val CHANNEL_TASKS = "agy_tasks"
        private const val TAG = "NotificationHandler"
    }
}
