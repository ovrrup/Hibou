package com.agy.ru

import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reads per-model reasoning-effort metadata from the provider connections
 * file and injects provider-specific "thinking" fields into request bodies
 * (OpenAI `reasoning_effort`, Anthropic `thinking.budget_tokens`, Gemini
 * `generationConfig.thinkingLevel`).
 *
 * Ported from decompiled `com/agy/ru/AgyEffort.java`. Decompile note: in
 * `readMeta` the recovered bytecode had a flattened control-flow artifact
 * (`z = false` emitted before the `if (z)` check, which would have made the
 * branch dead); the reconstructed semantics below match the intended
 * behavior: a connection whose `selected` list matches the model (exact or
 * `/name` suffix) contributes its `modelMeta[model]`.
 */
object AgyEffort {
    @JvmStatic
    var TEST_FILE: String? = null

    internal fun readMeta(model: String?): JSONObject? {
        var text: String? = null
        try {
            if (TEST_FILE == null) {
                val app = Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication")
                    .invoke(null) ?: return null
                val file = File(
                    app.javaClass.getMethod("getFilesDir").invoke(app).toString(),
                    "agy-providers/providers.json",
                )
                if (!file.exists()) {
                    return null
                }
                val randomAccess = RandomAccessFile(file, "r")
                val bytes = ByteArray(randomAccess.length().toInt())
                randomAccess.readFully(bytes)
                randomAccess.close()
                text = String(bytes, StandardCharsets.UTF_8)
            } else {
                val file = File(TEST_FILE)
                if (!file.exists()) {
                    text = null
                } else {
                    val randomAccess = RandomAccessFile(file, "r")
                    val bytes = ByteArray(randomAccess.length().toInt())
                    randomAccess.readFully(bytes)
                    randomAccess.close()
                    text = String(bytes, StandardCharsets.UTF_8)
                }
            }
        } catch (_: Throwable) {
        }
        if (text.isNullOrEmpty()) {
            return null
        }
        val connections = JSONObject(text).optJSONArray("connections") ?: return null
        for (i in 0 until connections.length()) {
            val connection = connections.optJSONObject(i) ?: continue
            val selected = connection.optJSONArray("selected") ?: continue
            var matched = false
            for (j in 0 until selected.length()) {
                val name = selected.optString(j)
                if (model != null && (model == name || model.endsWith("/$name"))) {
                    matched = true
                    break
                }
            }
            if (matched) {
                val modelMeta = connection.optJSONObject("modelMeta") ?: return null
                return modelMeta.optJSONObject(model)
            }
        }
        return null
    }

    internal fun effortFor(model: String?): String? {
        return try {
            val meta = readMeta(model) ?: return null
            val effort = meta.optString("effort", "")
            if (effort.isEmpty()) null else effort
        } catch (_: Throwable) {
            null
        }
    }

    internal fun openaiEffort(effort: String?): String? {
        if (effort == null) return null
        if (effort == "low" || effort == "medium" || effort == "high") {
            return effort
        }
        if (effort == "xhigh" || effort == "max") {
            return "high"
        }
        return null
    }

    internal fun anthropicBudget(effort: String?, meta: JSONObject?): Int {
        if (meta != null) {
            try {
                if (meta.optInt("thinkingBudget", 0) > 0) {
                    return meta.optInt("thinkingBudget", 0)
                }
            } catch (_: Throwable) {
            }
        }
        when (effort) {
            "low" -> return 1024
            "medium" -> return 4096 // kotlin.io.DEFAULT_BLOCK_SIZE
            "high" -> return 8192 // kotlin.io.DEFAULT_BUFFER_SIZE
            "xhigh" -> return 16384
            "max" -> return 32768
        }
        return 0
    }

    internal fun geminiLevel(effort: String?): String {
        return if (effort == "low") "low" else "high"
    }

    @JvmStatic
    fun injectOpenAI(body: JSONObject?, model: String?): JSONObject? {
        if (body != null) {
            if (!body.has("reasoning_effort")) {
                val effort = openaiEffort(effortFor(model)) ?: return body
                body.put("reasoning_effort", effort)
                return body
            }
        }
        return body
    }

    @JvmStatic
    fun injectOpenAIString(body: String?, model: String?): String? {
        return try {
            val json = JSONObject(body)
            injectOpenAI(json, model)
            json.toString()
        } catch (_: Throwable) {
            body
        }
    }

    @JvmStatic
    fun injectAnthropic(body: JSONObject?, model: String?): JSONObject? {
        if (body != null) {
            if (!body.has("thinking")) {
                val effort = effortFor(model)
                val budget = if (effort == null) 0 else anthropicBudget(effort, readMeta(model))
                if (effort == null || budget <= 0) {
                    return body
                }
                body.put("thinking", JSONObject().put("type", "enabled").put("budget_tokens", budget))
                return body
            }
        }
        return body
    }

    @JvmStatic
    fun injectGemini(body: JSONObject?, model: String?): JSONObject? {
        if (body == null) {
            return body
        }
        return try {
            var config = body.optJSONObject("generationConfig")
            if ((config != null && config.has("thinkingLevel")) || effortFor(model) == null) {
                return body
            }
            if (config == null) {
                config = JSONObject()
                body.put("generationConfig", config)
            }
            config.put("thinkingLevel", geminiLevel(effortFor(model)))
            body
        } catch (_: Throwable) {
            body
        }
    }
}
