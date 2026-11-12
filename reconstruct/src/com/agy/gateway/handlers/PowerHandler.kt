package com.agy.gateway.handlers

import android.content.Context
import android.os.PowerManager
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject

/**
 * Gateway command `wakelock`: reference-counted partial wakelocks tagged by
 * the caller, held across gateway calls.
 *
 * Ported from decompiled `com/agy/gateway/handlers/PowerHandler.java`. Port
 * delta: the wakelock tag prefix derives from the runtime package name
 * (`<pkg>:wakelock:<tag>`) instead of the hardcoded original `agy.hibou`.
 */
class PowerHandler(context: Context) {
    private val context: Context = context.applicationContext
    private val powerManager: PowerManager? =
        context.applicationContext.getSystemService("power") as? PowerManager
    private val locks = ConcurrentHashMap<String, PowerManager.WakeLock>()

    fun handle(request: JSONObject): JSONObject {
        val response = JSONObject()
        var action: String
        var tag: String
        var timeoutMs: Long
        try {
            action = request.optString("action", "acquire")
            tag = request.optString("tag", "agy_task")
            timeoutMs = request.optLong("timeout_ms", 0L)
        } catch (e: Exception) {
            Log.w(TAG, "wakelock error: " + e.message)
            try {
                response.put("ok", false)
                response.put("error", e.message)
            } catch (_: Exception) {
            }
            return response
        }
        if (powerManager == null) {
            response.put("ok", false)
            response.put("error", "PowerManager unavailable")
            return response
        }
        cleanupUnheld()
        if ("release".equals(action, ignoreCase = true)) {
            val removed = locks.remove(tag)
            if (removed != null && removed.isHeld) {
                removed.release()
                Log.d(TAG, "Released wakelock: $tag")
            }
            response.put("ok", true)
            response.put("held", false)
            return response
        }
        if ("status".equals(action, ignoreCase = true)) {
            val wakeLock = locks[tag]
            val held = wakeLock != null && wakeLock.isHeld
            if (wakeLock != null && !held) {
                locks.remove(tag, wakeLock)
            }
            response.put("ok", true)
            response.put("held", held)
            response.put("tag", tag)
            return response
        }
        val wakeLock = locks.compute(tag) { _, existing ->
            if (existing != null && existing.isHeld) {
                existing
            } else {
                powerManager.newWakeLock(1, context.packageName + ":wakelock:" + tag)
            }
        }
        if (wakeLock != null && !wakeLock.isHeld) {
            if (timeoutMs > 0) {
                wakeLock.acquire(timeoutMs)
            } else {
                wakeLock.acquire()
            }
            Log.d(TAG, "Acquired wakelock: $tag" + (if (timeoutMs > 0) " timeout=${timeoutMs}ms" else ""))
        }
        val held = wakeLock != null && wakeLock.isHeld
        if (wakeLock != null && !held) {
            locks.remove(tag, wakeLock)
        }
        response.put("ok", true)
        response.put("held", held)
        response.put("tag", tag)
        return response
    }

    private fun cleanupUnheld() {
        val iterator = locks.entries.iterator()
        while (iterator.hasNext()) {
            val value = iterator.next().value
            if (value == null || !value.isHeld) {
                iterator.remove()
            }
        }
    }

    fun releaseAll() {
        for (tag in locks.keys) {
            val removed = locks.remove(tag)
            if (removed != null && removed.isHeld) {
                try {
                    removed.release()
                } catch (_: Exception) {
                }
            }
        }
        locks.clear()
    }

    private companion object {
        const val TAG = "PowerHandler"
    }
}
