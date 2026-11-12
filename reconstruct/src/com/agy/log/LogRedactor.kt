package com.agy.log

import java.util.regex.Pattern

/**
 * Redacts bearer tokens, CSRF tokens and key/secret assignments before data
 * reaches persisted logs, the gateway log tail, or crash reports.
 *
 * Ported 1:1 from decompiled `com/agy/log/LogRedactor.java`.
 */
object LogRedactor {
    private val BEARER_PATTERN = Pattern.compile("Bearer\\s+[A-Za-z0-9_\\-\\.~]+", Pattern.CASE_INSENSITIVE)
    private val CSRF_PATTERN = Pattern.compile("csrf[_-]?token[=:\\s]+([A-Za-z0-9_\\-]+)", Pattern.CASE_INSENSITIVE)
    private val KEY_PATTERN = Pattern.compile("(api[_-]?key|secret|password)[=:\\s]+([A-Za-z0-9_\\-]+)", Pattern.CASE_INSENSITIVE)

    @JvmStatic
    fun redact(text: String?): String? {
        if (text == null || text.isEmpty()) return text
        val a = BEARER_PATTERN.matcher(text).replaceAll("Bearer [REDACTED]")
        val b = CSRF_PATTERN.matcher(a).replaceAll("csrf_token=[REDACTED]")
        return KEY_PATTERN.matcher(b).replaceAll("\$1=[REDACTED]")
    }
}
