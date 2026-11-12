package com.agy.runtime

import android.content.Context

/**
 * Build identity and fixed ports of the runtime layer. The HTTPS port is
 * pinned to 48255 (the engine and web layer both assume it).
 *
 * Ported 1:1 from decompiled `com/agy/runtime/RuntimeConfig.java`.
 */
object RuntimeConfig {
    const val APP_VERSION_CODE = 17
    const val APP_VERSION_NAME = "2.4.1"
    const val CORE_VERSION = "2.11.0"
    const val DEBUG = false
    const val DEFAULT_HTTPS_PORT = 48155
    const val MIN_API = 24
    const val TARGET_SDK = 28

    @JvmStatic
    fun httpsPort(context: Context?): Int = 48255
}
