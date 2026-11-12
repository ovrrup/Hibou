package com.agy.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.util.Log

/**
 * Registers a default-network callback and fires [Listener.onNetworkChanged]
 * so the DNS config can be refreshed while the core stays up.
 *
 * Ported 1:1 from decompiled `com/agy/net/NetworkMonitor.java`. `getSystemService`
 * returns `null` on test environments; the original null-checks and
 * swallowed-error paths are preserved.
 */
class NetworkMonitor(context: Context, private val listener: Listener) {
    interface Listener {
        fun onNetworkChanged()
    }

    private val appContext: Context = context.applicationContext
    private var callback: ConnectivityManager.NetworkCallback? = null
    private var connectivityManager: ConnectivityManager? = null
    private var registered = false

    private companion object {
        const val TAG = "NetworkMonitor"
    }

    @Synchronized
    fun register() {
        if (registered) return
        var manager: ConnectivityManager? = null
        try {
            try {
                manager = appContext.getSystemService("connectivity") as? ConnectivityManager
            } catch (e: SecurityException) {
                Log.w(TAG, "ACCESS_NETWORK_STATE missing; DNS refresh disabled", e)
            }
        } catch (e: Exception) {
            Log.w(TAG, "cannot register network callback; DNS refresh disabled", e)
        }
        if (manager == null) {
            Log.w(TAG, "ConnectivityManager unavailable; DNS refresh disabled")
            return
        }
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                notifyChanged()
            }

            override fun onLost(network: Network) {
                notifyChanged()
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                notifyChanged()
            }
        }
        manager.registerDefaultNetworkCallback(networkCallback)
        connectivityManager = manager
        callback = networkCallback
        registered = true
        Log.i(TAG, "registered default network callback")
    }

    @Synchronized
    fun unregister() {
        val manager = connectivityManager
        val networkCallback = callback
        if (!registered || manager == null || networkCallback == null) {
            registered = false
            callback = null
            return
        }
        try {
            try {
                manager.unregisterNetworkCallback(networkCallback)
                registered = false
                connectivityManager = null
            } catch (e: Exception) {
                Log.w(TAG, "cannot unregister network callback", e)
                registered = false
                connectivityManager = null
            }
            callback = null
        } catch (t: Throwable) {
            registered = false
            connectivityManager = null
            callback = null
            throw t
        }
    }

    private fun notifyChanged() {
        try {
            listener.onNetworkChanged()
        } catch (e: Exception) {
            Log.w(TAG, "network change listener failed", e)
        }
    }
}
