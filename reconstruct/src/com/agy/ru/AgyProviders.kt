package com.agy.ru

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.lang.reflect.Method
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject

/**
 * WebView JS bridge for the provider-connection settings: loads/saves
 * `agy-providers/providers.json`, restarts the core service, and fetches
 * model lists from OpenAI/Anthropic/Gemini endpoints asynchronously.
 *
 * Ported 1:1 from decompiled `com/agy/ru/AgyProviders.java`. The restart
 * flow (stop → wait for port 48155 to close → 400ms → start on main thread)
 * and the fetch-job protocol (`status`/`body`/`done` with
 * HibouConnect.PROTOCOL_VERSION as the done sentinel) are contracts with the
 * web UI and stay unchanged.
 */
class AgyProviders(context: Context) {
    private val ctx: Context = context
    private val jobs = ConcurrentHashMap<String, Array<String>>()

    companion object {
        @Volatile
        private var restarting = false

        @JvmStatic
        fun app(): Context? {
            return try {
                Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication")
                    .invoke(null) as Context?
            } catch (_: Throwable) {
                null
            }
        }

        @JvmStatic
        fun file(context: Context): File {
            val dir = File(context.filesDir, "agy-providers")
            dir.mkdirs()
            return File(dir, "providers.json")
        }

        @JvmStatic
        fun portAlive(port: Int): Boolean {
            val socket = Socket()
            return try {
                socket.connect(InetSocketAddress("127.0.0.1", port), 250)
                try {
                    socket.close()
                    true
                } catch (_: Throwable) {
                    true
                }
            } catch (_: Throwable) {
                try {
                    socket.close()
                } catch (_: Throwable) {
                }
                false
            }
        }

        @JvmStatic
        fun trimUrl(raw: String?): String {
            var url = raw?.trim() ?: ""
            while (url.endsWith("/")) {
                url = url.substring(0, url.length - 1)
            }
            if (url.endsWith("/responses")) {
                url = url.substring(0, url.length - "/responses".length)
            }
            if (url.endsWith("/chat/completions")) {
                return url.substring(0, url.length - "/chat/completions".length)
            }
            return url
        }

        @Throws(Exception::class)
        internal fun doFetch(provider0: String?, url: String, apiKey: String?, extraHeaders: String?): Array<String> {
            var provider = provider0
            var base = trimUrl(url)
            if (provider == null) {
                provider = "openai"
            }
            val endpoint: String
            if (provider == "gemini") {
                if (base.isEmpty()) {
                    base = "https://generativelanguage.googleapis.com/v1beta"
                }
                endpoint = base + "/models?key=" + (apiKey?.trim() ?: "")
            } else if (provider == "anthropic") {
                if (base.isEmpty()) {
                    base = "https://api.anthropic.com"
                }
                endpoint = "$base/v1/models"
            } else {
                if (base.isEmpty()) {
                    base = "https://api.openai.com/v1"
                }
                endpoint = "$base/models"
            }
            val connection = URL(endpoint).openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.setRequestProperty("Accept", HibouConnect.UNARY_TYPE)
            if (provider == "anthropic") {
                connection.setRequestProperty("x-api-key", apiKey?.trim() ?: "")
                connection.setRequestProperty("anthropic-version", "2023-06-01")
            } else if (provider != "gemini") {
                connection.setRequestProperty("Authorization", "Bearer " + (apiKey?.trim() ?: ""))
            }
            if (!extraHeaders.isNullOrBlank()) {
                try {
                    val headers = JSONObject(extraHeaders)
                    val keys = headers.keys()
                    while (keys.hasNext()) {
                        val name = keys.next()
                        connection.setRequestProperty(name, headers.optString(name))
                    }
                } catch (_: Throwable) {
                }
            }
            val code = connection.responseCode
            val stream: InputStream? =
                if (code < 200 || code >= 400) connection.errorStream else connection.inputStream
            val body = ByteArrayOutputStream()
            if (stream != null) {
                val buffer = ByteArray(8192) // kotlin.io.DEFAULT_BUFFER_SIZE
                while (true) {
                    val read = stream.read(buffer)
                    if (read <= 0) break
                    body.write(buffer, 0, read)
                }
                stream.close()
            }
            connection.disconnect()
            var text = String(body.toByteArray(), StandardCharsets.UTF_8)
            if (text.length > 200000) {
                text = text.substring(0, 200000)
            }
            return arrayOf(code.toString(), text, HibouConnect.PROTOCOL_VERSION)
        }
    }

    @JavascriptInterface
    fun load(): String {
        return try {
            val file = file(ctx)
            if (!file.exists()) {
                ""
            } else {
                val randomAccess = RandomAccessFile(file, "r")
                val bytes = ByteArray(randomAccess.length().toInt())
                randomAccess.readFully(bytes)
                randomAccess.close()
                String(bytes, StandardCharsets.UTF_8)
            }
        } catch (_: Throwable) {
            ""
        }
    }

    @JavascriptInterface
    fun save(raw: String?): String {
        val text = raw ?: ""
        return try {
            val file = file(ctx)
            val tmp = File(file.parentFile, "providers.json.tmp")
            val out = FileOutputStream(tmp)
            out.write(text.toByteArray(StandardCharsets.UTF_8))
            out.fd.sync()
            out.close()
            if (file.exists()) {
                file.delete()
            }
            if (!tmp.renameTo(file)) "ERR:rename" else "ok"
        } catch (t: Throwable) {
            "ERR:$t"
        }
    }

    @JavascriptInterface
    fun available(): Boolean = true

    @JavascriptInterface
    fun restartCore() {
        if (restarting) {
            return
        }
        val appContext = ctx.applicationContext ?: app() ?: return
        restarting = true
        Thread({
            try {
                val cls = Class.forName("com.agy.core.CoreServiceLauncher")
                val stop = cls.getMethod("stop", Context::class.java)
                val start = cls.getMethod("start", Context::class.java)
                try {
                    stop.invoke(null, appContext)
                } catch (_: Throwable) {
                }
                val deadline = System.currentTimeMillis() + 12000
                while (System.currentTimeMillis() < deadline && portAlive(48155)) {
                    try {
                        Thread.sleep(300L)
                    } catch (_: InterruptedException) {
                    }
                }
                try {
                    Thread.sleep(400L)
                } catch (_: InterruptedException) {
                }
                Handler(Looper.getMainLooper()).post {
                    try {
                        start.invoke(null, appContext)
                    } catch (_: Throwable) {
                    }
                }
            } catch (_: Throwable) {
            }
            restarting = false
        }, "AgyCoreRestart").start()
    }

    @JavascriptInterface
    fun fetchModels(provider: String?, url: String, apiKey: String?, extraHeaders: String?): String {
        val jobId = System.nanoTime().toString()
        jobs[jobId] = arrayOf("", "", "")
        val thread = Thread({
            val result: Array<String> = try {
                doFetch(provider, url, apiKey, extraHeaders)
            } catch (t: Throwable) {
                arrayOf("0", "ERR:$t", HibouConnect.PROTOCOL_VERSION)
            }
            jobs[jobId] = result
        }, "AgyProvFetch")
        thread.isDaemon = true
        thread.start()
        return jobId
    }

    @JavascriptInterface
    fun readFetch(jobId: String?): String {
        val result = if (jobId == null) null else jobs[jobId]
        if (result == null) {
            return "{}"
        }
        return try {
            val json = JSONObject()
            json.put("status", result[0])
            json.put("body", result[1])
            json.put("done", HibouConnect.PROTOCOL_VERSION == result[2])
            if (HibouConnect.PROTOCOL_VERSION == result[2]) {
                jobs.remove(jobId)
            }
            json.toString()
        } catch (_: Throwable) {
            "{}"
        }
    }
}
