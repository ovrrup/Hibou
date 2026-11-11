package com.agy.ru

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class HibouRpcException(val code: String, message: String) : IOException(message)

/**
 * Minimal ConnectRPC client for the loopback core.
 *
 * The shipped web bundle talks to the same process with
 * `useBinaryFormat:false`, i.e. the Connect JSON codec.  This client mirrors
 * that choice: unary calls are `application/json`, streaming calls are
 * `application/connect+json` with 5-byte envelopes.  The service path and the
 * CSRF header are pinned by tests against the bundle and the descriptor index.
 */
class HibouConnect(
    private val baseUrl: String,
    private val csrfToken: String
) {
    companion object {
        const val SERVICE = "exa.language_server_pb.LanguageServerService"
        const val CSRF_HEADER = "x-codeium-csrf-token"
        const val PROTOCOL_HEADER = "Connect-Protocol-Version"
        const val PROTOCOL_VERSION = "1"
        const val UNARY_TYPE = "application/json"
        const val STREAM_TYPE = "application/connect+json"

        const val FLAG_COMPRESSED = 0x01
        const val FLAG_END_STREAM = 0x02
        const val ENVELOPE_HEADER_BYTES = 5

        fun path(method: String): String = "/$SERVICE/$method"

        /** Accepts the self-signed loopback certificate the core generates. */
        private fun loopbackFactory(): SSLSocketFactory {
            val trust = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            val context = SSLContext.getInstance("TLS")
            context.init(null, arrayOf<TrustManager>(trust), null)
            return context.socketFactory
        }

        private fun isLoopback(url: URL): Boolean {
            val host = url.host ?: return false
            return host == "127.0.0.1" || host == "localhost" || host == "::1"
        }
    }

    @Volatile
    private var active: HttpURLConnection? = null

    @Volatile
    private var cancelled = false

    fun cancel() {
        cancelled = true
        try {
            active?.disconnect()
        } catch (_: Throwable) {
        }
    }

    private fun open(method: String, streaming: Boolean): HttpURLConnection {
        val url = URL(baseUrl + path(method))
        if (!isLoopback(url)) {
            throw IOException("refusing to send core credentials to " + url.host)
        }
        val connection = url.openConnection() as HttpURLConnection
        if (connection is HttpsURLConnection) {
            connection.sslSocketFactory = loopbackFactory()
            connection.hostnameVerifier = HostnameVerifier { _: String?, _: SSLSession? -> true }
        }
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 5000
        connection.readTimeout = if (streaming) 0 else 30000
        connection.setRequestProperty("Content-Type", if (streaming) STREAM_TYPE else UNARY_TYPE)
        connection.setRequestProperty("Accept", if (streaming) STREAM_TYPE else UNARY_TYPE)
        connection.setRequestProperty(PROTOCOL_HEADER, PROTOCOL_VERSION)
        connection.setRequestProperty("Accept-Encoding", "identity")
        if (csrfToken.isNotEmpty()) {
            connection.setRequestProperty(CSRF_HEADER, csrfToken)
        }
        return connection
    }

    private fun write(connection: HttpURLConnection, payload: ByteArray) {
        connection.setFixedLengthStreamingMode(payload.size)
        connection.outputStream.use { it.write(payload) }
    }

    private fun readError(connection: HttpURLConnection): HibouRpcException {
        val body = try {
            connection.errorStream?.use { it.readAllBytesCompat().toString(Charsets.UTF_8) } ?: ""
        } catch (_: Throwable) {
            ""
        }
        if (body.isBlank()) {
            return HibouRpcException("http_${connection.responseCode}",
                "core returned HTTP " + connection.responseCode)
        }
        return try {
            val json = JSONObject(body)
            val code = json.optString("code", "http_${connection.responseCode}")
            val message = json.optString("message", body.take(400))
            HibouRpcException(code, message)
        } catch (_: Throwable) {
            HibouRpcException("http_${connection.responseCode}", body.take(400))
        }
    }

    /** Unary call. Returns the decoded response object (empty object when the response is empty). */
    fun unary(method: String, request: JSONObject): JSONObject {
        if (cancelled) throw IOException("cancelled")
        val connection = open(method, streaming = false)
        active = connection
        try {
            write(connection, request.toString().toByteArray(Charsets.UTF_8))
            val status = connection.responseCode
            if (status !in 200..299) throw readError(connection)
            val body = connection.inputStream.use { it.readAllBytesCompat().toString(Charsets.UTF_8) }
            if (body.isBlank()) return JSONObject()
            return JSONObject(body)
        } finally {
            active = null
            connection.disconnect()
        }
    }

    /**
     * Server-streaming call.  Blocks until the stream ends, the core sends an
     * end-stream envelope, or [cancel] is called.
     */
    fun stream(method: String, request: JSONObject, onMessage: (JSONObject) -> Unit) {
        if (cancelled) throw IOException("cancelled")
        val connection = open(method, streaming = true)
        active = connection
        try {
            write(connection, request.toString().toByteArray(Charsets.UTF_8))
            val status = connection.responseCode
            if (status !in 200..299) throw readError(connection)
            val input = connection.inputStream
            try {
                while (!cancelled) {
                    val header = readFully(input, ENVELOPE_HEADER_BYTES) ?: break
                    val flags = header[0].toInt() and 0xff
                    if (flags and FLAG_COMPRESSED != 0) {
                        throw IOException("compressed connect envelopes are not supported")
                    }
                    val length = ((header[1].toInt() and 0xff) shl 24) or
                        ((header[2].toInt() and 0xff) shl 16) or
                        ((header[3].toInt() and 0xff) shl 8) or
                        (header[4].toInt() and 0xff)
                    if (length < 0 || length > 64 * 1024 * 1024) {
                        throw IOException("invalid connect envelope length " + length)
                    }
                    val payload = if (length == 0) ByteArray(0) else readFully(input, length)
                        ?: throw IOException("truncated connect envelope")
                    val text = payload.toString(Charsets.UTF_8)
                    if (flags and FLAG_END_STREAM != 0) {
                        if (text.isNotBlank()) {
                            val trailer = JSONObject(text)
                            val error = trailer.optJSONObject("error")
                            if (error != null) {
                                throw HibouRpcException(
                                    error.optString("code", "unknown"),
                                    error.optString("message", "stream failed")
                                )
                            }
                        }
                        return
                    }
                    if (text.isNotBlank()) onMessage(JSONObject(text))
                }
            } finally {
                try {
                    input.close()
                } catch (_: Throwable) {
                }
            }
        } finally {
            active = null
            connection.disconnect()
        }
    }

    private fun readFully(input: InputStream, count: Int): ByteArray? {
        val buffer = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val read = input.read(buffer, offset, count - offset)
            if (read < 0) {
                if (offset == 0) return null
                throw IOException("truncated connect envelope")
            }
            offset += read
        }
        return buffer
    }
}

/** Reads the whole body of a small response; kept for diagnostics. */
internal fun InputStream.readAllBytesCompat(): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}
