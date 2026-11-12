package com.agy.runtime

import android.content.Context
import com.agy.net.NetworkMonitor

/**
 * Owns the [NetworkMonitor] lifecycle for a [RuntimeManager].
 *
 * Ported 1:1 from decompiled `com/agy/runtime/NetworkMonitorHolder.java`.
 */
class NetworkMonitorHolder(context: Context) {
    private val appContext: Context = context.applicationContext
    private var monitor: NetworkMonitor? = null

    @Synchronized
    fun start() {
        if (monitor == null) {
            monitor = NetworkMonitor(
                appContext,
                object : NetworkMonitor.Listener {
                    override fun onNetworkChanged() {
                        NetworkConfigWriter.write(appContext)
                    }
                },
            )
        }
        monitor?.register()
    }

    @Synchronized
    fun unregister() {
        monitor?.unregister()
    }
}
