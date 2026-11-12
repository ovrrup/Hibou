package com.agy.ru

import android.content.Context
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.ArrayList
import java.util.HashSet
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local OpenAI-compatible LLM gateway on `http://127.0.0.1:48156` (only
 * listening while provider connections are configured): forwards
 * `/chat/completions` to OpenAI/Anthropic/Gemini (translating request bodies
 * and, for streaming, Anthropic/Gemini SSE into OpenAI chunks) and serves the
 * configured models at `/models`.
 *
 * Ported 1:1 from decompiled `com/agy/ru/AgyGateway.java`. Ports, wire
 * formats and error strings are contracts with the engine.
 */
object AgyGateway {
    @Volatile
    private var started = false

    class Conn internal constructor() {
        var headers: JSONObject? = null
        var type: String = "openai"
        var baseUrl: String = ""
        var apiKey: String = ""
        var selected: MutableList<String> = ArrayList()
    }

    @JvmStatic
    @Synchronized
    fun start(context: Context) {
        synchronized(AgyGateway::class.java) {
            if (started) {
                return
            }
            started = true
            val thread = Thread({
                try {
                    try {
                        while (true) {
                            val serverSocket = ServerSocket(AgyConst.GW, 48, InetAddress.getByName("127.0.0.1"))
                            val client = serverSocket.accept()
                            val conn = Thread({ serve(context, client) }, "AgyGwConn")
                            conn.isDaemon = true
                            conn.start()
                        }
                    } finally {
                    }
                } catch (_: Throwable) {
                    started = false
                }
            }, "AgyGwServer")
            thread.isDaemon = true
            thread.start()
        }
    }

    internal fun config(context: Context): List<Conn> {
        val result = ArrayList<Conn>()
        val load = try {
            AgyProviders(context).load()
        } catch (_: Throwable) {
            null
        }
        if (!load.isNullOrEmpty()) {
            val connections = JSONObject(load).optJSONArray("connections") ?: return result
            for (i in 0 until connections.length()) {
                val connection = connections.optJSONObject(i) ?: continue
                val conn = Conn()
                conn.type = connection.optString("type", "openai")
                conn.baseUrl = connection.optString("baseUrl", "")
                conn.apiKey = connection.optString("apiKey", "")
                conn.headers = connection.optJSONObject("headers")
                val selected = connection.optJSONArray("selected")
                if (selected != null) {
                    for (j in 0 until selected.length()) {
                        conn.selected.add(selected.optString(j))
                    }
                }
                result.add(conn)
            }
        }
        return result
    }

    internal fun find(connections: List<Conn>, model: String?): Conn? {
        if (model == null) {
            return null
        }
        for (conn in connections) {
            if (conn.selected.contains(model)) {
                return conn
            }
        }
        for (conn in connections) {
            for (name in conn.selected) {
                if (model == name || model.endsWith("/$name")) {
                    return conn
                }
            }
        }
        return null
    }

    internal fun serve(context: Context, socket: Socket) {
        try {
            socket.soTimeout = 600000
            val input: InputStream = socket.getInputStream()
            val output: OutputStream = socket.getOutputStream()
            val head = arrayOfNulls<String>(3)
            val contentLength = readHead(input, head, ArrayList())
            if (head[0] == null) {
                socket.close()
                return
            }
            val path = head[1] ?: ""
            val body = if (contentLength > 0) readN(input, contentLength) else ByteArray(0)
            val connections = config(context)
            if (path.contains("/chat/completions")) {
                handleChat(connections, String(body, StandardCharsets.UTF_8), output)
            } else if (path.contains("/models")) {
                handleModels(connections, output)
            } else {
                writeJson(output, 404, err("agy-gateway: no route for $path"))
            }
            socket.close()
        } catch (_: Throwable) {
            try {
                socket.close()
            } catch (_: Throwable) {
            }
        }
    }

    @Throws(Exception::class)
    internal fun readHead(input: InputStream, head: Array<String?>, headers: MutableList<Array<String>>): Int {
        val requestLine = readLine(input) ?: return -1
        val parts = requestLine.split(" ")
        head[0] = if (parts.isNotEmpty()) parts[0] else null
        head[1] = if (parts.size > 1) parts[1] else ""
        head[2] = if (parts.size > 2) parts[2] else ""
        var contentLength = 0
        while (true) {
            val line = readLine(input)
            if (line.isNullOrEmpty()) break
            val colon = line.indexOf(58.toChar())
            if (colon > 0) {
                val name = line.substring(0, colon).trim()
                val value = line.substring(colon + 1).trim()
                headers.add(arrayOf(name, value))
                if (name.equals("Content-Length", ignoreCase = true)) {
                    try {
                        contentLength = value.toInt()
                    } catch (_: Exception) {
                    }
                }
            }
        }
        return contentLength
    }

    @Throws(Exception::class)
    internal fun readLine(input: InputStream): String? {
        val buffer = ByteArrayOutputStream()
        var c: Int
        while (true) {
            c = input.read()
            if (c == -1 || c == 10) {
                break
            }
            if (c != 13) {
                buffer.write(c)
            }
        }
        if (c == -1 && buffer.size() == 0) {
            return null
        }
        return String(buffer.toByteArray(), StandardCharsets.UTF_8)
    }

    @Throws(Exception::class)
    internal fun readN(input: InputStream, count: Int): ByteArray {
        val buffer = ByteArray(count)
        var read = 0
        while (read < count) {
            val n = input.read(buffer, read, count - read)
            if (n <= 0) {
                break
            }
            read += n
        }
        return buffer
    }

    @Throws(Exception::class)
    internal fun handleModels(connections: List<Conn>, output: OutputStream) {
        val models = JSONArray()
        val seen = HashSet<String>()
        for (conn in connections) {
            for (model in conn.selected) {
                if (!model.isNullOrEmpty() && seen.add(model)) {
                    models.put(JSONObject().put("id", model).put("object", "model").put("owned_by", conn.type))
                }
            }
        }
        writeJson(output, 200, JSONObject().put("object", "list").put("data", models).toString())
    }

    @Throws(Exception::class)
    internal fun handleChat(connections: List<Conn>, body: String, output: OutputStream) {
        try {
            val request = JSONObject(body)
            val model = request.optString("model", "")
            val conn = find(connections, model)
            if (conn == null) {
                writeJson(output, 404, err("agy-gateway: model not configured: $model"))
                return
            }
            val stream = request.optBoolean("stream", false)
            var base = AgyProviders.trimUrl(conn.baseUrl)
            if (conn.type == "anthropic") {
                if (base.isEmpty()) {
                    base = "https://api.anthropic.com"
                }
                forwardAnthropic(base, conn, request, model, stream, output)
            } else if (conn.type == "gemini") {
                if (base.isEmpty()) {
                    base = "https://generativelanguage.googleapis.com/v1beta"
                }
                forwardGemini(base, conn, request, model, stream, output)
            } else {
                if (base.isEmpty()) {
                    base = "https://api.openai.com/v1"
                }
                forwardOpenAI(base, conn, AgyEffort.injectOpenAIString(body, model), stream, output)
            }
        } catch (_: Throwable) {
            writeJson(output, 400, err("agy-gateway: bad request json"))
        }
    }

    @Throws(Exception::class)
    internal fun forwardOpenAI(base: String, conn: Conn, body: String?, stream: Boolean, output: OutputStream) {
        val connection = URL("$base/chat/completions").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = 20000
        connection.readTimeout = 600000
        connection.setRequestProperty("Content-Type", HibouConnect.UNARY_TYPE)
        connection.setRequestProperty("Authorization", "Bearer " + conn.apiKey.trim())
        applyHeaders(connection, conn)
        connection.outputStream.write(body?.toByteArray(StandardCharsets.UTF_8) ?: ByteArray(0))
        pipePassthrough(connection, output, stream)
    }

    @Throws(Exception::class)
    internal fun pipePassthrough(connection: HttpURLConnection, output: OutputStream, stream: Boolean) {
        val code = connection.responseCode
        val input: InputStream? = if (code < 200 || code >= 400) connection.errorStream else connection.inputStream
        if (code < 200 || code >= 400) {
            writeJson(output, code, bodyOrErr(code, readAll(input)))
            connection.disconnect()
            return
        }
        if (stream) {
            val writer = beginSse(output)
            val reader = BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8))
            while (true) {
                val line = reader.readLine() ?: break
                writer.write(line + "\n")
                writer.flush()
            }
            writer.flush()
        } else {
            writeJson(output, 200, readAll(input))
        }
        connection.disconnect()
    }

    @Throws(Exception::class)
    internal fun convertUserContent(blocks: JSONArray, anthropic: Boolean): JSONArray {
        val converted = JSONArray()
        for (i in 0 until blocks.length()) {
            val block = blocks.optJSONObject(i) ?: throw IllegalArgumentException("Invalid content block")
            var url: String
            val type = block.optString("type", "")
            if (type == "text") {
                val text = JSONObject().put("text", block.optString("text", ""))
                if (anthropic) {
                    text.put("type", "text")
                }
                converted.put(text)
            } else if (type == "image_url") {
                val image = block.opt("image_url")
                url = when (image) {
                    is JSONObject -> image.optString("url", "")
                    is String -> image
                    else -> ""
                }
                if (url.startsWith("data:image/")) {
                    val comma = url.indexOf(44.toChar())
                    val semicolon = url.indexOf(59.toChar())
                    if (semicolon < 0 || comma < semicolon || comma == url.length - 1 || !url.substring(0, comma).endsWith(";base64")) {
                        throw IllegalArgumentException("Invalid base64 image")
                    }
                    var mediaType = url.substring(5, semicolon).lowercase(Locale.ROOT)
                    if (mediaType == "image/jpg") {
                        mediaType = "image/jpeg"
                    }
                    if (anthropic && mediaType != "image/jpeg" && mediaType != "image/png" && mediaType != "image/gif" && mediaType != "image/webp") {
                        throw IllegalArgumentException("Image format not supported by Anthropic: $mediaType")
                    }
                    val data = url.substring(comma + 1)
                    if (anthropic) {
                        converted.put(
                            JSONObject().put("type", "image").put(
                                "source",
                                JSONObject().put("type", "base64").put("media_type", mediaType).put("data", data)
                            )
                        )
                    } else {
                        converted.put(
                            JSONObject().put(
                                "inline_data",
                                JSONObject().put("mime_type", mediaType).put("data", data)
                            )
                        )
                    }
                } else if (anthropic && (url.startsWith("https://") || url.startsWith("http://"))) {
                    converted.put(
                        JSONObject().put("type", "image").put(
                            "source",
                            JSONObject().put("type", "url").put("url", url)
                        )
                    )
                } else {
                    throw IllegalArgumentException("Unsupported image source; use a base64 image")
                }
            } else {
                throw IllegalArgumentException("Unsupported content block: $type")
            }
        }
        return converted
    }

    @Throws(Exception::class)
    internal fun forwardAnthropic(
        base: String,
        conn: Conn,
        request: JSONObject,
        model: String,
        stream: Boolean,
        output: OutputStream,
    ) {
        val body = JSONObject()
        body.put("model", model)
        body.put("stream", stream)
        val maxTokens = if (request.has("max_tokens")) {
            request.optInt("max_tokens", 4096) // kotlin.io.DEFAULT_BLOCK_SIZE
        } else {
            4096
        }
        body.put("max_tokens", maxTokens)
        if (request.has("temperature")) {
            body.put("temperature", request.optDouble("temperature", 1.0))
        }
        val messages = request.optJSONArray("messages")
        val converted = JSONArray()
        val system = StringBuilder()
        var fullText = ""
        if (messages != null) {
            var i = 0
            while (i < messages.length()) {
                val message = messages.optJSONObject(i)
                if (message != null) {
                    val role = message.optString("role", "user")
                    val content = message.optString("content", "")
                    if (role == "system") {
                        if (system.length > 0) {
                            system.append("\n")
                        }
                        system.append(content)
                    } else {
                        var payload: Any = content
                        if (role == "user" && message.optJSONArray("content") != null) {
                            try {
                                payload = convertUserContent(message.optJSONArray("content"), true)
                            } catch (e: IllegalArgumentException) {
                                writeJson(output, 400, err("agy-gateway: " + e.message))
                                return
                            }
                        }
                        converted.put(
                            JSONObject().put(
                                "role",
                                if (role == "assistant") "assistant" else "user"
                            ).put("content", payload)
                        )
                    }
                }
                i++
            }
        }
        if (system.length > 0) {
            body.put("system", system.toString())
        }
        body.put("messages", converted)
        val outgoing = AgyEffort.injectAnthropic(body, model)
        val connection = URL("$base/v1/messages").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = 20000
        connection.readTimeout = 600000
        connection.setRequestProperty("Content-Type", HibouConnect.UNARY_TYPE)
        connection.setRequestProperty("x-api-key", conn.apiKey.trim())
        connection.setRequestProperty("anthropic-version", "2023-06-01")
        applyHeaders(connection, conn)
        connection.outputStream.write(outgoing.toString().toByteArray(StandardCharsets.UTF_8))
        val code = connection.responseCode
        val input: InputStream? = if (code < 200 || code >= 400) connection.errorStream else connection.inputStream
        if (code < 200 || code >= 400) {
            writeJson(output, code, bodyOrErr(code, readAll(input)))
            connection.disconnect()
            return
        }
        if (stream) {
            val writer = beginSse(output)
            val reader = BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8))
            while (true) {
                val line = reader.readLine() ?: break
                if (line.startsWith("data:")) {
                    val payload = line.substring(5).trim()
                    if (!payload.isEmpty()) {
                        try {
                            val event = JSONObject(payload)
                            when (event.optString("type", "")) {
                                "content_block_delta" -> {
                                    val text = event.optJSONObject("delta")?.optString("text", "") ?: ""
                                    if (!text.isEmpty()) {
                                        try {
                                            writer.write("data: " + openAIChunk(model, text, null) + "\n\n")
                                            writer.flush()
                                        } catch (_: Throwable) {
                                        }
                                    }
                                }
                                "message_stop" -> {
                                    writer.write("data: " + openAIChunk(model, "", "stop") + "\n\n")
                                    writer.write("data: [DONE]\n\n")
                                    writer.flush()
                                }
                            }
                        } catch (_: Throwable) {
                        }
                    }
                }
            }
            writer.flush()
        } else {
            val content = JSONObject(readAll(input)).optJSONArray("content")
            if (content != null) {
                for (i in 0 until content.length()) {
                    val block = content.optJSONObject(i)
                    if (block != null && block.optString("type") == "text") {
                        fullText += block.optString("text")
                    }
                }
            }
            writeJson(output, 200, openAIResp(model, fullText).toString())
        }
        connection.disconnect()
    }

    @Throws(Exception::class)
    internal fun forwardGemini(
        base: String,
        conn: Conn,
        request: JSONObject,
        model: String,
        stream: Boolean,
        output: OutputStream,
    ) {
        val body = JSONObject()
        val contents = JSONArray()
        val system = StringBuilder()
        val messages = request.optJSONArray("messages")
        if (messages != null) {
            var i = 0
            while (i < messages.length()) {
                val message = messages.optJSONObject(i)
                if (message != null) {
                    val role = message.optString("role", "user")
                    val content = message.optString("content", "")
                    if (role == "system") {
                        if (system.length > 0) {
                            system.append("\n")
                        }
                        system.append(content)
                    } else {
                        val parts: JSONArray = if (role == "user" && message.optJSONArray("content") != null) {
                            try {
                                convertUserContent(message.optJSONArray("content"), false)
                            } catch (e: IllegalArgumentException) {
                                writeJson(output, 400, err("agy-gateway: " + e.message))
                                return
                            }
                        } else {
                            JSONArray().put(JSONObject().put("text", content))
                        }
                        contents.put(
                            JSONObject().put(
                                "role",
                                if (role == "assistant") "model" else "user"
                            ).put("parts", parts)
                        )
                    }
                }
                i++
            }
        }
        body.put("contents", contents)
        if (system.length > 0) {
            body.put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system.toString())))
            )
        }
        val outgoing = AgyEffort.injectGemini(body, model)
        val url = base + "/models/" + model +
            (if (stream) ":streamGenerateContent?alt=sse&key=" else ":generateContent?key=") +
            URLEncoder.encode(conn.apiKey.trim(), "UTF-8")
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = 20000
        connection.readTimeout = 600000
        connection.setRequestProperty("Content-Type", HibouConnect.UNARY_TYPE)
        applyHeaders(connection, conn)
        connection.outputStream.write(outgoing.toString().toByteArray(StandardCharsets.UTF_8))
        val code = connection.responseCode
        val input: InputStream? = if (code < 200 || code >= 400) connection.errorStream else connection.inputStream
        if (code < 200 || code >= 400) {
            writeJson(output, code, bodyOrErr(code, readAll(input)))
            connection.disconnect()
            return
        }
        if (!stream) {
            writeJson(output, 200, openAIResp(model, geminiText(JSONObject(readAll(input)))).toString())
        } else {
            val writer = beginSse(output)
            val reader = BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8))
            while (true) {
                val line = reader.readLine() ?: break
                if (line.startsWith("data:")) {
                    val payload = line.substring(5).trim()
                    if (!payload.isEmpty()) {
                        try {
                            val text = geminiText(JSONObject(payload))
                            if (!text.isEmpty()) {
                                writer.write("data: " + openAIChunk(model, text, null) + "\n\n")
                                writer.flush()
                            }
                        } catch (_: Throwable) {
                        }
                    }
                }
            }
            writer.write("data: " + openAIChunk(model, "", "stop") + "\n\n")
            writer.write("data: [DONE]\n\n")
            writer.flush()
        }
        connection.disconnect()
    }

    internal fun geminiText(response: JSONObject): String {
        val sb = StringBuilder()
        val candidates = response.optJSONArray("candidates")
        if (candidates != null && candidates.length() > 0) {
            val candidate = candidates.optJSONObject(0)
            val content = candidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val part = parts.optJSONObject(i)
                    if (part != null && part.has("text")) {
                        sb.append(part.optString("text"))
                    }
                }
            }
        }
        return sb.toString()
    }

    internal fun openAIChunk(model: String, content: String?, finishReason: String?): String {
        return try {
            val delta = JSONObject()
            if (!content.isNullOrEmpty()) {
                delta.put("content", content)
            }
            val choice = JSONObject().put("index", 0).put("delta", delta)
            if (finishReason != null) {
                choice.put("finish_reason", finishReason)
            }
            JSONObject()
                .put("object", "chat.completion.chunk")
                .put("model", model)
                .put("choices", JSONArray().put(choice))
                .toString()
        } catch (_: Throwable) {
            "{}"
        }
    }

    internal fun openAIResp(model: String, content: String): JSONObject {
        return try {
            JSONObject()
                .put("object", "chat.completion")
                .put("model", model)
                .put(
                    "choices",
                    JSONArray().put(
                        JSONObject()
                            .put("index", 0)
                            .put(
                                "message",
                                JSONObject().put("role", "assistant").put("content", content)
                            )
                            .put("finish_reason", "stop")
                    )
                )
        } catch (_: Throwable) {
            JSONObject()
        }
    }

    internal fun applyHeaders(connection: HttpURLConnection, conn: Conn) {
        val headers = conn.headers ?: return
        val keys = headers.keys() ?: return
        while (keys.hasNext()) {
            val name = keys.next()
            connection.setRequestProperty(name, headers.optString(name))
        }
    }

    @Throws(Exception::class)
    internal fun beginSse(output: OutputStream): Writer {
        val writer = OutputStreamWriter(output, StandardCharsets.UTF_8)
        writer.write(
            "HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nCache-Control: no-cache\r\nConnection: close\r\n\r\n"
        )
        writer.flush()
        return writer
    }

    @Throws(Exception::class)
    internal fun readAll(input: InputStream?): String {
        if (input == null) {
            return ""
        }
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192) // kotlin.io.DEFAULT_BUFFER_SIZE
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) {
                return String(out.toByteArray(), StandardCharsets.UTF_8)
            }
            out.write(buffer, 0, read)
        }
    }

    @Throws(Exception::class)
    internal fun writeJson(output: OutputStream, code: Int, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        val writer = OutputStreamWriter(output, StandardCharsets.UTF_8)
        writer.write(
            "HTTP/1.1 $code OK\r\nContent-Type: application/json\r\nContent-Length: " + bytes.size +
                "\r\nConnection: close\r\n\r\n"
        )
        writer.flush()
        output.write(bytes)
        output.flush()
    }

    internal fun bodyOrErr(code: Int, body: String?): String {
        if (body != null && body.trim().startsWith("{")) {
            return body
        }
        return err("provider $code: ${body ?: ""}")
    }

    internal fun err(message: String): String {
        return try {
            JSONObject()
                .put("error", JSONObject().put("message", message).put("code", 400))
                .toString()
        } catch (_: Throwable) {
            "{\"error\":{\"message\":\"agy-gateway\"}}"
        }
    }
}
