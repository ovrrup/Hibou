package com.agy.gateway

import android.content.Context
import android.util.Log
import com.agy.log.CommandHistory
import com.agy.log.CrashRecorder
import com.agy.log.LogRingBuffer
import com.agy.util.AtomicFileWriter
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.json.JSONObject

/**
 * Plain-HTTP sidecar API on `http://127.0.0.1:48258` (fallback +2 if busy):
 * status/logs/commands/crash GETs and `{"command","payload"}` POST dispatch.
 * Writes its actual port to `logs/gateway.port` for the web layer.
 *
 * Ported 1:1 from decompiled `com/agy/gateway/SystemGatewayServer.java`,
 * including the reconstructed `readHeaderLine` (CR tolerated mid-line, LF
 * terminates, 8192-byte cap) whose body the decompiler could not recover.
 */
class SystemGatewayServer(
    context: Context,
    logRingBuffer: LogRingBuffer?,
    port: Int,
) {
    constructor(context: Context, logRingBuffer: LogRingBuffer?) : this(context, logRingBuffer, DEFAULT_PORT)

    private var acceptThread: Thread? = null
    private val context: Context = context
    private val dispatcher = GatewayDispatcher(context, logRingBuffer)
    private val executor: ExecutorService = Executors.newFixedThreadPool(4)
    private val logBuffer: LogRingBuffer? = logRingBuffer
    private var port: Int = port

    @Volatile
    private var running = false
    private var serverSocket: ServerSocket? = null

    @Synchronized
    fun start() {
        if (running) return
        running = true
        val thread = Thread({ acceptLoop() }, "AgyGatewayServer")
        acceptThread = thread
        thread.isDaemon = true
        thread.start()
    }

    private fun acceptLoop() {
        try {
            try {
                val socket = ServerSocket()
                serverSocket = socket
                socket.reuseAddress = true
                socket.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 50)
            } catch (e: IOException) {
                Log.w(TAG, "Port $port in use, trying fallback ${port + 2}")
                port += 2
                val socket = ServerSocket()
                serverSocket = socket
                socket.reuseAddress = true
                socket.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 50)
            }
            Log.i(TAG, "System Gateway listening on http://127.0.0.1:$port/gateway")
            try {
                val portFile = File(context.filesDir, "logs/gateway.port")
                if (portFile.parentFile != null) {
                    portFile.parentFile.mkdirs()
                }
                AtomicFileWriter.writeText(portFile, port.toString() + "\n")
            } catch (_: Throwable) {
            }
            while (running && serverSocket?.isClosed == false) {
                try {
                    val client = serverSocket?.accept() ?: break
                    client.soTimeout = 3000
                    executor.submit { handleClient(client) }
                } catch (_: Exception) {
                    if (!running) return
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gateway server bind failed on port $port: " + e.message)
        }
    }

    @Synchronized
    fun stop() {
        running = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        acceptThread?.interrupt()
        dispatcher.releaseResources()
        executor.shutdownNow()
        Log.i(TAG, "System Gateway stopped.")
    }

    private fun handleClient(socket: Socket) {
        try {
            val input: InputStream = socket.getInputStream()
            val output: OutputStream = socket.getOutputStream()
            val requestLine = readHeaderLine(input) ?: run {
                output.close(); input.close(); socket.close(); return
            }
            val parts = requestLine.split(" ".toRegex()).toTypedArray()
            if (parts.size < 2) {
                output.close(); input.close(); socket.close(); return
            }
            val method = parts[0].uppercase()
            val path = parts[1]
            var contentLength = 0
            while (true) {
                val header = readHeaderLine(input)
                if (header == null || header.isEmpty()) break
                if (header.lowercase().startsWith("content-length:")) {
                    contentLength = header.substring(15).trim().toInt()
                }
            }
            if (contentLength < 0) throw IOException("Invalid Content-Length: $contentLength")
            if (contentLength > MAX_PAYLOAD_BYTES) throw IOException("Payload too large: $contentLength")

            var response = JSONObject()
            when {
                path.startsWith("/gateway/status") -> {
                    response.put("ok", true)
                    response.put("status", "online")
                    response.put("port", port)
                }
                path.startsWith("/gateway/logs") -> {
                    response.put("ok", true)
                    response.put("logs", LogRingBuffer.toJsonArray(logBuffer?.getRecent(50) ?: emptyList()))
                }
                path.startsWith("/gateway/commands") -> {
                    response.put("ok", true)
                    response.put(
                        "commands",
                        CommandHistory.toJsonArray(dispatcher.commandHistory.getRecent(20))
                    )
                }
                path.startsWith("/gateway/crash") -> {
                    val latest = CrashRecorder.getLatestCrash()
                    response.put("ok", true)
                    response.put("crash", latest ?: JSONObject.NULL)
                }
                method == "POST" && path.startsWith("/gateway") -> {
                    var body = ""
                    if (contentLength > 0) {
                        val buffer = ByteArray(contentLength)
                        var read = 0
                        while (read < contentLength) {
                            val n = input.read(buffer, read, contentLength - read)
                            if (n == -1) break
                            read += n
                        }
                        if (read < contentLength) {
                            throw IOException("Unexpected EOF while reading body: expected $contentLength, got $read")
                        }
                        body = String(buffer, StandardCharsets.UTF_8)
                    }
                    val payload = if (body.isNotEmpty()) JSONObject(body) else JSONObject()
                    response = dispatcher.dispatch(payload.optString("command", ""), payload.optJSONObject("payload"))
                }
                else -> {
                    response.put("ok", false)
                    response.put("error", "not found")
                }
            }

            val bytes = response.toString().toByteArray(StandardCharsets.UTF_8)
            output.write(
                ("HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: " +
                    bytes.size + "\r\nConnection: close\r\n\r\n").toByteArray(StandardCharsets.UTF_8)
            )
            output.write(bytes)
            output.flush()
            output.close()
            input.close()
            socket.close()
        } catch (e: Exception) {
            Log.d(TAG, "client error: " + e.message)
        }
    }

    private fun readHeaderLine(input: InputStream): String? {
        val buffer = java.io.ByteArrayOutputStream()
        while (true) {
            val c = input.read()
            if (c == -1) {
                if (buffer.size() == 0) return null
                return buffer.toString("UTF-8")
            }
            if (c == '\n'.code) {
                return buffer.toString("UTF-8")
            }
            if (c != '\r'.code) {
                if (buffer.size() >= MAX_HEADER_LINE_BYTES) {
                    throw IOException("HTTP header line exceeded limit of $MAX_HEADER_LINE_BYTES bytes")
                }
                buffer.write(c)
            }
        }
    }

    companion object {
        const val DEFAULT_PORT = 48158
        private const val MAX_HEADER_LINE_BYTES = 8192
        private const val MAX_PAYLOAD_BYTES = 4194304
        private const val TAG = "SystemGateway"
    }
}
