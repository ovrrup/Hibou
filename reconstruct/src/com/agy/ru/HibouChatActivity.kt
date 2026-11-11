package com.agy.ru

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup

/**
 * Native launcher for the Kotlin UI.
 *
 * Subclasses Android Activity directly (not the precompiled MainActivity),
 * so this repository is fully self-contained while preserving the splash
 * handshake and drawer fallback during staged migration.
 *
 * Ported from native-src/com/agy/ru/HibouChatActivity.kt
 */
class HibouChatActivity : Activity() {

    private var chatView: HibouChatView? = null
    private var webContent: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val content = findViewById<ViewGroup>(android.R.id.content)
            if (content != null && content.childCount > 0) {
                webContent = content.getChildAt(0)
            }
            val view = HibouChatView(this)
            view.onOpenWeb = { showWebFallback() }
            chatView = view
            setContentView(view)
            HibouNativeSplash.hide(null, false)
        } catch (error: Throwable) {
            // Never brick the app: if the native shell cannot be installed the
            // WebView fallback stays available.
            chatView = null
        }
    }

    private fun showWebFallback() {
        val original = webContent
        if (original != null) {
            try {
                setContentView(original)
                chatView = null
                return
            } catch (_: Throwable) {
            }
        }
        try {
            startActivity(Intent().setClassName(packageName, "com.agy.ui.MainActivity"))
            finish()
        } catch (_: Throwable) {
        }
    }

    override fun onBackPressed() {
        val view = chatView
        if (view != null && view.handleBack()) return
        super.onBackPressed()
    }

    override fun onDestroy() {
        try {
            chatView?.onDestroy()
        } catch (_: Throwable) {
        }
        chatView = null
        super.onDestroy()
    }
}