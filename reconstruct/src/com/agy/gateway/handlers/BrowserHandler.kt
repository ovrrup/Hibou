package com.agy.gateway.handlers

import android.content.Context
import android.util.Log
import com.agy.util.UriDispatcher
import org.json.JSONObject

/**
 * Gateway command `open_url`: opens a URL/file/directory via [UriDispatcher].
 *
 * Ported 1:1 from decompiled `com/agy/gateway/handlers/BrowserHandler.java`.
 */
class BrowserHandler(context: Context) {
    private val context: Context = context.applicationContext

    fun handle(request: JSONObject): JSONObject {
        val response = JSONObject()
        var url = ""
        try {
            url = request.optString("url", "").trim()
        } catch (e: Exception) {
            Log.w(TAG, "failed to open url: " + e.message)
            try {
                response.put("ok", false)
                response.put("error", e.message)
            } catch (_: Exception) {
            }
        }
        if (url.isEmpty()) {
            response.put("ok", false)
            response.put("error", "missing or empty 'url'")
            return response
        }
        response.put("ok", UriDispatcher.open(context, url))
        response.put("url", url)
        return response
    }

    private companion object {
        const val TAG = "BrowserHandler"
    }
}
