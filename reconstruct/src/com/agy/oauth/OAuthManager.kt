package com.agy.oauth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import com.agy.util.SensitiveDataRedactor
import java.io.File

/**
 * Facade for the OAuth flow: URL matching, external-browser opening, token
 * presence checks and deep-link handling.
 *
 * Ported 1:1 from decompiled `com/agy/oauth/OAuthManager.java`.
 */
class OAuthManager {
    private val matcher = AuthUrlMatcher()
    private val launcher = CustomTabLauncher()

    fun tokenFile(context: Context): File? = OAuthTokenStore.tokenFile(context)

    fun hasToken(context: Context): Boolean = OAuthTokenStore.hasToken(context)

    fun isAuthUrl(url: String?): Boolean = matcher.isAuthUrl(url)

    fun intercept(context: Context, url: String?): Boolean {
        val auth = matcher.isAuthUrl(url)
        Log.i(OAuthConstants.TAG, "intercept isAuthUrl=$auth url=" + SensitiveDataRedactor.safeUrl(url))
        if (!auth) {
            return false
        }
        Log.i(OAuthConstants.TAG, "isAuthUrl matched -> external browser: " + SensitiveDataRedactor.safeUrl(url))
        return launcher.openInBrowser(context, url)
    }

    fun openInBrowser(context: Context, url: String?): Boolean {
        return launcher.openInBrowser(context, url)
    }

    fun getCallbackScheme(context: Context): String {
        return DeepLinkHandler.getCallbackScheme(context)
    }

    fun handleDeepLink(activity: Activity, intent: Intent?, onHandled: Runnable?): Boolean {
        return DeepLinkHandler.handleDeepLink(activity, intent, onHandled)
    }
}
