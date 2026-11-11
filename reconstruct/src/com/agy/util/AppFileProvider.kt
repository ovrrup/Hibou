// Minimal FileProvider stand-in using reflection, mirroring the original Java behavior.
package com.agy.util

import android.content.Context
import android.net.Uri
import java.io.File

object AppFileProvider {
    @JvmStatic
    fun getUriForFile(context: Context, file: File): Uri {
        try {
            val fp = Class.forName("androidx.core.content.FileProvider")
            return fp.getMethod("getUriForFile", Context::class.java, String::class.java, File::class.java)
                .invoke(null, context, context.packageName + ".fileprovider", file) as Uri
        } catch (_: Exception) {
            return Uri.parse("content://" + context.packageName + ".fileprovider/" + file.name)
        }
    }
}
