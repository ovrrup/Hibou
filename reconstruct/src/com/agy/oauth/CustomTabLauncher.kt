package com.agy.oauth

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import com.agy.ui.Foreground
import com.agy.util.SensitiveDataRedactor

/**
 * Resolves the device's default browser and opens OAuth URLs in it, with a
 * system chooser fallback.
 *
 * Ported from decompiled `com/agy/oauth/CustomTabLauncher.java`. Port delta: the
 * original preferred `com.agy.ui.MainActivity.activeActivity` as the launching
 * context; the rebuilt app uses the `com.agy.ui.Foreground` hook.
 */
class CustomTabLauncher {
    fun resolveDefaultBrowserPackage(context: Context?): String? {
        if (context == null) return null
        val packageManager = context.packageManager ?: return null
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        return try {
            val resolveInfo = packageManager.resolveActivity(intent, 65536)
            if (resolveInfo != null && resolveInfo.activityInfo != null) {
                val candidate = resolveInfo.activityInfo.packageName
                if (isRealBrowserPackage(candidate)) {
                    return candidate
                }
            }
            null
        } catch (t: Throwable) {
            Log.w("OAuthManager", "resolveActivity failed: " + t.message)
            null
        }
    }

    private fun isRealBrowserPackage(candidate: String?): Boolean {
        if (candidate.isNullOrEmpty() ||
            candidate == "android" ||
            candidate == "com.android.internal.app.ResolverActivity" ||
            candidate == "com.android.internal.app.ChooserActivity" ||
            candidate == "com.google.android.gms" ||
            candidate == "com.google.android.googlequicksearchbox"
        ) {
            return false
        }
        return true
    }

    fun openInBrowser(context: Context?, raw: String?): Boolean {
        if (context == null || raw == null || raw.trim().isEmpty()) {
            return false
        }
        return try {
            val uri = Uri.parse(raw.trim())
            val scheme = uri.scheme
            if ((!scheme.equals("http", ignoreCase = true) && !scheme.equals("https", ignoreCase = true)) ||
                uri.host.isNullOrEmpty() ||
                uri.userInfo != null
            ) {
                Log.w(
                    "OAuthManager",
                    "refusing non-web or credential-bearing URL: " + SensitiveDataRedactor.safeUrl(uri.toString())
                )
                return false
            }
            val activity = Foreground.current
            doOpen(activity ?: context, uri)
        } catch (e: Exception) {
            Log.e("OAuthManager", "bad url: " + SensitiveDataRedactor.safeUrl(raw), e)
            false
        }
    }

    private fun doOpen(context: Context, uri: Uri): Boolean {
        val browser = resolveDefaultBrowserPackage(context)
        if (browser != null) {
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.addCategory(Intent.CATEGORY_BROWSABLE)
            intent.addFlags(268435456)
            intent.setPackage(browser)
            try {
                context.startActivity(intent)
                Log.i(
                    "OAuthManager",
                    "opened external browser pkg=$browser url=" + SensitiveDataRedactor.safeUrl(uri.toString())
                )
                return true
            } catch (e: Exception) {
                Log.w(
                    "OAuthManager",
                    "failed to launch default browser pkg=$browser: " + e.message
                )
            }
        }
        val fallback = Intent(Intent.ACTION_VIEW, uri)
        fallback.addCategory(Intent.CATEGORY_BROWSABLE)
        fallback.addFlags(268435456)
        return try {
            val chooser = Intent.createChooser(fallback, "Open in browser")
            chooser.addFlags(268435456)
            context.startActivity(chooser)
            Log.i("OAuthManager", "opened chooser for url=" + SensitiveDataRedactor.safeUrl(uri.toString()))
            true
        } catch (e: Exception) {
            Log.e(
                "OAuthManager",
                "browser chooser launch failed for url: " + SensitiveDataRedactor.safeUrl(uri.toString()),
                e
            )
            false
        }
    }
}
