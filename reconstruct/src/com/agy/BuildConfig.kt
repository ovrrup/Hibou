package com.agy

/**
 * App identity constants for the rebuilt app (Kestrel — full native redesign of
 * Hibou). This class replaces the AGP-generated `com.agy.BuildConfig`; the
 * fields that ported code reads are provided here. The app label is a
 * user-visible identity choice, unlike the engine contracts which stay fixed.
 */
object BuildConfig {
    const val APP_NAME = "Kestrel"
    const val VERSION_NAME = "1.0.0-beta.1"
    const val VERSION_CODE = 1
    const val DEBUG = false
}
