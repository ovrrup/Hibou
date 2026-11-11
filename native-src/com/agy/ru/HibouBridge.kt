package com.agy.ru

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import org.json.JSONObject

/**
 * Modern native bridge helper for Hibou mobile app in Kotlin.
 */
object HibouBridge {
    @JvmStatic
    fun getAppInfo(ctx: Context): String = HibouUpdates.getAppInfo(ctx)

    @JvmStatic
    fun installUpdate(ctx: Context): String = HibouUpdates.installUpdate(ctx)

    @JvmStatic
    fun getPlatformArch(): String = "arm64-v8a"

    @JvmStatic
    fun isSupportedAbi(abi: String): Boolean = abi == "arm64-v8a" || abi == "armeabi-v7a"

    @JvmStatic
    fun checkPermissions(ctx: Context): String {
        val root = JSONObject()
        try {
            // 1. Storage / All files access
            val storageGranted = if (Build.VERSION.SDK_INT >= 30) {
                Environment.isExternalStorageManager()
            } else {
                ctx.checkSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE") == PackageManager.PERMISSION_GRANTED
            }
            root.put("storage", storageGranted)

            // 2. Notifications
            val notifGranted = if (Build.VERSION.SDK_INT >= 33) {
                ctx.checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED
            } else {
                val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.areNotificationsEnabled() ?: true
            }
            root.put("notifications", notifGranted)

            // 3. Microphone
            val micGranted = ctx.checkSelfPermission("android.permission.RECORD_AUDIO") == PackageManager.PERMISSION_GRANTED
            root.put("microphone", micGranted)
        } catch (e: Exception) {
            root.put("error", e.message ?: "unknown")
        }
        return root.toString()
    }

    @JvmStatic
    fun requestPermission(ctx: Context, type: String?): String {
        try {
            val t = (type ?: "").lowercase().trim()
            if (t == "storage" || t == "files") {
                if (Build.VERSION.SDK_INT >= 30) {
                    val intent = Intent("android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION")
                    intent.setData(Uri.parse("package:" + ctx.packageName))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        ctx.startActivity(intent)
                        return "opened:manage_app_all_files"
                    } catch (_: Exception) {
                        val fallback = Intent("android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION")
                        fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ctx.startActivity(fallback)
                        return "opened:manage_all_files"
                    }
                } else {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.setData(Uri.parse("package:" + ctx.packageName))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(intent)
                    return "opened:app_details"
                }
            } else if (t == "notifications" || t == "notif") {
                val intent = Intent("android.settings.APP_NOTIFICATION_SETTINGS")
                intent.putExtra("android.provider.extra.APP_PACKAGE", ctx.packageName)
                intent.putExtra("app_package", ctx.packageName)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    ctx.startActivity(intent)
                    return "opened:notification_settings"
                } catch (_: Exception) {
                    val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    fallback.setData(Uri.parse("package:" + ctx.packageName))
                    fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(fallback)
                    return "opened:app_details"
                }
            } else if (t == "microphone" || t == "mic" || t == "audio") {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.setData(Uri.parse("package:" + ctx.packageName))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(intent)
                return "opened:app_details"
            } else {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.setData(Uri.parse("package:" + ctx.packageName))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(intent)
                return "opened:app_details"
            }
        } catch (e: Exception) {
            return "error:" + e.javaClass.simpleName
        }
    }

    @JvmStatic
    fun normalizeEndpoint(base: String?, type: String?): String {
        var b = (base ?: "").trim().trimEnd('/')
        if (b.isEmpty()) return b
        if (b.contains("openrouter.ai", ignoreCase = true)) {
            b = b.replace(Regex("/+(api(/v1)?|v1)?/?$", RegexOption.IGNORE_CASE), "") + "/api/v1"
            return b
        }
        if (b.contains("opencode.ai", ignoreCase = true) || type.equals("opencode", ignoreCase = true)) {
            b = if (b.contains("localhost") || b.contains("127.0.0.1") || b.contains(":4096")) {
                b.replace(Regex("/+v1/?$", RegexOption.IGNORE_CASE), "") + "/v1"
            } else {
                b.replace(Regex("/+(zen(/v1)?|api(/v1)?|v1)?/?$", RegexOption.IGNORE_CASE), "") + "/zen/v1"
            }
            return b
        }
        if (b.contains("api.deepseek.com", ignoreCase = true) ||
            b.contains("api.mistral.ai", ignoreCase = true)) {
            b = b.replace(Regex("/+v1/?$", RegexOption.IGNORE_CASE), "") + "/v1"
            return b
        }
        if (type.equals("anthropic", ignoreCase = true)) {
            b = b.replace(Regex("/v1$", RegexOption.IGNORE_CASE), "")
        }
        return b
    }

    @JvmStatic
    fun sanitizeErrorBody(body: String?): String {
        if (body.isNullOrBlank()) return ""
        val trimmed = body.trim()
        try {
            val json = org.json.JSONObject(trimmed)
            if (json.has("error")) {
                val err = json.get("error")
                if (err is org.json.JSONObject && err.has("message")) {
                    return err.getString("message").take(200)
                } else if (err is String) {
                    return err.take(200)
                }
            }
            if (json.has("message")) {
                return json.getString("message").take(200)
            }
        } catch (_: Exception) {}

        val noScripts = trimmed
            .replace(Regex("<script\\b[^<]*(?:(?!</script>)<[^<]*)*</script>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<style\\b[^<]*(?:(?!</style>)<[^<]*)*</style>", RegexOption.IGNORE_CASE), " ")
        val titleMatch = Regex("<title[^>]*>([^<]+)</title>", RegexOption.IGNORE_CASE).find(trimmed)
        val title = titleMatch?.groupValues?.get(1)?.trim()
        val text = noScripts.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()

        if (text.contains("function(") || text.contains("sendBeacon") || text.isEmpty()) {
            if (!title.isNullOrEmpty()) return title.take(150)
            return "Server returned HTML error page instead of JSON"
        }
        if (text.length > 200) {
            if (!title.isNullOrEmpty() && title.length < text.length) return title.take(150)
            return text.take(200)
        }
        return if (text.isNotEmpty()) text else (title ?: "Unknown response")
    }

    @JvmStatic
    fun openControlCenter(ctx: Context?): String {
        val activity = (ctx as? android.app.Activity) ?: try {
            val cls = Class.forName("com.agy.ui.MainActivity")
            cls.getField("activeActivity").get(null) as? android.app.Activity
        } catch (_: Exception) {
            null
        }
        if (activity != null) {
            HibouControlCenterDialog.show(activity)
            return "ok"
        }
        return "error:no_activity"
    }

    @JvmStatic
    fun showPermissionSheet(ctx: Context?, type: String?): String {
        val activity = (ctx as? android.app.Activity) ?: try {
            val cls = Class.forName("com.agy.ui.MainActivity")
            cls.getField("activeActivity").get(null) as? android.app.Activity
        } catch (_: Exception) {
            null
        }
        if (activity != null) {
            HibouPermissionSheet.show(activity, type)
            return "ok"
        }
        return "error:no_activity"
    }

    @JvmStatic
    fun showNativeSplash(parent: android.view.ViewGroup?, fg: String?, bg: String?): android.view.View? {
        return HibouNativeSplash.show(parent, fg, bg)
    }

    @JvmStatic
    fun hideNativeSplash(parent: android.view.ViewGroup?) {
        HibouNativeSplash.hide(parent)
    }

    @JvmStatic
    fun showFatalError(parent: android.view.ViewGroup?, title: String?, err: String?, retry: Runnable?) {
        HibouNativeSplash.showFatalError(parent, title, err, retry)
    }
}

