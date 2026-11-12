package com.agy.util

import java.io.File
import java.io.IOException

/**
 * Canonical-path containment checks used before destructive operations.
 *
 * Ported 1:1 from decompiled `com/agy/util/PathSafety.java`.
 */
object PathSafety {
    @JvmStatic
    @Throws(IOException::class)
    fun isInside(parent: File, candidate: File): Boolean {
        val parentPath = parent.canonicalPath
        val candidatePath = candidate.canonicalPath
        return candidatePath == parentPath || candidatePath.startsWith(parentPath + File.separator)
    }
}
