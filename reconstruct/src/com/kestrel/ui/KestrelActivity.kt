package com.kestrel.ui

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup

/**
 * Kestrel launcher — the redesigned app's single activity.
 *
 * Responsibilities are deliberately thin: register as the foreground activity
 * (engine callbacks resolve through [com.agy.ui.Foreground]), ask the core
 * service to run, and hand the screen to [KestrelConsoleView] which owns the
 * boot handshake, onboarding and the console itself.
 *
 * The manifest selects this class as the LAUNCHER (patched launcher slot);
 * the window background is set programmatically so the patched
 * `window_background` resource and this surface always agree.
 */
class KestrelActivity : Activity() {

    private var console: KestrelConsoleView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = KestrelTheme.BG
        window.navigationBarColor = KestrelTheme.BG

        val view = KestrelConsoleView(this)
        console = view
        setContentView(view)

        // Engine: keep the foreground service alive while the user is here.
        com.agy.core.CoreServiceLauncher.start(this)
        view.beginBoot()
    }

    override fun onResume() {
        super.onResume()
        com.agy.ui.Foreground.current = this
        console?.refreshPermissions()
        console?.refreshStatus()
    }

    override fun onPause() {
        com.agy.ui.Foreground.current = null
        super.onPause()
    }

    override fun onBackPressed() {
        val view = console
        if (view != null && view.handleBack()) return
        super.onBackPressed()
    }

    override fun onDestroy() {
        console?.onDestroy()
        console = null
        super.onDestroy()
    }
}
