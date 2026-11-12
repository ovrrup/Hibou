package com.agy.core

import android.content.Context
import android.util.Log
import com.agy.util.CoreUrl
import com.agy.util.ProcessUtils
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection

/**
 * Polls the core HTTPS endpoint until it responds with a certificate whose
 * SHA-256 matches the bundled `raw/agy_core_cert` resource (pinning), so the
 * web layer can trust `https://127.0.0.1:48255`.
 *
 * Ported from decompiled `com/agy/core/CoreReadyProbe.java`, whose body was
 * emitted as raw bytecode. Recovered semantics: 800ms timeouts, no redirects,
 * any HTTP response in [100, 599] continues to certificate verification; the
 * `lastProbeError` message is set as soon as HTTPS responded and again on any
 * failure; exceptions sleep 60ms and report `false`.
 */
object CoreReadyProbe {
    const val READY_TIMEOUT_MS = 60000L
    private const val RETRY_SLEEP_MS = 60L
    private const val TAG = "CoreReadyProbe"

    @Volatile
    private var expectedCertDigest: ByteArray? = null

    @Volatile
    var lastProbeError: String? = null

    @JvmStatic
    fun probeOnce(context: Context?, port: Int): Boolean {
        if (context == null) return false
        var connection: HttpsURLConnection? = null
        try {
            val url = CoreUrl.startUrl(port)
            connection = url.let { java.net.URL(it).openConnection() as HttpsURLConnection }
            connection.connectTimeout = 800
            connection.readTimeout = 800
            connection.useCaches = false
            connection.instanceFollowRedirects = false
            connection.requestMethod = "GET"
            connection.connect()
            val responseCode = connection.responseCode
            if (responseCode in 100..599) {
                lastProbeError = "HTTPS responded, certificate missing or fingerprint mismatch"
                val certs = connection.serverCertificates
                if (certs.isNotEmpty() && certs[0] is X509Certificate) {
                    val expected = expectedCertDigest(context)
                    val actual = sha256((certs[0] as X509Certificate).encoded)
                    return expected != null && MessageDigest.isEqual(expected, actual)
                }
                return false
            }
            return false
        } catch (e: Exception) {
            lastProbeError = e.toString()
            ProcessUtils.sleep(RETRY_SLEEP_MS)
            return false
        } catch (t: Throwable) {
            IoDisconnect(connection)
            throw t
        } finally {
            IoDisconnect(connection)
        }
    }

    private fun IoDisconnect(connection: HttpsURLConnection?) {
        try {
            connection?.disconnect()
        } catch (_: Exception) {
        }
    }

    @Throws(Exception::class)
    private fun expectedCertDigest(context: Context): ByteArray? {
        expectedCertDigest?.let { return it }
        synchronized(this) {
            expectedCertDigest?.let { return it }
            val identifier = context.resources.getIdentifier("agy_core_cert", "raw", context.packageName)
            if (identifier == 0) {
                Log.e(TAG, "bundled core certificate resource is missing")
                return null
            }
            val input = context.resources.openRawResource(identifier)
            return try {
                val certificate = CertificateFactory.getInstance("X.509").generateCertificate(input)
                if (certificate is X509Certificate) {
                    val digest = sha256(certificate.encoded)
                    expectedCertDigest = digest
                    input.close()
                    digest
                } else {
                    input.close()
                    null
                }
            } finally {
            }
        }
    }

    @Throws(Exception::class)
    private fun sha256(data: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(data)
    }
}
