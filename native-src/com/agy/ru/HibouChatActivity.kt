package com.agy.ru

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup

/**
 * Native launcher for the Kotlin UI.
 *
 * It subclasses the precompiled [com.agy.ui.MainActivity] so the existing core
 * supervisor, splash handshake and file-provider plumbing keep working while
 * this repository has no MainActivity source.  The WebView is detached but kept
 * alive, which preserves the `onUiReady` handshake and gives the drawer a real
 * fallback during the staged migration.
 */
class HibouChatActivity : com.agy.ui.MainActivity() {

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
            dismissSplash()
        } catch (error: Throwable) {
            // Never brick the app: if the native shell cannot be installed the
            // WebView created by super.onCreate stays on screen.
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
