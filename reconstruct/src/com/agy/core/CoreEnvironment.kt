package com.agy.core

import android.util.Log
import com.agy.install.EnvPaths
import com.agy.ru.AgyEnv
import com.agy.ru.HibouConnect
import com.agy.runtime.RuntimeManager
import java.io.File

/**
 * Builds the process environment for the core engine (HOME, certs, shell,
 * locale, IDE identity overrides).
 *
 * Ported 1:1 from decompiled `com/agy/core/CoreEnvironment.java`. The
 * `ANTIGRAVITY_VSCODE_HOST` value is an engine handshake contract and stays
 * tied to the Connect protocol version despite the app rebrand.
 */
object CoreEnvironment {
    private const val TAG = "CoreEnvironment"

    @JvmStatic
    fun apply(runtimeManager: RuntimeManager, processBuilder: ProcessBuilder) {
        AgyEnv.apply(processBuilder)
        val filesDir = runtimeManager.appContext().filesDir
        val binDir = runtimeManager.getBinDir()
        val env = processBuilder.environment()
        env["HOME"] = filesDir.absolutePath
        env["TMPDIR"] = runtimeManager.appContext().cacheDir.absolutePath
        Log.i(
            TAG,
            "core env: HOME=" + filesDir.absolutePath +
                " appDataDir=" + runtimeManager.getAppDataDir().absolutePath +
                " projectsDir=" + runtimeManager.getProjectsDir().absolutePath +
                " TMPDIR=" + runtimeManager.appContext().cacheDir.absolutePath
        )
        env["SSL_CERT_FILE"] = runtimeManager.getCertFile().absolutePath
        val existingPath = env["PATH"]
        env["PATH"] = binDir.absolutePath + (if (existingPath.isNullOrEmpty()) "" else ":$existingPath")

        val prefix = File(filesDir, EnvPaths.PREFIX_DIR)
        val prefixBash = File(prefix, EnvPaths.BASH_REL)
        val binBash = File(binDir, "bash")
        when {
            prefixBash.isFile && prefixBash.canExecute() -> env["SHELL"] = prefixBash.absolutePath
            binBash.isFile && binBash.canExecute() -> env["SHELL"] = binBash.absolutePath
            else -> env["SHELL"] = "/system/bin/sh"
        }
        env["TERM"] = "xterm-256color"
        val terminfo = File(prefix, "share/terminfo")
        if (terminfo.isDirectory) {
            env["TERMINFO"] = terminfo.absolutePath
        }
        env["LANG"] = "C.UTF-8"
        env["LC_ALL"] = "C.UTF-8"
        val godebug = env["GODEBUG"]
        env["GODEBUG"] = if (godebug.isNullOrEmpty()) "netdns=go" else "$godebug,netdns=go"
        env["ANTIGRAVITY_VSCODE_HOST"] = HibouConnect.PROTOCOL_VERSION
        env["BROWSER"] = File(binDir, "xdg-open").absolutePath
        env["DISPLAY"] = ":0"
        env["WAYLAND_DISPLAY"] = "wayland-0"
        env["XDG_CURRENT_DESKTOP"] = "X-Generic"
        env["XDG_DATA_DIRS"] = runtimeManager.getRuntimeDir().absolutePath
        env["XDG_DATA_HOME"] = File(filesDir, ".local/share").absolutePath
        env["XDG_CONFIG_HOME"] = File(filesDir, ".config").absolutePath
        env.remove("LD_LIBRARY_PATH")
        env.remove("LD_PRELOAD")
    }
}
