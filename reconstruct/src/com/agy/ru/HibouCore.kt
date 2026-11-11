package com.agy.ru

import android.app.Activity
import android.content.Context
import org.json.JSONObject

/**
 * Access to the running core process without a source-level dependency on the
 * precompiled `com.agy.ui.MainActivity`.
 *
 * `MainActivity` keeps a private `androidBridge` field of type
 * `com.agy.bridge.AndroidBridge`; that object is the single authority for the
 * loopback engine port and the CSRF token (see `tools/dex_inspect.py`).  The
 * bridge is reached reflectively so the patcher build never needs the
 * `com.agy.bridge` classes on its compile classpath.
 */
object HibouCore {
    private const val MAIN_ACTIVITY = "com.agy.ui.MainActivity"
    private const val BRIDGE_FIELD = "androidBridge"
    private const val ACTIVE_FIELD = "activeActivity"

    fun currentActivity(): Activity? {
        try {
            val cls = Class.forName(MAIN_ACTIVITY)
            val field = cls.getDeclaredField(ACTIVE_FIELD)
            field.isAccessible = true
            return field.get(null) as? Activity
        } catch (_: Throwable) {
            return null
        }
    }

    fun bridge(activity: Activity? = currentActivity()): Any? {
        var type: Class<*>? = activity?.javaClass
        while (type != null) {
            try {
                val field = type.getDeclaredField(BRIDGE_FIELD)
                field.isAccessible = true
                return field.get(activity)
            } catch (_: NoSuchFieldException) {
                type = type.superclass
            } catch (_: Throwable) {
                return null
            }
        }
        return null
    }

    private fun call(target: Any?, name: String): Any? {
        if (target == null) return null
        return try {
            val method = target.javaClass.getMethod(name)
            method.isAccessible = true
            method.invoke(target)
        } catch (_: Throwable) {
            null
        }
    }

    fun enginePort(activity: Activity? = currentActivity()): Int {
        val direct = call(bridge(activity), "getEnginePort")
        if (direct is Int && direct > 0) return direct
        val config = configJson(activity)
        val fromConfig = config?.optInt("enginePort", 0) ?: 0
        if (fromConfig > 0) return fromConfig
        return 0
    }

    fun csrfToken(activity: Activity? = currentActivity()): String {
        val direct = call(bridge(activity), "getCsrfToken") as? String
        if (!direct.isNullOrEmpty()) return direct
        return configJson(activity)?.optString("csrfToken", "") ?: ""
    }

    fun isReady(activity: Activity? = currentActivity()): Boolean {
        val ready = call(bridge(activity), "isEngineReady")
        return ready == true || enginePort(activity) > 0
    }

    /** `https://127.0.0.1:<port>` or null while the core is still starting. */
    fun baseUrl(activity: Activity? = currentActivity()): String? {
        val port = enginePort(activity)
        return if (port > 0) "https://127.0.0.1:$port" else null
    }

    private fun configJson(activity: Activity?): JSONObject? {
        val raw = call(bridge(activity), "getConfig") as? String ?: return null
        if (raw.isBlank()) return null
        return try {
            JSONObject(raw)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Metadata block the core expects on `StartCascade` and
     * `SendUserCascadeMessage`.  Only stable, non-identifying fields are sent.
     */
    fun metadata(ctx: Context): JSONObject {
        val meta = JSONObject()
        try {
            meta.put("ideName", "Hibou")
            meta.put("ideVersion", appVersion(ctx))
            meta.put("extensionName", "hibou-native")
            meta.put("extensionVersion", appVersion(ctx))
            meta.put("locale", "en")
            meta.put("os", "android")
            meta.put("productName", "hibou")
            meta.put("disableTelemetry", true)
            meta.put("sessionId", sessionId(ctx))
        } catch (_: Throwable) {
        }
        return meta
    }

    private fun appVersion(ctx: Context): String {
        return try {
            val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            info.versionName ?: "0"
        } catch (_: Throwable) {
            "0"
        }
    }

    private var cachedSession: String? = null

    private fun sessionId(ctx: Context): String {
        cachedSession?.let { return it }
        val prefs = ctx.getSharedPreferences("hibou_native_ui", Context.MODE_PRIVATE)
        val saved = prefs.getString("session_id", null)
        if (!saved.isNullOrEmpty()) {
            cachedSession = saved
            return saved
        }
        val generated = java.util.UUID.randomUUID().toString()
        prefs.edit().putString("session_id", generated).apply()
        cachedSession = generated
        return generated
    }
}
