package com.agy.ru

import android.content.Context

/**
 * Backward compatibility delegation wrapper in Kotlin.
 */
object LeostryxUpdates {
    @JvmStatic
    fun getAppInfo(ctx: Context): String = HibouUpdates.getAppInfo(ctx)

    @JvmStatic
    fun installUpdate(ctx: Context): String = HibouUpdates.installUpdate(ctx)
}
