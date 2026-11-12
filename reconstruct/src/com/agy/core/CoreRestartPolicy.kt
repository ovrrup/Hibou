package com.agy.core

/**
 * Restart back-off policy for the core process: exponential 1s→30s, give up
 * after 3 rapid crashes, and treat a run that stayed ready for
 * [SUSTAINED_READY_MS] as healthy (resetting the counter).
 *
 * Ported 1:1 from decompiled `com/agy/core/CoreRestartPolicy.java`.
 */
object CoreRestartPolicy {
    const val MAX_RESTARTS = 3
    const val RESTART_BASE_MS = 1000L
    const val RESTART_MAX_MS = 30000L
    const val SUSTAINED_READY_MS = 60000L

    @JvmStatic
    fun delayFor(attempt: Int): Long {
        return minOf(1000L shl minOf(attempt - 1, 5), RESTART_MAX_MS)
    }
}
