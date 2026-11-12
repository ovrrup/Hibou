package com.agy.core

import android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.os.Build
import com.agy.BuildConfig

/**
 * The persistent foreground notification of [CoreServerService] (channel
 * `core_channel`, id 1001 — both are contracts with the web layer's status UI).
 *
 * Ported 1:1 from decompiled `com/agy/core/ForegroundNotification.java`. The
 * title comes from `com.agy.BuildConfig.APP_NAME`, which now carries the new
 * app identity.
 */
object ForegroundNotification {
    const val CHANNEL_ID = "core_channel"
    const val NOTIFICATION_ID = 1001

    @JvmStatic
    fun start(service: Service) {
        val notificationManager =
            service.getSystemService("notification") as? NotificationManager
        if (Build.VERSION.SDK_INT >= 26 && notificationManager != null) {
            notificationManager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Core", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = build(service, "Core running")
        com.kestrel.diag.FlightLog.w("NOTIF", "calling startForeground")
        if (Build.VERSION.SDK_INT >= 29) {
            service.startForeground(NOTIFICATION_ID, notification, 1)
        } else {
            service.startForeground(NOTIFICATION_ID, notification)
        }
    }

    @JvmStatic
    fun update(service: Service, text: String) {
        val notificationManager =
            service.getSystemService("notification") as? NotificationManager
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, build(service, text))
        }
    }

    private fun build(service: Service, text: String): Notification {
        var icon = service.resources.getIdentifier("ic_notification", "drawable", service.packageName)
        if (icon == 0) {
            icon = R.drawable.stat_notify_sync
        }
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(service, CHANNEL_ID)
        } else {
            Notification.Builder(service)
        }
        return builder
            .setContentTitle(BuildConfig.APP_NAME)
            .setContentText(text)
            .setSmallIcon(icon)
            .setOngoing(true)
            .build()
    }
}
