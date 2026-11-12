package com.agy.util

/**
 * POSIX-safe shell quoting used when generating bridge scripts.
 *
 * Ported 1:1 from decompiled `com/agy/util/ShellQuote.java`.
 */
object ShellQuote {
    @JvmStatic
    fun quote(value: String): String {
        if (value.matches(Regex("[A-Za-z0-9_./:=+-]+"))) {
            return value
        }
        return "'" + value.replace("'", "'\\''") + "'"
    }
}
