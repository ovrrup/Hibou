package com.agy.ui

import android.app.Activity

/**
 * Registration point for the app's foreground activity.
 *
 * Replaces the original `com.agy.ui.MainActivity.activeActivity` static field
 * (port delta documented in `OpenUrlWatcher`): the redesigned Kestrel UI is a
 * different activity class, so the foreground reference lives here and is
 * maintained by the active UI in `onResume`/`onPause`.
 */
object Foreground {
    @Volatile
    var current: Activity? = null
}
