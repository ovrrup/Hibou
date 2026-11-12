package com.agy.install

import android.util.Log
import com.agy.util.PathSafety
import java.io.File
import java.io.IOException

/**
 * Deletes known Termux bootstrap profile hooks from the extracted prefix.
 *
 * Ported 1:1 from decompiled `com/agy/install/TermuxHooksRemover.java`.
 */
object TermuxHooksRemover {
    private const val TAG = "TermuxHooksRemover"
    private val HOOKS = arrayOf(
        "01-termux-bootstrap-second-stage-fallback.sh",
        "init-termux-properties.sh",
    )

    @JvmStatic
    fun remove(prefix: File) {
        val profileDir = File(prefix, "etc/profile.d")
        for (hook in HOOKS) {
            val target = File(profileDir, hook)
            try {
                if (!PathSafety.isInside(prefix, target)) {
                    Log.w(TAG, "refusing to delete outside prefix: $target")
                } else if (target.exists() && !target.delete()) {
                    Log.w(TAG, "cannot delete profile hook: $target")
                }
            } catch (e: IOException) {
                Log.w(TAG, "cannot resolve $target: $e")
            }
        }
    }
}
