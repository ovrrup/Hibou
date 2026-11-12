package com.agy.oauth

/**
 * Constants of the OAuth flow. The callback scheme/hosts and the token file
 * name are contracts with the engine's auth flow and stay unchanged.
 *
 * Ported 1:1 from decompiled `com/agy/oauth/OAuthConstants.java`.
 */
object OAuthConstants {
    const val CALLBACK_HOST = "oauth-success"
    const val CALLBACK_HOST_COMPAT = "auth-success"
    const val CALLBACK_SCHEME = "antigravity"
    const val CHROME_PACKAGE = "com.android.chrome"
    const val EXTRA_APP_ID = "com.android.browser.application_id"
    const val EXTRA_SESSION = "android.support.customtabs.extra.SESSION"
    const val EXTRA_TOOLBAR_COLOR = "android.support.customtabs.extra.TOOLBAR_COLOR"
    const val TAG = "OAuthManager"
    const val TOKEN_DIR = ".gemini"
    const val TOKEN_NAME = "jetski-standalone-oauth-token"
    const val TOOLBAR_COLOR_DARK = -15724528
}
