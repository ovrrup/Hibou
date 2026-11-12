package com.agy.util

import android.content.res.AssetManager
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * Recursive asset extraction used by the runtime and env installers.
 *
 * Ported 1:1 from decompiled `com/agy/util/AssetUnpacker.java`.
 */
object AssetUnpacker {
    @JvmStatic
    @Throws(IOException::class)
    fun copyTree(assets: AssetManager, path: String, target: File) {
        val list = assets.list(path)
        if (list == null || list.isEmpty()) {
            copyFile(assets, path, target)
            return
        }
        if (!target.isDirectory && !target.mkdirs() && !target.isDirectory) {
            throw IOException("cannot create $target")
        }
        for (child in list) {
            copyTree(assets, "$path/$child", File(target, child))
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun unpackTree(assets: AssetManager, path: String, target: File) {
        val list = assets.list(path)
        if (list == null || list.isEmpty()) {
            throw IOException("asset tree missing or empty: $path")
        }
        if (!target.exists() && !target.mkdirs()) {
            throw IOException("cannot create $target")
        }
        copyTree(assets, path, target)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun copyFile(assets: AssetManager, path: String, target: File) {
        val parent = target.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw IOException("cannot create $parent")
        }
        val input: InputStream = assets.open(path)
        try {
            val out = FileOutputStream(target)
            try {
                IoUtil.pipe(input, out)
                out.close()
            } finally {
            }
            input.close()
        } catch (t: Throwable) {
            try {
                input.close()
            } catch (suppressed: Throwable) {
                t.addSuppressed(suppressed)
            }
            throw t
        }
    }
}
