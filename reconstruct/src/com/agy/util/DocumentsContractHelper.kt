package com.agy.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import java.io.File

/**
 * Resolves SAF tree/document URIs into filesystem paths for the gateway's
 * project picker.
 *
 * Ported 1:1 from decompiled `com/agy/util/DocumentsContractHelper.java`.
 */
object DocumentsContractHelper {
    private const val TAG = "DocumentsContractHelper"

    @JvmStatic
    fun resolveTreeUriToPath(context: Context?, uri: Uri?): String? {
        if (uri == null) return null
        return try {
            val treeDocumentId = DocumentsContract.getTreeDocumentId(uri) ?: return null
            var basePath = Environment.getExternalStorageDirectory().absolutePath
            if (treeDocumentId.startsWith("primary:")) {
                var sub = treeDocumentId.substring("primary:".length)
                if (sub.startsWith("/")) sub = sub.substring(1)
                if (sub.isNotEmpty()) {
                    basePath = File(basePath, sub).absolutePath
                }
                return basePath
            }
            val parts = treeDocumentId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (parts.size > 1) {
                val storage = parts[0]
                var sub = parts[1]
                if (sub.startsWith("/")) sub = sub.substring(1)
                if ("primary".equals(storage, ignoreCase = true)) {
                    if (sub.isNotEmpty()) {
                        basePath = File(basePath, sub).absolutePath
                    }
                    return basePath
                }
                val candidate = File("/storage/$storage/$sub")
                if (candidate.exists()) {
                    return candidate.absolutePath
                }
                if (sub.isNotEmpty()) {
                    basePath = File(basePath, sub).absolutePath
                }
                return basePath
            }
            var remainder = treeDocumentId
            if (remainder.startsWith("/")) remainder = remainder.substring(1)
            if (remainder.isNotEmpty()) {
                basePath = File(basePath, remainder).absolutePath
            }
            basePath
        } catch (t: Throwable) {
            Log.w(TAG, "Error resolving tree uri to path: $uri", t)
            null
        }
    }
}
