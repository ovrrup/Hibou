package com.agy.install

import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * Minimal tar header parsing helpers for the streaming extractor.
 *
 * Ported 1:1 from decompiled `com/agy/install/TarHeader.java`.
 */
object TarHeader {
    @Throws(IOException::class)
    fun readBlock(input: InputStream, block: ByteArray): Boolean {
        var offset = 0
        while (offset < block.size) {
            val read = input.read(block, offset, block.size - offset)
            if (read < 0) {
                if (offset == 0) {
                    return false
                }
                throw IOException("truncated tar header")
            }
            offset += read
        }
        return true
    }

    fun isZeroBlock(block: ByteArray): Boolean {
        for (b in block) {
            if (b != 0.toByte()) {
                return false
            }
        }
        return true
    }

    fun readField(block: ByteArray, offset: Int, length: Int): String {
        val end = length + offset
        var index = offset
        while (index < end && block[index] != 0.toByte()) {
            index++
        }
        return String(block, offset, index - offset, StandardCharsets.UTF_8)
    }

    fun readOctal(block: ByteArray, offset: Int, length: Int): Long {
        var start = offset
        val end = length + offset
        while (start < end) {
            val b = block[start]
            if (b != 32.toByte() && b != 0.toByte()) {
                break
            }
            start++
        }
        var digitsEnd = start
        while (digitsEnd < end) {
            val b = block[digitsEnd]
            if (b < 48 || b > 55) {
                break
            }
            digitsEnd++
        }
        if (digitsEnd == start) {
            return 0L
        }
        return String(block, start, digitsEnd - start, StandardCharsets.US_ASCII).toLong(8)
    }
}
