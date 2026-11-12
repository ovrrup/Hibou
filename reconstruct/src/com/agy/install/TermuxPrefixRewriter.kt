package com.agy.install

import android.util.Log
import com.agy.util.AtomicFileWriter
import com.agy.util.IoUtil
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.regex.Matcher

/**
 * Rewrites any Android app files prefix found in text files of the prefix's
 * script dirs onto the local prefix (binary-safe ISO-8859-1 round trip).
 *
 * Ported 1:1 from decompiled `com/agy/install/TermuxPrefixRewriter.java`.
 */
object TermuxPrefixRewriter {
    private const val TAG = "TermuxPrefixRewriter"

    @JvmStatic
    fun rewrite(prefix: File) {
        rewrite(prefix, prefix.absolutePath)
    }

    @JvmStatic
    fun rewrite(prefix: File, targetPrefixPath: String) {
        var count = 0
        for (dir in EnvPaths.SCRIPT_DIRS) {
            count += rewriteIn(File(prefix, dir), targetPrefixPath)
        }
        if (count > 0) {
            Log.i(TAG, "rewrote termux paths in $count file(s) -> $targetPrefixPath")
        }
    }

    private fun rewriteIn(dir: File, targetPrefixPath: String): Int {
        val children = dir.listFiles() ?: return 0
        var count = 0
        for (child in children) {
            if (!TextFileDetector.isSymlink(child)) {
                if (child.isDirectory) {
                    count += rewriteIn(child, targetPrefixPath)
                } else if (child.isFile && rewriteFile(child, targetPrefixPath)) {
                    count++
                }
            }
        }
        return count
    }

    private fun rewriteFile(file: File, targetPrefixPath: String): Boolean {
        try {
            val input = FileInputStream(file)
            try {
                val bytes = IoUtil.readAllBytes(input)
                input.close()
                if (!TextFileDetector.isTextFile(bytes)) {
                    return false
                }
                val text = String(bytes, StandardCharsets.ISO_8859_1)
                if (!text.contains(EnvPaths.PREFIX_MARKER)) {
                    return false
                }
                val rewritten = EnvPaths.TERMUX_ANY_PREFIX.matcher(text)
                    .replaceAll(Matcher.quoteReplacement(targetPrefixPath))
                if (rewritten == text) {
                    return false
                }
                return AtomicFileWriter.writeBytes(file, rewritten.toByteArray(StandardCharsets.ISO_8859_1))
            } finally {
            }
        } catch (_: IOException) {
            return false
        }
    }
}
