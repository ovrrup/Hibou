package com.agy.gateway.handlers

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import org.json.JSONObject

/**
 * Gateway commands `system_info`, `vibrate` and `toast`.
 *
 * Ported 1:1 from decompiled `com/agy/gateway/handlers/DeviceHandler.java`.
 */
class DeviceHandler(context: Context) {
    private val context: Context = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    fun handleSystemInfo(): JSONObject {
        val response = JSONObject()
        return try {
            response.put("ok", true)
            val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (battery != null) {
                var level = battery.getIntExtra("level", -1)
                val scale = battery.getIntExtra("scale", -1)
                val status = battery.getIntExtra("status", -1)
                val charging = status == 2 || status == 5
                if (scale > 0) {
                    level = (level / scale * 100.0f).toInt()
                }
                response.put("battery", level)
                response.put("charging", charging)
            }
            val connectivity = context.getSystemService("connectivity") as? ConnectivityManager
            if (connectivity != null) {
                val info = connectivity.activeNetworkInfo
                if (info == null || !info.isConnected) {
                    response.put("network", "offline")
                } else {
                    response.put("network", info.typeName.lowercase())
                }
            }
            response.put("storage_free_mb", context.filesDir.usableSpace / 1048576)
            val runtime = Runtime.getRuntime()
            response.put(
                "memory_free_mb",
                (runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())) / 1048576
            )
            response
        } catch (e: Exception) {
            try {
                response.put("ok", false)
                response.put("error", e.message)
            } catch (_: Exception) {
            }
            response
        }
    }

    fun handleVibrate(request: JSONObject): JSONObject {
        val response = JSONObject()
        return try {
            val vibrator = context.getSystemService("vibrator") as? Vibrator
            if (vibrator != null && vibrator.hasVibrator()) {
                val duration = request.optLong("duration_ms", 100L)
                if (Build.VERSION.SDK_INT >= 26) {
                    vibrator.vibrate(VibrationEffect.createOneShot(duration, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(duration)
                }
                response.put("ok", true)
            } else {
                response.put("ok", false)
                response.put("error", "vibrator not available")
            }
            response
        } catch (e: Exception) {
            try {
                response.put("ok", false)
                response.put("error", e.message)
            } catch (_: Exception) {
            }
            response
        }
    }

    fun handleToast(request: JSONObject): JSONObject {
        val response = JSONObject()
        return try {
            val message = request.optString("message", "")
            val long = "long".equals(request.optString("duration"), ignoreCase = true)
            mainHandler.post {
                Toast.makeText(context, message, if (long) 1 else 0).show()
            }
            response.put("ok", true)
            response
        } catch (e: Exception) {
            try {
                response.put("ok", false)
                response.put("error", e.message)
            } catch (_: Exception) {
            }
            response
        }
    }
}
