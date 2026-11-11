package com.agy.ru

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import com.agy.util.AppFileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.HashSet
import java.util.Locale
import java.util.zip.ZipFile
import org.json.JSONObject

/**
 * Native metadata and verified, user-confirmed APK installation for Hibou in Kotlin.
 */
object HibouUpdates {

    @Throws(Exception::class)
    private fun read(input: InputStream): String {
        return input.use { stream ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            var n: Int
            while (stream.read(buf).also { n = it } != -1) {
                out.write(buf, 0, n)
            }
            out.toString("UTF-8")
        }
    }

    @Throws(Exception::class)
    private fun metadata(ctx: Context): JSONObject {
        return JSONObject(read(ctx.assets.open("web/build-info.json")))
    }

    @JvmStatic
    fun getAppInfo(ctx: Context): String {
        return try {
            val pi: PackageInfo = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            val info = metadata(ctx)
            info.put("packageName", pi.packageName)
            info.put("versionName", pi.versionName)
            info.put("versionCode", pi.versionCode)
            info.toString()
        } catch (e: Exception) {
            "{}"
        }
    }

    @Throws(Exception::class)
    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { stream ->
            val buf = ByteArray(65536)
            var n: Int
            while (stream.read(buf).also { n = it } != -1) {
                md.update(buf, 0, n)
            }
        }
        val out = StringBuilder()
        for (b in md.digest()) {
            out.append(String.format(Locale.US, "%02x", (b.toInt() and 0xFF)))
        }
        return out.toString()
    }

    private fun signatures(info: PackageInfo): Set<String> {
        val out = HashSet<String>()
        val sigs = info.signatures
        if (sigs != null) {
            for (sig in sigs) {
                out.add(sig.toCharsString())
            }
        }
        return out
    }

    @JvmStatic
    fun installUpdate(ctx: Context): String {
        return try {
            var apk = File(ctx.filesDir, "updates/hibou-update.apk")
            var checksum = File(ctx.filesDir, "updates/hibou-update.sha256")
            if (!apk.isFile || !checksum.isFile) {
                apk = File(ctx.filesDir, "updates/leostryx-update.apk")
                checksum = File(ctx.filesDir, "updates/leostryx-update.sha256")
            }
            if (!apk.isFile || !checksum.isFile) return "error:missing_download"

            val expected = read(FileInputStream(checksum)).trim()
            if (!expected.matches(Regex("[a-f0-9]{64}")) || expected != sha256(apk)) {
                return "error:checksum"
            }

            val pm = ctx.packageManager
            val current = pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNATURES)
            val next = pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNATURES)
            if (next == null || current.packageName != next.packageName) return "error:package"
            if (next.versionCode <= current.versionCode) return "error:version"
            if (signatures(current).isEmpty() || signatures(current) != signatures(next)) return "error:signature"

            val zip = ZipFile(apk)
            try {
                val incoming = JSONObject(read(zip.getInputStream(zip.getEntry("assets/web/build-info.json"))))
                if (metadata(ctx).getString("variant") != incoming.getString("variant")) return "error:variant"
            } finally {
                zip.close()
            }

            if (Build.VERSION.SDK_INT >= 26) {
                val canRequest = PackageManager::class.java.getMethod("canRequestPackageInstalls").invoke(pm) as Boolean
                if (!canRequest) {
                    val settings = Intent("android.settings.MANAGE_UNKNOWN_APP_SOURCES", Uri.parse("package:" + ctx.packageName))
                    settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(settings)
                    return "permission"
                }
            }

            val uri = AppFileProvider.getUriForFile(ctx, apk)
            val install = Intent(Intent.ACTION_VIEW)
            install.setDataAndType(uri, "application/vnd.android.package-archive")
            install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            ctx.startActivity(install)
            "installer"
        } catch (e: Exception) {
            "error:" + e.javaClass.simpleName
        }
    }
}
