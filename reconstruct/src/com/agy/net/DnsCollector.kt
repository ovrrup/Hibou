package com.agy.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.util.Log
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Enumerates the device's DNS servers (IPv4 first) for `resolv.conf`; falls
 * back to fixed public resolvers when discovery is unavailable.
 *
 * Ported 1:1 from decompiled `com/agy/net/DnsCollector.java`.
 */
object DnsCollector {
    private const val TAG = "DnsCollector"

    @JvmStatic
    fun collectDnsServers(context: Context): List<String> {
        val v4 = ArrayList<String>()
        val v6 = ArrayList<String>()
        try {
            val connectivityManager =
                context.getSystemService("connectivity") as? ConnectivityManager
            if (connectivityManager != null) {
                val activeNetwork = connectivityManager.activeNetwork
                if (activeNetwork != null) {
                    addDns(v4, v6, connectivityManager.getLinkProperties(activeNetwork))
                }
                for (network in connectivityManager.allNetworks) {
                    addDns(v4, v6, connectivityManager.getLinkProperties(network))
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "ACCESS_NETWORK_STATE missing; using fallback DNS", e)
        } catch (e: Exception) {
            Log.w(TAG, "DNS discovery failed", e)
        }
        val result = ArrayList<String>(v4.size + v6.size)
        result.addAll(v4)
        result.addAll(v6)
        if (result.isEmpty()) {
            result.add("1.1.1.1")
            result.add("77.88.8.8")
            result.add("8.8.8.8")
        }
        return result
    }

    private fun addDns(v4: MutableList<String>, v6: MutableList<String>, properties: LinkProperties?) {
        if (properties == null) return
        for (address in properties.dnsServers) {
            if (address == null) continue
            val hostAddress = address.hostAddress
            if (hostAddress != null && hostAddress.isNotEmpty() && hostAddress.indexOf('%') < 0) {
                if (address is Inet4Address) {
                    if (!v4.contains(hostAddress)) {
                        v4.add(hostAddress)
                    }
                } else if (address is Inet6Address && !v6.contains(hostAddress)) {
                    v6.add(hostAddress)
                }
            }
        }
    }
}
