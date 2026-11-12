package com.agy.oauth

import android.net.Uri
import java.util.Locale

/**
 * Recognizes Google/Antigravity OAuth URLs for browser interception.
 *
 * Ported 1:1 from decompiled `com/agy/oauth/AuthUrlMatcher.java`.
 */
class AuthUrlMatcher {
    fun isAuthUrl(raw: String?): Boolean {
        if (raw == null) return false
        return try {
            val uri = Uri.parse(raw)
            val scheme = uri.scheme
            if (scheme == null ||
                (!scheme.equals("http", ignoreCase = true) && !scheme.equals("https", ignoreCase = true))
            ) {
                return false
            }
            val host = uri.host ?: return false
            val lower = host.lowercase(Locale.US)
            if (lower == "accounts.google.com" ||
                lower.endsWith(".accounts.google.com") ||
                lower == "accounts.googleusercontent.com" ||
                lower == "oauth2.googleapis.com"
            ) {
                return true
            }
            val path = uri.path ?: ""
            if ((lower == "google.com" || lower.endsWith(".google.com")) && path.contains("/o/oauth2")) {
                return true
            }
            (lower == "antigravity.google" || lower.endsWith(".antigravity.google")) && path.contains("/auth")
        } catch (_: Exception) {
            false
        }
    }
}
