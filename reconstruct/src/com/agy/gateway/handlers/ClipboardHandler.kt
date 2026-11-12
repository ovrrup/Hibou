package com.agy.gateway.handlers

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject

/**
 * Gateway command `clipboard`/`clipboard_set`. Reads must hop to the main
 * thread and block the gateway worker for at most 1.5s.
 *
 * Ported 1:1 from decompiled `com/agy/gateway/handlers/ClipboardHandler.java`.
 */
class ClipboardHandler(context: Context) {
    private val context: Context = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    fun handle(request: JSONObject): JSONObject {
        val response = JSONObject()
        try {
            val action = request.optString("action", "set")
            if ("get".equals(action, ignoreCase = true)) {
                val reference = AtomicReference("")
                val latch = CountDownLatch(1)
                mainHandler.post {
                    try {
                        try {
                            val clipboard = context.getSystemService("clipboard") as? ClipboardManager
                            if (clipboard != null && clipboard.hasPrimaryClip()) {
                                val clip = clipboard.primaryClip
                                if (clip != null && clip.itemCount > 0) {
                                    val text = clip.getItemAt(0).text
                                    if (text != null) {
                                        reference.set(text.toString())
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "get clipboard failed: " + e.message)
                        }
                    } finally {
                        latch.countDown()
                    }
                }
                latch.await(1500L, TimeUnit.MILLISECONDS)
                response.put("ok", true)
                response.put("text", reference.get())
                return response
            }
            val text = request.optString("text", "")
            mainHandler.post {
                try {
                    val clipboard = context.getSystemService("clipboard") as? ClipboardManager
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(ClipData.newPlainText("antigravity", text))
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "set clipboard failed: " + e.message)
                }
            }
            response.put("ok", true)
            response.put("length", text.length)
            return response
        } catch (e: Exception) {
            Log.w(TAG, "clipboard error: " + e.message)
            try {
                response.put("ok", false)
                response.put("error", e.message)
            } catch (_: Exception) {
            }
            return response
        }
    }

    private companion object {
        const val TAG = "ClipboardHandler"
    }
}
