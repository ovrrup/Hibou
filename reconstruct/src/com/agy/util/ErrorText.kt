package com.agy.util

/**
 * Short human-readable error description used in user-facing state.
 *
 * Ported 1:1 from decompiled `com/agy/util/ErrorText.java`.
 */
object ErrorText {
    @JvmStatic
    fun describe(e: Exception): String {
        val message = e.message
        return if (message.isNullOrEmpty()) e.javaClass.simpleName else message
    }
}
