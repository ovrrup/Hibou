package com.agy.util

import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest

/**
 * Streaming SHA-256 helpers backing the runtime manifest verification.
 *
 * Ported 1:1 from decompiled `com/agy/util/Sha256.java`.
 */
object Sha256 {
    @JvmStatic
    @Throws(Exception::class)
    fun of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = FileInputStream(file)
        try {
            val buffer = ByteArray(65536)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
            return hex(digest.digest())
        } finally {
            input.close()
        }
    }

    @JvmStatic
    @Throws(Exception::class)
    fun of(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(65536)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
        return hex(digest.digest())
    }

    private fun hex(digest: ByteArray): String {
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            sb.append(Character.forDigit((b.toInt() shr 4) and 0x0F, 16))
            sb.append(Character.forDigit(b.toInt() and 0x0F, 16))
        }
        return sb.toString()
    }
}
