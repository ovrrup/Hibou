package com.agy.core

import android.content.Context
import android.os.PowerManager
import android.util.Log

/**
 * Partial wakelock held while the core service runs.
 *
 * Ported 1:1 from decompiled `com/agy/core/WakeLockHolder.java`.
 */
class WakeLockHolder {
    private var wakeLock: PowerManager.WakeLock? = null

    fun acquire(context: Context) {
        try {
            val powerManager = context.getSystemService("power") as? PowerManager
            if (powerManager != null) {
                wakeLock = powerManager.newWakeLock(1, "agy::core")
                wakeLock?.acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "wakelock failed", e)
        }
    }

    fun release() {
        val lock = wakeLock
        if (lock != null && lock.isHeld) {
            lock.release()
        }
    }

    private companion object {
        const val TAG = "WakeLockHolder"
    }
}
