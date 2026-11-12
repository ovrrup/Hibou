package com.agy.util

import com.agy.ru.HibouConnect

/**
 * MIME sniffing by extension for the local gateway file endpoints. Note that
 * `.json` maps to the Connect content type, matching the original bundle.
 *
 * Ported 1:1 from decompiled `com/agy/util/MimeTypes.java`.
 */
object MimeTypes {
    @JvmStatic
    fun isTextMime(mime: String?): Boolean {
        if (mime == null) return false
        return mime.startsWith("text/") ||
            mime == "application/javascript" ||
            mime == HibouConnect.UNARY_TYPE ||
            mime == "image/svg+xml"
    }

    @JvmStatic
    fun getMimeType(name: String?): String {
        if (name == null) return "application/octet-stream"
        val lower = name.lowercase()
        if (lower.endsWith(".js") || lower.endsWith(".mjs")) return "application/javascript"
        if (lower.endsWith(".css")) return "text/css"
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html"
        if (lower.endsWith(".json")) return HibouConnect.UNARY_TYPE
        if (lower.endsWith(".png")) return "image/png"
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg"
        return when {
            lower.endsWith(".svg") -> "image/svg+xml"
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".gif") -> "image/gif"
            lower.endsWith(".ico") -> "image/x-icon"
            lower.endsWith(".mp3") -> "audio/mpeg"
            lower.endsWith(".wav") -> "audio/wav"
            lower.endsWith(".woff2") -> "font/woff2"
            lower.endsWith(".woff") -> "font/woff"
            lower.endsWith(".ttf") -> "font/ttf"
            else -> "application/octet-stream"
        }
    }
}
