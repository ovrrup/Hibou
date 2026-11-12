package com.agy.util

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import kotlin.io.DEFAULT_BUFFER_SIZE

/**
 * Stream and file IO helpers used across the installer, runtime, and shell
 * bridges.
 *
 * Ported 1:1 from decompiled `com/agy/util/IoUtil.java`.
 */
object IoUtil {
    @JvmStatic
    @Throws(IOException::class)
    fun pipe(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(65536)
        while (true) {
            val read = input.read(buffer)
            if (read > 0) {
                output.write(buffer, 0, read)
            } else {
                return
            }
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun readAllBytes(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read > 0) {
                out.write(buffer, 0, read)
            } else {
                return out.toByteArray()
            }
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun readAll(input: InputStream, charsetName: String): String {
        return String(readAllBytes(input), Charset.forName(charsetName))
    }

    @JvmStatic
    @Throws(IOException::class)
    fun readFully(input: InputStream, target: ByteArray): Int {
        var total = 0
        while (total < target.size) {
            val read = input.read(target, total, target.size - total)
            if (read < 0) break
            total += read
        }
        return total
    }

    @JvmStatic
    fun closeQuietly(closeable: Closeable?) {
        if (closeable != null) {
            try {
                closeable.close()
            } catch (_: Exception) {
            }
        }
    }

    @JvmStatic
    fun deleteQuietly(file: File?) {
        if (file == null || !file.exists()) return
        file.delete()
    }
}
