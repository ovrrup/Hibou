package com.agy.util

/**
 * URL helpers for the local core HTTPS server.
 *
 * Ported 1:1 from decompiled `com/agy/util/CoreUrl.java`. The URL shape is a
 * contract with the engine (`?csrf_token=` query is validated by the core).
 */
object CoreUrl {
    @JvmStatic
    fun startUrl(port: Int): String = "https://127.0.0.1:$port/"

    @JvmStatic
    fun startUrl(port: Int, csrfToken: String?): String {
        val base = startUrl(port)
        return if (csrfToken == null) base else "${base}?csrf_token=$csrfToken"
    }

    @JvmStatic
    fun baseUrl(port: Int): String = "https://127.0.0.1:$port"
}
