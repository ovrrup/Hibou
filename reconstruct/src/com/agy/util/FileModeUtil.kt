package com.agy.util

import android.system.ErrnoException
import android.system.Os
import android.util.Log
import java.io.File

/**
 * File-mode helpers used after extracting executables from assets: the engine
 * loader, core binary, and shell bridges must be readable and executable.
 *
 * Ported 1:1 from decompiled `com/agy/util/FileModeUtil.java`.
 */
object FileModeUtil {
    private const val TAG = "FileModeUtil"

    @JvmStatic
    fun recursiveSetReadableExecutable(file: File) {
        val children = file.listFiles()
        if (children == null) {
            Log.w(TAG, "makeBinExecutable: cannot list $file")
            return
        }
        for (child in children) {
            if (child.isDirectory) {
                recursiveSetReadableExecutable(child)
            } else {
                child.setReadable(true, false)
                if (!child.setExecutable(true, false)) {
                    Log.w(TAG, "setExecutable failed: $child")
                }
            }
        }
    }

    @JvmStatic
    fun setExec(file: File?) {
        if (file == null || !file.exists()) return
        try {
            Os.chmod(file.absolutePath, 493)
        } catch (e: ErrnoException) {
            Log.w(TAG, "chmod failed $file: $e")
        }
        file.setReadable(true, false)
        if (!file.setExecutable(true, false)) {
            Log.w(TAG, "setExecutable failed: $file")
        }
    }

    @JvmStatic
    fun recursiveSetExec(file: File) {
        val children = file.listFiles()
        if (children == null) {
            Log.w(TAG, "cannot list $file")
            return
        }
        for (child in children) {
            if (child.isDirectory) {
                recursiveSetExec(child)
            } else {
                setExec(child)
            }
        }
    }

    @JvmStatic
    fun applyMode(file: File, mode: Long) {
        try {
            Os.chmod(file.absolutePath, (511 and mode.toInt()))
        } catch (e: ErrnoException) {
            Log.w(TAG, "chmod failed $file: $e")
        }
        val userRead = (mode and 256L) != 0L
        val userWrite = (mode and 128L) != 0L
        val userExecute = if ((mode and 64L) == 0L && !file.isDirectory) false else true
        file.setReadable(userRead, false)
        file.setWritable(userWrite, false)
        file.setExecutable(userExecute, false)
    }

    @JvmStatic
    fun copyPermissions(from: File, to: File) {
        try {
            Os.chmod(to.absolutePath, Os.stat(from.absolutePath).st_mode.toInt() and 511)
        } catch (e: ErrnoException) {
            Log.w(TAG, "cannot stat/chmod mode between $from and $to: $e")
        }
    }
}
