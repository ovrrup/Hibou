package com.agy.install

import android.util.Log
import com.agy.util.IoUtil
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.regex.Matcher

/**
 * Rewrites Termux-style `#!…/files/usr/…` and `#!/usr/bin/env …` shebangs in
 * the prefix's script dirs to point at the local prefix.
 *
 * Ported from decompiled `com/agy/install/TermuxShebangRewriter.java`. The
 * JNA constant used for the read buffer (`Function.MAX_NARGS`) is inlined as
 * its literal value 256.
 */
object TermuxShebangRewriter {
    private const val TAG = "TermuxShebangRewriter"
    private const val READ_BUFFER = 256 // was com.sun.jna.Function.MAX_NARGS

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
            Log.i(TAG, "rewrote $count termux shebang(s) -> $targetPrefixPath")
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
        if (file.length() < 2) {
            return false
        }
        val head = ByteArray(READ_BUFFER)
        var headLen: Int
        var isTermux: Boolean
        var isEnv: Boolean
        try {
            var input = FileInputStream(file)
            try {
                headLen = IoUtil.readFully(input, head)
                input.close()
                if (headLen < 4 || head[0] != 35.toByte() || head[1] != 33.toByte()) {
                    return false
                }
                var newlineAt = 2
                while (true) {
                    if (newlineAt >= headLen) {
                        newlineAt = -1
                        break
                    }
                    if (head[newlineAt] == 10.toByte()) {
                        break
                    }
                    newlineAt++
                }
                if (newlineAt < 0 && headLen == READ_BUFFER) {
                    return false
                }
                if (newlineAt >= 0) {
                    headLen = newlineAt
                }
                val firstLine = String(head, 0, headLen, StandardCharsets.ISO_8859_1)
                val trimmed = if (firstLine.endsWith("\r")) {
                    firstLine.substring(0, firstLine.length - 1)
                } else {
                    firstLine
                }
                isTermux = EnvPaths.TERMUX_SHEBANG.matcher(trimmed).matches()
                isEnv = EnvPaths.ENV_SHEBANG.matcher(trimmed).matches()
                if (!isTermux && !isEnv) {
                    return false
                }
                input = FileInputStream(file)
                try {
                    val bytes = IoUtil.readAllBytes(input)
                    input.close()
                    val text = String(bytes, StandardCharsets.ISO_8859_1)
                    var rewritten = text
                    if (isTermux) {
                        rewritten = EnvPaths.TERMUX_SHEBANG_LINES.matcher(text)
                            .replaceAll("#!" + Matcher.quoteReplacement(targetPrefixPath) + "$2")
                    }
                    if (isEnv || EnvPaths.ENV_SHEBANG_LINES.matcher(rewritten).find()) {
                        rewritten = EnvPaths.ENV_SHEBANG_LINES.matcher(rewritten)
                            .replaceAll("#!" + Matcher.quoteReplacement(targetPrefixPath) + "/bin/$1$2")
                    }
                    if (rewritten == text) {
                        return false
                    }
                    try {
                        val out = FileOutputStream(file)
                        try {
                            out.write(rewritten.toByteArray(StandardCharsets.ISO_8859_1))
                            out.close()
                            return true
                        } finally {
                        }
                    } catch (e: IOException) {
                        Log.w(TAG, "cannot rewrite shebang $file: $e")
                        return false
                    }
                } catch (e: IOException) {
                    Log.w(TAG, "cannot read $file: $e")
                    return false
                }
            } finally {
                try {
                    input.close()
                } catch (suppressed: Throwable) {
                    suppressed.addSuppressed(suppressed)
                }
            }
        } catch (_: IOException) {
            return false
        }
    }
}
