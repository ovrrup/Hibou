package com.agy.util

import android.content.Context
import java.io.InputStream

/**
 * Reads a bundled asset as UTF-8 text, `null` on any failure.
 *
 * Ported 1:1 from decompiled `com/agy/util/AssetReader.java`.
 */
object AssetReader {
    @JvmStatic
    fun readString(context: Context, path: String): String? {
        return try {
            val input: InputStream = context.assets.open(path)
            try {
                val text = IoUtil.readAll(input, "UTF-8")
                input.close()
                text
            } finally {
            }
        } catch (_: Exception) {
            null
        }
    }
}
