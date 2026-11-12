package com.agy.oauth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.agy.util.SensitiveDataRedactor

/**
 * Handles the `antigravity://oauth-success` deep link the browser redirects to
 * after the engine stores the token.
 *
 * Ported 1:1 from decompiled `com/agy/oauth/DeepLinkHandler.java`. The scheme
 * is read from the `oauth_scheme` string resource with the `antigravity`
 * fallback (contract with the engine's redirect).
 */
object DeepLinkHandler {
    @JvmStatic
    fun getCallbackScheme(context: Context?): String {
        if (context != null) {
            return try {
                val identifier =
                    context.resources.getIdentifier("oauth_scheme", "string", context.packageName)
                if (identifier != 0) {
                    context.getString(identifier)
                } else {
                    "antigravity"
                }
            } catch (_: Exception) {
                "antigravity"
            }
        }
        return "antigravity"
    }

    @JvmStatic
    fun handleDeepLink(activity: Activity?, intent: Intent?, onHandled: Runnable?): Boolean {
        if (activity == null || intent == null || intent.data == null) {
            return false
        }
        val data = intent.data ?: return false
        val callbackScheme = getCallbackScheme(activity)
        val host = data.host
        val hostMatches =
            OAuthConstants.CALLBACK_HOST == host || OAuthConstants.CALLBACK_HOST_COMPAT == host
        if (!callbackScheme.equals(data.scheme, ignoreCase = true) || !hostMatches) {
            return false
        }
        Log.i(
            OAuthConstants.TAG,
            "auth-success deep link received: " + SensitiveDataRedactor.safeUrl(data.toString()) +
                " tokenPresent=" + OAuthTokenStore.hasToken(activity)
        )
        onHandled?.run()
        return true
    }
}
