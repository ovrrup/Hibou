package com.agy.util

import java.io.IOException
import java.io.InputStream
import kotlin.io.DEFAULT_BUFFER_SIZE

/**
 * Consumes a stream to EOF, discarding the bytes.
 *
 * Ported 1:1 from decompiled `com/agy/util/StreamDrain.java`.
 */
object StreamDrain {
    @JvmStatic
    @Throws(IOException::class)
    fun drain(input: InputStream) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (input.read(buffer) > 0) {
        }
    }
}
