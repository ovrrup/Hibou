package com.agy.install

import android.content.res.AssetManager
import com.agy.util.FileModeUtil
import com.agy.util.ShellQuote
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * Installs the `libexec/pkg` package manager from `assets/env/pkg` and the
 * `bin/pkg` wrapper that runs it with the prefix bash.
 *
 * Ported 1:1 from decompiled `com/agy/install/PkgInstaller.java`.
 */
object PkgInstaller {
    @JvmStatic
    fun install(assetManager: AssetManager, prefix: File) {
        install(assetManager, prefix, prefix.absolutePath)
    }

    @JvmStatic
    fun install(assetManager: AssetManager, prefix: File, displayPrefixPath: String) {
        val pkgDir = File(prefix, "libexec/pkg")
        if (!pkgDir.isDirectory && !pkgDir.mkdirs() && !pkgDir.isDirectory) {
            throw IllegalStateException("cannot create $pkgDir")
        }
        OverlayCopier.copyTree(assetManager, EnvPaths.ASSET_PKG, pkgDir)
        val pkgScript = File(pkgDir, "pkg")
        if (!pkgScript.isFile) {
            throw IllegalStateException("required asset missing: env/pkg/pkg")
        }
        FileModeUtil.setExec(pkgScript)
        writeWrapper(File(prefix, "bin/pkg"), File(displayPrefixPath))
    }

    private fun writeWrapper(target: File, prefixForScript: File) {
        val parent = target.parentFile
        if (parent != null && !parent.isDirectory && !parent.mkdirs() && !parent.isDirectory) {
            throw IllegalStateException("cannot create $parent")
        }
        val bash = File(prefixForScript, EnvPaths.BASH_REL).absolutePath
        val pkgExec = File(prefixForScript, "libexec/pkg/pkg").absolutePath
        val script = StringBuilder()
            .append('#').append('!').append(bash).append('\n')
            .append("export PREFIX=").append(ShellQuote.quote(prefixForScript.absolutePath)).append('\n')
            .append("exec ").append(ShellQuote.quote(pkgExec)).append(" \"$@\"").append('\n')
            .toString()
        try {
            val out = FileOutputStream(target)
            try {
                out.write(script.toByteArray(StandardCharsets.UTF_8))
                out.close()
                FileModeUtil.setExec(target)
            } finally {
            }
        } catch (e: IOException) {
            throw IllegalStateException("cannot write $target: " + e.message, e)
        }
    }
}
