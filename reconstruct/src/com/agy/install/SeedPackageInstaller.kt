package com.agy.install

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import com.agy.util.AtomicFileWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Properties
import java.util.concurrent.TimeUnit

/**
 * Installs the env bundle's default seed packages through `bin/pkg`, with a
 * 6h retry back-off, a 20-minute command timeout and per-package state in
 * `.agy-env-seeds.properties` (all diagnostics appended to
 * `env-seed-install.log`).
 *
 * Ported 1:1 from decompiled `com/agy/install/SeedPackageInstaller.java`.
 */
object SeedPackageInstaller {
    private const val COMMAND_TIMEOUT_MINUTES = 20L
    private val RETRY_BACKOFF_MS = TimeUnit.HOURS.toMillis(6)
    private const val STATE_NAME = ".agy-env-seeds.properties"
    private const val TAG = "SeedPackageInstaller"

    @JvmStatic
    fun installDefaults(context: Context, prefix: File, packages: List<String>?): Boolean {
        if (packages == null || packages.isEmpty()) {
            return false
        }
        val stateFile = File(context.filesDir, STATE_NAME)
        val state = readState(stateFile)
        val logFile = File(context.filesDir, "env-seed-install.log")
        if (logFile.isFile && logFile.length() > 1048576) {
            logFile.delete()
        }
        var ranUpdate = false
        var installedAny = false
        for (pkg in packages) {
            if (isPackageAvailable(context, prefix, pkg, logFile)) {
                state.setProperty("$pkg.status", "ready")
            } else {
                val lastAttempt = parseLong(state.getProperty("$pkg.lastAttempt"))
                val now = System.currentTimeMillis()
                if (lastAttempt > 0 && now - lastAttempt < RETRY_BACKOFF_MS) {
                    Log.i(TAG, "$pkg install deferred by retry backoff")
                } else if (!hasNetwork(context)) {
                    state.setProperty("$pkg.status", "pending-network")
                    writeState(stateFile, state)
                    Log.i(TAG, "$pkg is a default seed; waiting for a network connection")
                } else {
                    if (!ranUpdate) {
                        if (!runPkg(context, prefix, "update")) {
                            Log.w(TAG, "pkg update failed; aborting seed package installation")
                            break
                        }
                        ranUpdate = true
                    }
                    state.setProperty("$pkg.lastAttempt", now.toString())
                    state.setProperty("$pkg.status", "installing")
                    writeState(stateFile, state)
                    if (runPkg(context, prefix, "install", pkg) && isPackageAvailable(context, prefix, pkg, logFile)) {
                        state.setProperty("$pkg.status", "ready")
                        state.remove("$pkg.lastAttempt")
                        Log.i(TAG, "default package installed: $pkg")
                        installedAny = true
                    } else {
                        state.setProperty("$pkg.status", "pending-retry")
                        Log.w(TAG, "default package not ready: $pkg; see $logFile")
                    }
                    writeState(stateFile, state)
                }
            }
        }
        return installedAny
    }

    private fun runPkg(context: Context, prefix: File, vararg args: String): Boolean {
        val pkgWrapper = File(prefix, "bin/pkg")
        if (!pkgWrapper.isFile) {
            Log.w(TAG, "pkg wrapper missing: $pkgWrapper")
            return false
        }
        val argv = ArrayList<String>()
        argv.add(pkgWrapper.absolutePath)
        for (arg in args) {
            argv.add(arg)
        }
        val logFile = File(context.filesDir, "env-seed-install.log")
        return try {
            val home = File(context.filesDir, "home")
            val tmp = File(prefix, "tmp")
            home.mkdirs()
            tmp.mkdirs()
            logFile.parentFile?.mkdirs()
            val processBuilder = ProcessBuilder(argv)
            processBuilder.directory(context.filesDir)
            processBuilder.redirectErrorStream(true)
            processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
            val env = processBuilder.environment()
            env["PREFIX"] = prefix.absolutePath
            env["HOME"] = home.absolutePath
            env["TMPDIR"] = tmp.absolutePath
            env["PATH"] = File(prefix, "bin").absolutePath + ":/system/bin"
            env["LD_LIBRARY_PATH"] = File(prefix, "lib").absolutePath
            val preload = File(prefix, "lib/libtermux-exec.so")
            if (preload.isFile) {
                env["LD_PRELOAD"] = preload.absolutePath
            }
            env["SSL_CERT_FILE"] = File(prefix, "etc/tls/cert.pem").absolutePath
            env["CURL_CA_BUNDLE"] = File(prefix, "etc/tls/cert.pem").absolutePath
            env["OPENSSL_CONF"] = File(prefix, "etc/tls/openssl.cnf").absolutePath
            env["GIT_SSL_CAINFO"] = File(prefix, "etc/tls/cert.pem").absolutePath
            env["AGY_APPLICATION_ID"] = context.packageName
            env["LANG"] = "C.UTF-8"
            val process = processBuilder.start()
            if (!process.waitFor(COMMAND_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                process.destroyForcibly()
                appendLog(logFile, "\n[AGY] timed out running: $argv\n")
                return false
            }
            val exitCode = process.exitValue()
            if (exitCode != 0) {
                appendLog(logFile, "\n[AGY] exit=$exitCode command=$argv\n")
            }
            exitCode == 0
        } catch (e: Exception) {
            appendLog(logFile, "\n[AGY] failed command=$argv error=$e\n")
            Log.w(TAG, "cannot run pkg $argv: " + e.message)
            false
        }
    }

    private fun isPackageAvailable(context: Context, prefix: File, name: String, logFile: File): Boolean {
        var pkg = name
        if (pkg == "python") {
            pkg = "python3"
        }
        val binary = File(prefix, "bin/$pkg")
        if (!binary.isFile) {
            return false
        }
        return try {
            val processBuilder = ProcessBuilder(binary.absolutePath, "--version")
            processBuilder.directory(context.filesDir)
            processBuilder.redirectErrorStream(true)
            processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
            val env = processBuilder.environment()
            env["PREFIX"] = prefix.absolutePath
            env["HOME"] = File(context.filesDir, "home").absolutePath
            env["PATH"] = File(prefix, "bin").absolutePath + ":/system/bin"
            env["LD_LIBRARY_PATH"] = File(prefix, "lib").absolutePath
            val preload = File(prefix, "lib/libtermux-exec.so")
            if (preload.isFile) {
                env["LD_PRELOAD"] = preload.absolutePath
            }
            val process = processBuilder.start()
            if (process.waitFor(10L, TimeUnit.SECONDS)) {
                process.exitValue() == 0
            } else {
                process.destroyForcibly()
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun hasNetwork(context: Context): Boolean {
        return try {
            val connectivityManager =
                context.getSystemService("connectivity") as? ConnectivityManager
            val info = connectivityManager?.activeNetworkInfo
            info?.isConnected == true
        } catch (e: Exception) {
            Log.w(TAG, "cannot check network state: " + e.message)
            false
        }
    }

    private fun readState(file: File): Properties {
        val properties = Properties()
        if (!file.isFile) {
            return properties
        }
        try {
            val input = FileInputStream(file)
            try {
                properties.load(input)
                input.close()
            } finally {
            }
        } catch (e: IOException) {
            Log.w(TAG, "cannot read seed state: " + e.message)
        }
        return properties
    }

    private fun writeState(file: File, properties: Properties) {
        val sb = StringBuilder()
        for (name in properties.stringPropertyNames()) {
            sb.append(name).append('=').append(properties.getProperty(name)).append('\n')
        }
        AtomicFileWriter.writeText(file, sb.toString())
    }

    private fun parseLong(raw: String?): Long {
        if (raw == null) return 0L
        return try {
            raw.toLong()
        } catch (_: NumberFormatException) {
            0L
        }
    }

    private fun appendLog(file: File, text: String) {
        try {
            val out = FileOutputStream(file, true)
            try {
                out.write(text.toByteArray(StandardCharsets.UTF_8))
                out.close()
            } finally {
            }
        } catch (_: IOException) {
        }
    }
}
