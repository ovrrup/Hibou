package com.agy.runtime

import android.content.Context
import com.agy.util.IoUtil
import com.agy.util.Sha256
import java.io.File
import java.io.InputStream
import org.json.JSONObject

/**
 * Verifies every extracted runtime file against `assets/runtime/manifest.json`
 * hashes. The core binary itself is exempt from the hash check (it is
 * re-fingerprinted at start), but never exempt from the non-empty check.
 *
 * Ported from decompiled `com/agy/runtime/ManifestVerifier.java`. The decompile
 * showed a control-flow artifact (`if (!a && !a)` plus an unreachable
 * trailing `missing runtime file` return); the reconstructed if/else below
 * matches the intended behavior: present-and-verified files pass, everything
 * else fails with the matching error string.
 */
object ManifestVerifier {
    @JvmStatic
    fun verifyError(context: Context, runtimeDir: File): String? {
        var input: InputStream? = null
        try {
            input = context.assets.open(RuntimePaths.MANIFEST_ASSET)
            val root = JSONObject(IoUtil.readAll(input, "UTF-8"))
            val table = root.optJSONObject("files") ?: root
            val keys = table.keys()
            while (keys.hasNext()) {
                val name = keys.next()
                if (name.endsWith("manifest.json")) {
                    continue
                }
                val expected = table.optString(name)
                val file = File(runtimeDir, name)
                if (expected != null && file.isFile) {
                    if (!name.endsWith(RuntimePaths.CORE_NAME)) {
                        val actual = Sha256.of(file)
                        if (!expected.equals(actual, ignoreCase = true)) {
                            return "hash mismatch for $name (expected=$expected actual=$actual)"
                        }
                    }
                    if (file.length() <= 0) {
                        return "empty runtime file: $name"
                    }
                } else {
                    return "missing runtime file: $name"
                }
            }
            return null
        } catch (e: Exception) {
            return "manifest unreadable: " + e.message
        } finally {
            IoUtil.closeQuietly(input)
        }
    }
}
