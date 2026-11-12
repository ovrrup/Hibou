package com.agy.runtime

import android.content.Context
import android.util.Log
import com.agy.net.DnsCollector
import com.agy.util.AtomicFileWriter
import java.io.File

/**
 * Writes `etc/resolv.conf` and `etc/hosts` into the app files dir so the
 * engine's glibc resolver sees the device DNS configuration.
 *
 * Ported 1:1 from decompiled `com/agy/runtime/NetworkConfigWriter.java`.
 */
object NetworkConfigWriter {
    private const val TAG = "NetworkConfigWriter"

    @JvmStatic
    fun write(context: Context) {
        val etc = File(context.filesDir, "etc")
        if (!etc.exists() && !etc.mkdirs()) {
            Log.w(TAG, "cannot create $etc")
            return
        }
        val sb = StringBuilder()
        sb.append("options timeout:1 attempts:1 rotate\n")
        for (server in DnsCollector.collectDnsServers(context)) {
            sb.append("nameserver ").append(server).append('\n')
        }
        AtomicFileWriter.writeText(File(etc, "resolv.conf"), sb.toString())
        AtomicFileWriter.writeText(File(etc, "hosts"), "127.0.0.1 localhost\n")
    }
}
