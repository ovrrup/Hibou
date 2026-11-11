package com.agy.ru

import android.content.Context

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
}
