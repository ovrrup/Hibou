package com.agy.core

/**
 * Shared runtime state of the core engine process, read by the UI and the
 * system gateway.
 *
 * Ported 1:1 from decompiled `com/agy/core/CoreRuntimeState.java`.
 */
object CoreRuntimeState {
    @Volatile
    var serverUrl: String? = null

    @Volatile
    var httpsPort: Int = 48255

    @Volatile
    var csrfToken: String? = null

    @Volatile
    var lastError: String? = null

    @Volatile
    var restarting: Boolean = false

    @Volatile
    var readyGeneration: Int = 0
}
