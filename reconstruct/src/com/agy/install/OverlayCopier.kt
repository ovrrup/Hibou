package com.agy.install

import android.content.res.AssetManager
import com.agy.util.AssetUnpacker
import java.io.File

/**
 * Thin wrappers that turn asset IO failures into [IllegalStateException].
 *
 * Ported 1:1 from decompiled `com/agy/install/OverlayCopier.java`.
 */
object OverlayCopier {
    @JvmStatic
    fun overlay(assetManager: AssetManager, path: String, target: File) {
        val list = list(assetManager, path)
        if (list == null || list.isEmpty()) {
            throw IllegalStateException("missing/empty asset tree: $path")
        }
        copyTree(assetManager, path, target)
    }

    @JvmStatic
    fun copyTree(assetManager: AssetManager, path: String, target: File) {
        try {
            AssetUnpacker.copyTree(assetManager, path, target)
        } catch (e: java.io.IOException) {
            throw IllegalStateException("cannot copy asset tree $path: " + e.message, e)
        }
    }

    @JvmStatic
    fun copyFile(assetManager: AssetManager, path: String, target: File) {
        try {
            AssetUnpacker.copyFile(assetManager, path, target)
        } catch (e: java.io.IOException) {
            throw IllegalStateException("cannot copy asset $path -> $target", e)
        }
    }

    @JvmStatic
    fun list(assetManager: AssetManager, path: String): Array<String>? {
        try {
            return assetManager.list(path)
        } catch (e: java.io.IOException) {
            throw IllegalStateException("cannot list asset $path: " + e.message, e)
        }
    }
}
