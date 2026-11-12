package com.agy.util

import android.net.Uri

/**
 * Origin check for URLs the WebView is allowed to trust as "the core".
 *
 * Ported 1:1 from decompiled `com/agy/util/TrustedCoreOrigin.java`.
 */
object TrustedCoreOrigin {
    @JvmStatic
    fun matches(uri: Uri?, port: Int): Boolean {
        return uri != null &&
            "https".equals(uri.scheme, ignoreCase = true) &&
            "127.0.0.1" == uri.host &&
            uri.userInfo == null &&
            uri.port == port
    }
}
