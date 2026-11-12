package com.agy.install

import android.system.ErrnoException
import android.system.Os
import android.util.Log
import java.io.File
import java.util.UUID

/**
 * Repoints symlinks that target any Android app files prefix onto the local
 * prefix, via symlink-then-rename (atomic swap).
 *
 * Ported 1:1 from decompiled `com/agy/install/TermuxSymlinkFixer.java`.
 */
object TermuxSymlinkFixer {
    private const val TAG = "TermuxSymlinkFixer"

    @JvmStatic
    fun fix(prefix: File): Int {
        return fix(prefix, prefix.absolutePath)
    }

    @JvmStatic
    fun fix(prefix: File, targetPrefixPath: String): Int {
        val fixed = fixIn(prefix, targetPrefixPath)
        if (fixed > 0) {
            Log.i(TAG, "repointed $fixed Android-prefix symlink(s) -> $targetPrefixPath")
        }
        return fixed
    }

    private fun fixIn(dir: File, targetPrefixPath: String): Int {
        val children = dir.listFiles() ?: return 0
        var fixed = 0
        for (child in children) {
            if (TextFileDetector.isSymlink(child)) {
                try {
                    val link = Os.readlink(child.absolutePath)
                    if (link != null) {
                        val matcher = EnvPaths.TERMUX_ANY_PREFIX.matcher(link)
                        if (matcher.lookingAt()) {
                            val rest = link.substring(matcher.end())
                            if (rest.isEmpty() || rest.startsWith("/")) {
                                val newTarget = targetPrefixPath + rest
                                val temp = File(child.parentFile, child.name + ".agy-link-" + UUID.randomUUID())
                                try {
                                    Os.symlink(newTarget, temp.absolutePath)
                                    Os.rename(temp.absolutePath, child.absolutePath)
                                    fixed++
                                } catch (e: Exception) {
                                    try {
                                        Os.remove(temp.absolutePath)
                                    } catch (_: ErrnoException) {
                                    }
                                    Log.w(TAG, "cannot repoint symlink $child: $e")
                                }
                            }
                        }
                    }
                } catch (e: ErrnoException) {
                    Log.w(TAG, "readlink failed $child: $e")
                }
            } else if (child.isDirectory) {
                fixed += fixIn(child, targetPrefixPath)
            }
        }
        return fixed
    }
}
