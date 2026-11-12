package com.agy.util

import android.os.Handler
import android.os.Looper

/**
 * Main-thread hop helper.
 *
 * Ported 1:1 from decompiled `com/agy/util/MainThreadPost.java`.
 */
object MainThreadPost {
    private val MAIN = Handler(Looper.getMainLooper())

    @JvmStatic
    fun post(runnable: Runnable) {
        MAIN.post(runnable)
    }
}
