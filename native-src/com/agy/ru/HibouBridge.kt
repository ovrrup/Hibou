package com.agy.ru

import android.content.Context

/**
 * Modern native bridge helper for Hibou mobile app in Kotlin.
 */
object HibouBridge {
    @JvmStatic
    fun getAppInfo(ctx: Context): String = HibouUpdates.getAppInfo(ctx)

    @JvmStatic
    fun installUpdate(ctx: Context): String = HibouUpdates.installUpdate(ctx)

    @JvmStatic
    fun getPlatformArch(): String = "arm64-v8a"

    @JvmStatic
    fun isSupportedAbi(abi: String): Boolean = abi == "arm64-v8a" || abi == "armeabi-v7a"
}
