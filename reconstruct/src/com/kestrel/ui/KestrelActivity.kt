// SPDX-FileCopyrightText: 2026 ovrrup <ofeducationc@gmail.com>
// SPDX-License-Identifier: Apache-2.0

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
        com.kestrel.diag.FlightLog.install(applicationContext)
        com.kestrel.diag.FlightLog.w("ACT", "onCreate begin")
        window.statusBarColor = KestrelTheme.BG
        window.navigationBarColor = KestrelTheme.BG

        val view = KestrelConsoleView(this)
        com.kestrel.diag.FlightLog.w("ACT", "console constructed")
        console = view
        setContentView(view)
        com.kestrel.diag.FlightLog.w("ACT", "content set")

        // Engine: keep the foreground service alive while the user is here.
        com.agy.core.CoreServiceLauncher.start(this)
        com.kestrel.diag.FlightLog.w("ACT", "core service requested")
        view.beginBoot()
        com.kestrel.diag.FlightLog.w("ACT", "beginBoot called")
    }

    override fun onResume() {
        super.onResume()
        com.kestrel.diag.FlightLog.w("ACT", "onResume")
        com.agy.ui.Foreground.current = this
        console?.refreshPermissions()
        console?.refreshStatus()
    }

    override fun onPause() {
        com.kestrel.diag.FlightLog.w("ACT", "onPause")
        com.agy.ui.Foreground.current = null
        super.onPause()
    }

    override fun onBackPressed() {
        val view = console
        if (view != null && view.handleBack()) return
        super.onBackPressed()
    }

    override fun onDestroy() {
        com.kestrel.diag.FlightLog.w("ACT", "onDestroy")
        console?.onDestroy()
        console = null
        super.onDestroy()
    }
}
