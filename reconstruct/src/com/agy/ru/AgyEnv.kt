package com.agy.ru

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Wires the local LLM gateway into the core's environment when the user has
 * configured provider connections: sets the `AGY_LLM_GATEWAY_*` variables and
 * starts [AgyGateway] on port [AgyConst.GW].
 *
 * Ported 1:1 from decompiled `com/agy/ru/AgyEnv.java` (gateway URL/key/wire
 * protocol are engine contracts).
 */
object AgyEnv {
    @JvmStatic
    fun apply(processBuilder: ProcessBuilder?) {
        try {
            val app = AgyProviders.app()
            if (app != null && processBuilder != null) {
                val load = AgyProviders(app).load()
                if (!load.isNullOrEmpty()) {
                    val root = JSONObject(load)
                    var hasSelection = false
                    if (root.optBoolean("useProviders", false)) {
                        val connections: JSONArray? = root.optJSONArray("connections")
                        if (connections != null) {
                            var i = 0
                            while (i < connections.length()) {
                                val connection = connections.optJSONObject(i)
                                val selected = connection?.optJSONArray("selected")
                                if (selected != null && selected.length() > 0) {
                                    hasSelection = true
                                    break
                                }
                                i++
                            }
                        }
                        if (hasSelection) {
                            val env = processBuilder.environment()
                            env["AGY_LLM_GATEWAY_URL"] = "http://127.0.0.1:48156"
                            env["AGY_LLM_GATEWAY_API_KEY"] = "agy-gateway"
                            env["AGY_LLM_GATEWAY_WIRE_PROTOCOL"] = "openai"
                            env["AGY_LLM_GATEWAY_HEADERS"] = "{}"
                            AgyGateway.start(app)
                        }
                    }
                }
            }
        } catch (_: Throwable) {
        }
    }
}
