package com.agy.util

import android.net.Uri
import java.util.Locale
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Redacts secrets from anything written to logs or the log ring buffer.
 *
 * Ported 1:1 from decompiled `com/agy/util/SensitiveDataRedactor.java`. The
 * regexes are contracts with the log consumers; exact ordering of the three
 * replace passes (secret fields, then auth headers, then bearer tokens) is
 * preserved.
 */
object SensitiveDataRedactor {
    private val URL: Pattern = Pattern.compile("(?i)(?:https?|wss?)://[^\\s\\\"'<>]+")
    private val SECRET_FIELD: Pattern = Pattern.compile(
        "(?i)([\\\"]?(?:access[_-]?token|refresh[_-]?token|id[_-]?token|csrf[_-]?token|auth[_-]?token|oauth[_-]?token|client[_-]?secret|device[_-]?code|code[_-]?verifier|session[_-]?state|password|passwd|api[_-]?key|secret)[\\\"]?\\s*[:=]\\s*[\\\"]?)([^\\\"'\\s,;&}]+)"
    )
    private val SENSITIVE_HEADER: Pattern = Pattern.compile(
        "(?i)([\\\"]?(?:authorization|cookie)[\\\"]?\\s*[:=]\\s*)[^,}\\r\\n]+"
    )
    private val BEARER: Pattern = Pattern.compile("(?i)(\\bBearer\\s+)[A-Za-z0-9._~+/-]+=*")

    @JvmStatic
    fun safeUrl(raw: String?): String {
        if (raw.isNullOrEmpty()) return "<url>"
        return try {
            val uri = Uri.parse(raw)
            val scheme = uri.scheme
            val host = uri.host
            if (scheme != null && !host.isNullOrEmpty()) {
                val sb = StringBuilder(scheme.lowercase(Locale.US)).append("://").append(host)
                val port = uri.port
                if (port >= 0) sb.append(':').append(port)
                sb.append("/<redacted>").toString()
            } else {
                "<url>"
            }
        } catch (_: Exception) {
            "<url>"
        }
    }

    @JvmStatic
    fun redactLine(line: String): String {
        if (line.isEmpty()) return line
        val urls = StringBuffer()
        val urlMatcher = URL.matcher(line)
        while (urlMatcher.find()) {
            urlMatcher.appendReplacement(urls, Matcher.quoteReplacement(safeUrl(urlMatcher.group())))
        }
        urlMatcher.appendTail(urls)
        var out = SECRET_FIELD.matcher(urls.toString()).replaceAll("\$1<redacted>")
        out = SENSITIVE_HEADER.matcher(out).replaceAll("\$1<redacted>")
        out = BEARER.matcher(out).replaceAll("\$1<redacted>")
        return out
    }
}
