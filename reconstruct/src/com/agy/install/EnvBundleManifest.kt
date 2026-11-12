package com.agy.install

import android.content.res.AssetManager
import com.agy.util.IoUtil
import com.agy.util.Sha256
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Collections
import java.util.regex.Pattern
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Verifies `assets/env/manifest.json` (schema 1): SHA-256 fields for the
 * bootstrap/overlay/pkg archives, bootstrap symlink count and the seed
 * package list.
 *
 * Ported 1:1 from decompiled `com/agy/install/EnvBundleManifest.java`.
 */
class EnvBundleManifest private constructor(
    val bundleId: String,
    val bootstrapSha256: String,
    val overlaySha256: String,
    val pkgSha256: String,
    val bootstrapSymlinkCount: Int,
    val seedPackages: List<String>,
) {
    @Throws(IOException::class)
    fun verifyBootstrap(file: File) {
        try {
            val actual = Sha256.of(file)
            if (bootstrapSha256 != actual) {
                throw IOException("bootstrap sha256 mismatch: got $actual want $bootstrapSha256")
            }
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("cannot hash bootstrap archive", e)
        }
    }

    companion object {
        private const val SUPPORTED_SCHEMA = 1
        private val SHA256: Pattern = Pattern.compile("^[0-9a-f]{64}$")
        private val PACKAGE: Pattern = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9+._-]*$")

        @JvmStatic
        fun load(assetManager: AssetManager): EnvBundleManifest {
            try {
                val input: InputStream = assetManager.open(EnvPaths.ASSET_MANIFEST)
                try {
                    val root = JSONObject(IoUtil.readAll(input, "UTF-8"))
                    val schema = root.getInt("schema")
                    if (schema != SUPPORTED_SCHEMA) {
                        throw IllegalStateException("unsupported env manifest schema: $schema")
                    }
                    val id = requireSha256(root.getString("bundleId"), "bundleId")
                    val bootstrap = requireSha256(root.getString("bootstrapSha256"), "bootstrapSha256")
                    val overlay = requireSha256(root.getString("overlaySha256"), "overlaySha256")
                    val pkg = requireSha256(root.getString("pkgSha256"), "pkgSha256")
                    val symlinkCount = root.getInt("bootstrapSymlinkCount")
                    if (symlinkCount < 1) {
                        throw IllegalStateException("env manifest contains no bootstrap symlinks")
                    }
                    val seedArray: JSONArray = root.getJSONArray("seedPackages")
                    val seeds = ArrayList<String>()
                    for (i in 0 until seedArray.length()) {
                        val name = seedArray.getString(i)
                        if (!PACKAGE.matcher(name).matches()) {
                            throw IllegalStateException("invalid seed package name: $name")
                        }
                        if (!seeds.contains(name)) {
                            seeds.add(name)
                        }
                    }
                    input.close()
                    return EnvBundleManifest(
                        id,
                        bootstrap,
                        overlay,
                        pkg,
                        symlinkCount,
                        Collections.unmodifiableList(seeds),
                    )
                } catch (t: Throwable) {
                    try {
                        input.close()
                    } catch (suppressed: Throwable) {
                        t.addSuppressed(suppressed)
                    }
                    throw t
                }
            } catch (e: IOException) {
                throw IllegalStateException("required asset missing: env/manifest.json", e)
            } catch (e: JSONException) {
                throw IllegalStateException("invalid env manifest: " + e.message, e)
            }
        }

        private fun requireSha256(value: String?, field: String): String {
            if (value == null || !SHA256.matcher(value).matches()) {
                throw IllegalStateException("invalid env manifest field: $field")
            }
            return value
        }
    }
}
