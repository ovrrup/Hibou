package com.agy.runtime

import android.content.Context
import android.system.Os
import android.util.Log
import com.agy.install.EnvInstaller
import com.agy.install.EnvPaths
import com.agy.util.ShellQuote
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.Charset

/**
 * Generates the `runtime/bin` shell bridge scripts: fallback `bash`/`sh`,
 * URL-opener shims (`xdg-open` etc.), torch shims, and per-command bridges
 * into the bionic prefix.
 *
 * Ported from decompiled `com/agy/runtime/ShellBridgeInstaller.java`. The JNA
 * constant used for the marker read buffer size (`Function.MAX_NARGS`) is
 * inlined as its literal value 256, so no JNA dependency is needed.
 */
object ShellBridgeInstaller {
    private const val BRIDGE_FORMAT = 2
    private const val BRIDGE_MARKER = ".agy-bridges"
    private val OPEN_URL_SHIMS = arrayOf(
        "xdg-open", "open", "open_helper", "sensible-browser", "x-www-browser",
        "gnome-open", "google-chrome", "chrome", "chromium",
    )
    private const val TAG = "ShellBridgeInstaller"

    @JvmStatic
    @Synchronized
    fun installFallbackBridges(binDir: File) {
        installFallbackBridges(binDir, null)
    }

    @JvmStatic
    @Synchronized
    fun installFallbackBridges(binDir: File, context: Context?) {
        if (binDir == null) return
        if (!binDir.isDirectory && !binDir.mkdirs() && !binDir.isDirectory) {
            Log.w(TAG, "cannot create $binDir; skipping fallback bridges")
            return
        }
        val bash = File(binDir, "bash")
        if (!bash.isFile) {
            writeScript(bash, "#!/system/bin/sh\n# AGY fallback shell\nexec /system/bin/sh \"\$@\"\n")
        }
        val sh = File(binDir, "sh")
        if (!sh.isFile) {
            writeScript(sh, "#!/system/bin/sh\n# AGY fallback shell\nexec /system/bin/sh \"\$@\"\n")
        }
        writeTorchShims(binDir, context)
    }

    @JvmStatic
    @Synchronized
    fun writeTorchShims(binDir: File, context: Context?) {
        val script = "#!/system/bin/sh\n" +
            "# AGY Torch / Flashlight shim\n" +
            "STATE=\"\$1\"\n" +
            "[ -z \"\$STATE\" ] && STATE=\"on\"\n" +
            "ENABLE=\"true\"\n" +
            "if [ \"\$STATE\" = \"off\" ] || [ \"\$STATE\" = \"0\" ] || [ \"\$STATE\" = \"false\" ]; then\n" +
            "  ENABLE=\"false\"\n" +
            "fi\n" +
            "if [ -x /system/bin/cmd ]; then\n" +
            "  /system/bin/cmd camera set-torch-mode 0 \"\$STATE\" >/dev/null 2>&1 || true\n" +
            "fi\n" +
            "if [ -x /system/bin/am ]; then\n" +
            "  /system/bin/am broadcast -a com.agy.ACTION_TORCH -p " +
            (context?.packageName ?: "agy.hibou") +
            " --ez enabled \"\$ENABLE\" --es state \"\$STATE\" >/dev/null 2>&1 || true\n" +
            "fi\n" +
            "echo \"Flashlight set to \$STATE\"\n" +
            "exit 0\n"
        writeScript(File(binDir, "torch"), script)
        writeScript(File(binDir, "termux-torch"), script)
        writeScript(File(binDir, "termux-camera-torch"), script)
    }

    @JvmStatic
    @Synchronized
    fun installOpenUrlShims(binDir: File) {
        installOpenUrlShims(binDir, null)
    }

    @JvmStatic
    @Synchronized
    fun installOpenUrlShims(binDir: File, context: Context?) {
        if (binDir == null) return
        if (!binDir.isDirectory && !binDir.mkdirs() && !binDir.isDirectory) {
            Log.w(TAG, "cannot create $binDir; skipping open-url shims")
            return
        }
        for (shim in OPEN_URL_SHIMS) {
            writeOpenUrlShim(File(binDir, shim), context)
        }
    }

    @JvmStatic
    @Synchronized
    fun install(runtimeManager: RuntimeManager) {
        install(runtimeManager, false)
    }

    @JvmStatic
    @Synchronized
    fun install(runtimeManager: RuntimeManager, force: Boolean) {
        val binDir = runtimeManager.getBinDir()
        if (!binDir.isDirectory && !binDir.mkdirs() && !binDir.isDirectory) {
            Log.w(TAG, "cannot create $binDir; skipping shell bridges")
            return
        }
        installFallbackBridges(binDir, runtimeManager.appContext())
        installOpenUrlShims(binDir, runtimeManager.appContext())
        val prefix = EnvInstaller.prefix(runtimeManager.appContext())
        val prefixBash = File(prefix, EnvPaths.BASH_REL)
        if (!prefixBash.isFile) {
            Log.w(TAG, "prefix bash missing ($prefixBash); keeping fallback shell bridges")
            return
        }
        val packageName = runtimeManager.appContext().packageName
        writeBridge(File(binDir, "bash"), prefix, "bash", packageName)
        writeBridge(File(binDir, "sh"), prefix, "bash", packageName)
        val prefixBin = File(prefix, "bin").listFiles()
        val count = countFiles(prefixBin)
        val marker = File(binDir, BRIDGE_MARKER)
        if (force || !markerMatches(marker, prefix, count)) {
            if (prefixBin != null) {
                for (command in prefixBin) {
                    if (command.isFile) {
                        val name = command.name
                        if (name != "rish" && !isOpenUrlShim(name)) {
                            writeBridge(File(binDir, name), prefix, name, packageName)
                        }
                    }
                }
            }
            writeMarkerFile(marker, prefix, count)
        }
    }

    private fun countFiles(files: Array<File>?): Int {
        if (files == null) return 0
        var count = 0
        for (file in files) {
            if (file.isFile) count++
        }
        return count
    }

    private fun markerMatches(marker: File, prefix: File, count: Int): Boolean {
        if (!marker.isFile) return false
        return try {
            val input = FileInputStream(marker)
            try {
                val buffer = ByteArray(256) // was com.sun.jna.Function.MAX_NARGS
                val read = input.read(buffer)
                val content = if (read > 0) String(buffer, 0, read, Charset.forName("UTF-8")) else ""
                content == markerContent(prefix, count)
            } finally {
            }
        } catch (_: IOException) {
            false
        }
    }

    private fun writeMarkerFile(marker: File, prefix: File, count: Int) {
        try {
            val out = FileOutputStream(marker)
            try {
                out.write(markerContent(prefix, count).toByteArray(Charset.forName("UTF-8")))
                out.close()
            } finally {
            }
        } catch (e: IOException) {
            Log.w(TAG, "cannot write bridge marker $marker: " + e.message)
        }
    }

    private fun markerContent(prefix: File, count: Int): String {
        return "$BRIDGE_FORMAT\n" + prefix.absolutePath + "\n" + count + "\n"
    }

    private fun isOpenUrlShim(name: String): Boolean {
        for (shim in OPEN_URL_SHIMS) {
            if (shim == name) return true
        }
        return false
    }

    private fun writeOpenUrlShim(target: File, context: Context?) {
        val packageName = context?.packageName ?: "agy.hibou"
        val sb = StringBuilder()
        sb.append("#!/system/bin/sh\n")
        sb.append("# AGY URL opener shim (xdg-open / open / sensible-browser).\n")
        sb.append("unset LD_PRELOAD\n")
        sb.append("unset LD_LIBRARY_PATH\n")
        sb.append("URL=\"\$1\"\n")
        sb.append("[ -z \"\$URL\" ] && URL=\"\$2\"\n")
        sb.append("[ -z \"\$URL\" ] && exit 0\n")
        sb.append("echo \"AGY_OPEN_URL=\$URL\"\n")
        sb.append("echo \"ANTIGRAVITY_OPEN_URL: \$URL\"\n")
        sb.append("if [ -x /system/bin/am ]; then\n")
        sb.append("  /system/bin/am broadcast -a com.agy.ACTION_OPEN_URL -p ").append(packageName).append(" --es url \"\$URL\" >/dev/null 2>&1 || true\n")
        sb.append("fi\n")
        sb.append("exit 0\n")
        writeScript(target, sb.toString())
    }

    private fun writeBridge(target: File, prefix: File, command: String, packageName: String) {
        val quotedPrefix = ShellQuote.quote(prefix.absolutePath)
        val sb = StringBuilder()
        sb.append("#!/system/bin/sh\n")
        sb.append("# AGY bridge -> bionic \$PREFIX/bin/").append(command).append('\n')
        sb.append("PREFIX=").append(quotedPrefix).append('\n')
        sb.append("AGY_APPLICATION_ID=").append(ShellQuote.quote(packageName)).append('\n')
        sb.append("export PREFIX AGY_APPLICATION_ID\n")
        sb.append("if [ -f \"\$PREFIX/lib/libtermux-exec.so\" ]; then\n")
        sb.append("  export LD_PRELOAD=\"\$PREFIX/lib/libtermux-exec.so\"\n")
        sb.append("else\n")
        sb.append("  unset LD_PRELOAD\n")
        sb.append("fi\n")
        sb.append("export LD_LIBRARY_PATH=\"\$PREFIX/lib\"\n")
        sb.append("export PATH=\"\$PREFIX/bin:/system/bin\"\n")
        sb.append("export OPENSSL_CONF=\"\$PREFIX/etc/tls/openssl.cnf\"\n")
        sb.append("export SSL_CERT_FILE=\"\$PREFIX/etc/tls/cert.pem\"\n")
        sb.append("export CURL_CA_BUNDLE=\"\$PREFIX/etc/tls/cert.pem\"\n")
        sb.append("export GIT_CONFIG_NOSYSTEM=1\n")
        sb.append("export GIT_ATTR_NOSYSTEM=1\n")
        sb.append("export GIT_TEMPLATE_DIR=\"\$PREFIX/share/git-core/templates\"\n")
        sb.append("export GIT_EXEC_PATH=\"\$PREFIX/libexec/git-core\"\n")
        sb.append("export PAGER=\"cat\"\n")
        sb.append("if [ -x \"\$PREFIX/bin/").append(command).append("\" ]; then\n")
        sb.append("  exec \"\$PREFIX/bin/").append(command).append("\" \"\$@\"\n")
        sb.append("else\n")
        sb.append("  exec /system/bin/sh \"\$@\"\n")
        sb.append("fi\n")
        writeScript(target, sb.toString())
    }

    private fun writeScript(target: File, content: String) {
        try {
            val out = FileOutputStream(target)
            try {
                out.write(content.toByteArray(Charset.forName("UTF-8")))
                out.close()
                target.setReadable(true, false)
                target.setExecutable(true, false)
                try {
                    Os.chmod(target.absolutePath, 493)
                } catch (_: Throwable) {
                }
            } finally {
            }
        } catch (e: IOException) {
            Log.w(TAG, "cannot write bridge $target: " + e.message)
        }
    }
}
