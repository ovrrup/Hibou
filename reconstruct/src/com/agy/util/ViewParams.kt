package com.agy.util

import android.widget.FrameLayout

/**
 * Layout helpers for dynamically attached views.
 *
 * Ported 1:1 from decompiled `com/agy/util/ViewParams.java`.
 */
object ViewParams {
    @JvmStatic
    fun full(): FrameLayout.LayoutParams = FrameLayout.LayoutParams(-1, -1)
}
