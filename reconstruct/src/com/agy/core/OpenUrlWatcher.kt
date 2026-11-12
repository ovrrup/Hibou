package com.agy.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.agy.oauth.OAuthManager
import com.agy.ui.Foreground
import com.agy.util.SensitiveDataRedactor
import java.util.regex.Pattern

/**
 * Detects URLs the core wants opened (stream markers `AGY_OPEN_URL=`,
 * `ANTIGRAVITY_OPEN_URL:`, auth URLs) and a protected broadcast
 * (`com.agy.ACTION_OPEN_URL`), then opens them in the user's browser.
 *
 * Ported from decompiled `com/agy/core/OpenUrlWatcher.java`. Port delta: the
 * original dispatched to `com.agy.ui.MainActivity.activeActivity`; the rebuilt
 * app uses the `com.agy.ui.Foreground` hook so the redesigned native activity
 * can register itself.
 */
class OpenUrlWatcher(context: Context) {
    private val appContext: Context = context.applicationContext
    private val oauth = OAuthManager()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var lastDispatchedUrl = ""

    @Volatile
    private var lastDispatchedTime = 0L

    private val openUrlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            val url = intent?.getStringExtra("url")
            if (!url.isNullOrBlank()) {
                Log.i(TAG, "broadcast ACTION_OPEN_URL received: " + SensitiveDataRedactor.safeUrl(url))
                dispatchIfValid(url)
            }
        }
    }

    fun start() {
        try {
            val filter = IntentFilter("com.agy.ACTION_OPEN_URL")
            val permission = appContext.packageName + ".permission.INTERNAL_BROADCAST"
            if (Build.VERSION.SDK_INT >= 33) {
                appContext.registerReceiver(openUrlReceiver, filter, permission, null, 4)
            } else {
                appContext.registerReceiver(openUrlReceiver, filter, permission, null)
            }
            Log.i(TAG, "registered protected ACTION_OPEN_URL receiver")
        } catch (t: Throwable) {
            Log.w(TAG, "failed to register ACTION_OPEN_URL receiver: " + t.message)
        }
    }

    fun stop() {
        try {
            appContext.unregisterReceiver(openUrlReceiver)
        } catch (_: Throwable) {
        }
    }

    fun handleStreamLine(line: String?) {
        if (line == null) return
        for (marker in STREAM_MARKERS) {
            val index = line.indexOf(marker)
            if (index >= 0) {
                dispatchIfValid(line.substring(index + marker.length).trim())
                return
            }
        }
        if (line.contains("accounts.google.com") || (line.contains("/auth/callback") && line.contains("http"))) {
            val matcher = URL_PATTERN.matcher(line)
            while (matcher.find()) {
                val group = matcher.group()
                if (group.startsWith("http://") || group.startsWith("https://")) {
                    if (!group.contains("googleapis.com/oauth2/v2/userinfo") &&
                        !group.contains("googleapis.com/auth") &&
                        !group.contains("daily-cloudcode-pa.googleapis.com") &&
                        (group.contains("accounts.google.com") ||
                            group.contains("/auth/callback") ||
                            group.contains("antigravity.google/auth"))
                    ) {
                        Log.i(TAG, "stream regex detected auth URL: " + SensitiveDataRedactor.safeUrl(group))
                        dispatchIfValid(group)
                    }
                }
            }
        }
    }

    fun dispatchIfValid(raw: String?) {
        if (raw == null) return
        val url = raw.trim()
        if (url.isEmpty()) return
        if ((!url.startsWith("http://") && !url.startsWith("https://")) ||
            url.contains("googleapis.com/oauth2/v2/userinfo") ||
            url.contains("googleapis.com/auth") ||
            url.contains("daily-cloudcode-pa.googleapis.com")
        ) {
            return
        }
        val now = System.currentTimeMillis()
        if (url == lastDispatchedUrl && now - lastDispatchedTime < 1500) {
            return
        }
        lastDispatchedUrl = url
        lastDispatchedTime = now
        Log.i(TAG, "dispatching external URL: " + SensitiveDataRedactor.safeUrl(url))
        openUrl(url)
    }

    private fun openUrl(url: String) {
        mainHandler.post {
            val context = Foreground.current ?: appContext
            if (!oauth.openInBrowser(context, url)) {
                Log.w(TAG, "no browser accepted the url: " + SensitiveDataRedactor.safeUrl(url))
            }
        }
    }

    private companion object {
        const val TAG = "OpenUrlWatcher"
        val URL_PATTERN: Pattern =
            Pattern.compile("https?://[\\w\\-\\.\\~:\\?\\#\\[\\]\\@\\!\\$\\&\\'\\(\\)\\*\\+,;=%/]+")
        val STREAM_MARKERS = arrayOf("AGY_OPEN_URL=", "ANTIGRAVITY_OPEN_URL:", "ANTIGRAVITY_OPEN_URL=")
    }
}
