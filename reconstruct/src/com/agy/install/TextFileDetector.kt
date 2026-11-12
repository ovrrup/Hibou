package com.agy.install

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File

/**
 * Sniffs whether bytes look like a text file and detects symlinks via
 * `lstat`.
 *
 * Ported 1:1 from decompiled `com/agy/install/TextFileDetector.java`.
 */
object TextFileDetector {
    @JvmStatic
    fun isTextFile(bytes: ByteArray): Boolean {
        if (bytes.size >= 4 && bytes[0] == Byte.MAX_VALUE && bytes[1] == 69.toByte() && bytes[2] == 76.toByte() && bytes[3] == 70.toByte()) {
            return false
        }
        for (b in bytes) {
            if (b == 0.toByte()) {
                return false
            }
        }
        return true
    }

    @JvmStatic
    fun isSymlink(file: File): Boolean {
        return try {
            OsConstants.S_ISLNK(Os.lstat(file.absolutePath).st_mode)
        } catch (_: ErrnoException) {
            false
        }
    }
}
