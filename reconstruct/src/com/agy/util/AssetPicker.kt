package com.agy.util

import android.content.res.AssetManager
import java.io.IOException

/**
 * Returns the first asset path among candidates that exists in the APK.
 *
 * Ported 1:1 from decompiled `com/agy/util/AssetPicker.java`.
 */
object AssetPicker {
    @JvmStatic
    fun pick(assets: AssetManager, vararg candidates: String): String? {
        for (candidate in candidates) {
            val input = try {
                assets.open(candidate)
            } catch (_: IOException) {
                null
            } ?: continue
            input.close()
            return candidate
        }
        return null
    }
}
