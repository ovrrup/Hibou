package com.agy.util

import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset

/**
 * Crash-safe text/bytes writer: writes to a temp sibling then renames, so
 * readers never observe half-written state (markers, DNS config, seed files).
 *
 * Ported 1:1 from decompiled `com/agy/util/AtomicFileWriter.java`.
 */
object AtomicFileWriter {
    @JvmStatic
    fun writeText(file: File, text: String) {
        val parent = file.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return
        }
        val tmp = File(parent, file.name + ".tmp")
        try {
            val out = FileOutputStream(tmp)
            try {
                out.write(text.toByteArray(Charset.forName("UTF-8")))
                out.flush()
                out.fd.sync()
                out.close()
                if (tmp.renameTo(file)) return
                if (file.delete() && tmp.renameTo(file)) return
                IoUtil.deleteQuietly(tmp)
            } finally {
            }
        } catch (_: Exception) {
            IoUtil.deleteQuietly(tmp)
        }
    }

    @JvmStatic
    fun writeBytes(file: File, bytes: ByteArray): Boolean {
        var tmp: File? = null
        try {
            tmp = File.createTempFile(file.name + ".", ".tmp", file.parentFile)
            val out = FileOutputStream(tmp)
            try {
                out.write(bytes)
                out.close()
                FileModeUtil.copyPermissions(file, tmp)
                if (!tmp.renameTo(file)) {
                    val direct = FileOutputStream(file)
                    try {
                        direct.write(bytes)
                        direct.close()
                        IoUtil.deleteQuietly(tmp)
                        return true
                    } finally {
                    }
                }
                return true
            } finally {
            }
        } catch (_: Exception) {
            IoUtil.deleteQuietly(tmp)
            return false
        }
    }
}
