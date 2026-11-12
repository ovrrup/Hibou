package com.agy.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import java.io.File

/**
 * Opens URLs, directories and files with the appropriate system handlers,
 * using the file provider for local content.
 *
 * Ported 1:1 from decompiled `com/agy/util/UriDispatcher.java`; the OAuth
 * userinfo/token endpoint URLs stay excluded from VIEW dispatch, matching the
 * original behavior.
 */
object UriDispatcher {
    private const val TAG = "UriDispatcher"

    @JvmStatic
    fun open(context: Context?, raw: String?): Boolean {
        if (context == null || raw == null || raw.trim().isEmpty()) return false
        val target = raw.trim()
        if (target.startsWith("http://") || target.startsWith("https://")) {
            if (target.contains("googleapis.com/oauth2/v2/userinfo") ||
                target.contains("googleapis.com/auth") ||
                target.contains("daily-cloudcode-pa.googleapis.com")
            ) {
                return false
            }
            return try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(target))
                intent.addFlags(268435456)
                intent.putExtra("android.support.customtabs.extra.TITLE_VISIBILITY", 1)
                context.startActivity(intent)
                true
            } catch (e: Exception) {
                Log.w(TAG, "Cannot open web uri: $target", e)
                false
            }
        }
        var file: File? = null
        if (target.startsWith("file://")) {
            try {
                file = File(Uri.parse(target).path)
            } catch (_: Exception) {
            }
        } else if (target.startsWith("/")) {
            file = File(target)
        }
        if (file != null) {
            if (file.isDirectory) return openDirectory(context, file)
            if (file.isFile) return openFile(context, file)
        }
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(target))
            intent.addFlags(268435457)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Cannot open generic uri: $target", e)
            false
        }
    }

    @JvmStatic
    fun openDirectory(context: Context, file: File): Boolean {
        return try {
            val uri = AppFileProvider.getUriForFile(context, file)
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, "vnd.android.document/directory")
            intent.addFlags(268435457)
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            try {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.setDataAndType(Uri.fromFile(file), "resource/folder")
                intent.addFlags(268435456)
                context.startActivity(intent)
                true
            } catch (e: Exception) {
                Log.w(TAG, "Cannot open directory: $file", e)
                false
            }
        }
    }

    @JvmStatic
    fun openFile(context: Context, file: File): Boolean {
        return try {
            val uri = AppFileProvider.getUriForFile(context, file)
            var type = context.contentResolver.getType(uri)
            if (type == null) {
                type = "*/*"
            }
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, type)
            intent.addFlags(268435457)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Cannot open file: $file", e)
            false
        }
    }
}
