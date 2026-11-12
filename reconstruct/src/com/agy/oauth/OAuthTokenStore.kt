package com.agy.oauth

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.nio.charset.StandardCharsets

/**
 * Checks the engine's stored OAuth token file (`.gemini/jetski-standalone-
 * oauth-token`) for a non-null token value.
 *
 * Ported 1:1 from decompiled `com/agy/oauth/OAuthTokenStore.java`.
 */
object OAuthTokenStore {
    private const val MAX_SCAN = 8192

    @JvmStatic
    fun tokenFile(context: Context?): File? {
        if (context == null) return null
        return File(File(context.filesDir, ".gemini"), OAuthConstants.TOKEN_NAME)
    }

    @JvmStatic
    fun hasToken(context: Context?): Boolean {
        val file = tokenFile(context)
        if (file == null || !file.isFile || file.length() == 0L) {
            return false
        }
        return try {
            val input = FileInputStream(file)
            try {
                val buffer = ByteArray(minOf(file.length(), 8192L).toInt())
                val read = input.read(buffer)
                if (read > 0) {
                    val has = !isNullOrBlankToken(String(buffer, 0, read, StandardCharsets.UTF_8))
                    input.close()
                    has
                } else {
                    input.close()
                    false
                }
            } finally {
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun isNullOrBlankToken(text: String?): Boolean {
        if (text == null) return true
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return true
        val tokenKey = trimmed.indexOf("\"token\"")
        if (tokenKey < 0) return false
        val colon = trimmed.indexOf(':', tokenKey)
        if (colon < 0) return false
        var index = colon + 1
        while (index < trimmed.length && trimmed[index].isWhitespace()) {
            index++
        }
        return trimmed.startsWith("null", index)
    }
}
