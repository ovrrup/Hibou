package com.agy.core

import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Starts/stops [CoreServerService] as a foreground service on the API levels
 * that require it.
 *
 * Ported 1:1 from decompiled `com/agy/core/CoreServiceLauncher.java`.
 */
object CoreServiceLauncher {
    @JvmStatic
    fun start(context: Context) {
        val intent = Intent(context, CoreServerService::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    @JvmStatic
    fun stop(context: Context) {
        try {
            context.stopService(Intent(context, CoreServerService::class.java))
        } catch (_: Exception) {
        }
    }
}
