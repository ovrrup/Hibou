package com.agy.install

import android.content.Context
import java.io.File
import java.util.regex.Pattern

/**
 * Paths and shebang-fixing regexes for the bionic env prefix (`files/usr`).
 *
 * Ported 1:1 from decompiled `com/agy/install/EnvPaths.java`.
 */
object EnvPaths {
    const val ANDROID_PREFIX_REGEX = "/data/(?:data|user/[0-9]+)/[^/]+/files/usr"
    const val ASSET_BOOTSTRAP = "env/bootstrap/bootstrap.tar.gz"
    const val ASSET_BOOTSTRAP_PLAIN = "env/bootstrap/bootstrap.tar"
    const val ASSET_BUS = "bus"
    const val ASSET_MANIFEST = "env/manifest.json"
    const val ASSET_OVERLAY = "env/overlay"
    const val ASSET_PKG = "env/pkg"
    const val BASH_REL = "bin/bash"
    const val CHMOD = "/system/bin/chmod"
    const val FIX_PATHS_REL = "bin/agy-fix-paths"
    const val MARKER = ".installed"
    const val PREFIX_DIR = "usr"
    const val PREFIX_MARKER = "/data/"
    const val TAR = "/system/bin/tar"
    const val TERMUX_PREFIX = "/data/data/com.termux/files/usr"
    const val TOYBOX = "/system/bin/toybox"
    const val USER_DATA_DIR = ".gemini"

    val TERMUX_SHEBANG: Pattern =
        Pattern.compile("^#!(/data/(?:data|user/[0-9]+)/[^/]+/files/usr)(.*)$")
    val TERMUX_SHEBANG_LINES: Pattern =
        Pattern.compile("(?m)^#!(/data/(?:data|user/[0-9]+)/[^/]+/files/usr)([^\\n]*)$")
    val ENV_SHEBANG: Pattern =
        Pattern.compile("^#![ \\t]*/usr/bin/env[ \\t]+([^ \\t\\r\\n]+)(.*)$")
    val ENV_SHEBANG_LINES: Pattern =
        Pattern.compile("(?m)^#![ \\t]*/usr/bin/env[ \\t]+([^ \\t\\r\\n]+)([^\\n]*)$")
    val TERMUX_ANY_PREFIX: Pattern =
        Pattern.compile("/data/(?:data|user/[0-9]+)/[^/]+/files/usr(?=/|$)")

    val SCRIPT_DIRS = arrayOf("bin", "libexec", "etc", "share", "var")

    @JvmStatic
    fun prefix(context: Context): File = File(context.filesDir, PREFIX_DIR)
}
