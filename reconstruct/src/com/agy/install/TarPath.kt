package com.agy.install

import android.text.TextUtils

/**
 * Normalizes tar member paths, rejecting absolute paths and `..` traversal.
 *
 * Ported 1:1 from decompiled `com/agy/install/TarPath.java`. `null` means
 * "reject", `""` means "the archive root itself".
 */
object TarPath {
    @JvmStatic
    fun normalize(raw: String?): String? {
        if (raw == null) return null
        var path = raw.replace('\\', '/')
        if (path.startsWith("/")) {
            return null
        }
        while (path.startsWith("./")) {
            path = path.substring(2)
        }
        if (path.isEmpty() || path == ".") {
            return ""
        }
        val parts = ArrayList<String>()
        for (part in path.split("/")) {
            if (part.isNotEmpty() && part != ".") {
                if (part == "..") {
                    return null
                }
                parts.add(part)
            }
        }
        return TextUtils.join("/", parts)
    }

    private class ArrayList<E> : kotlin.collections.ArrayList<E>()
}
