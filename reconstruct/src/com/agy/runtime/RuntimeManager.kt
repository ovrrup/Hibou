package com.agy.runtime

import android.content.Context
import android.util.Log
import com.agy.core.CoreEnvironment
import com.agy.core.CoreRuntimeState
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Facade over the runtime extraction: paths, ports, the CSRF token, the core
 * command line, and the network monitor.
 *
 * Ported 1:1 from decompiled `com/agy/runtime/RuntimeManager.java`. The
 * engine argv (`--override_ide_name antigravity` etc.) is an API contract with
 * the language server and stays unchanged despite the app rebrand.
 */
class RuntimeManager(context: Context) {
    private val appContext: Context = context.applicationContext
    private val paths: RuntimePaths = RuntimePaths(context)
    private val network: NetworkMonitorHolder = NetworkMonitorHolder(context)
    private val envInstallStarted = AtomicBoolean(false)
    private val csrfToken: String = UUID.randomUUID().toString()

    fun appContext(): Context = appContext

    fun getHttpsPort(): Int = RuntimeConfig.httpsPort(appContext)

    fun getCsrfToken(): String = csrfToken

    fun getRuntimeDir(): File = paths.getRuntimeDir()

    fun getWebDir(): File = paths.getWebDir()

    fun getBinDir(): File = paths.getBinDir()

    fun getLibDir(): File = paths.getLibDir()

    fun getCertFile(): File = paths.getCertFile()

    fun getGeminiDir(): File = paths.getGeminiDir()

    fun getAppDataDir(): File = paths.getAppDataDir()

    fun getProjectsDir(): File = paths.getProjectsDir()

    @Synchronized
    fun installCore() {
        CoreInstaller.installCore(this)
    }

    fun installEnvInBackground() {
        EnvInstallScheduler.schedule(this, envInstallStarted)
    }

    fun coreCommand(): Array<String> {
        val binDir = getBinDir()
        val libDir = getLibDir()
        val loader = File(binDir, RuntimePaths.LOADER_NAME)
        val core = File(binDir, RuntimePaths.CORE_NAME)
        val compat = File(libDir, "liblse_compat.so")
        if (!loader.isFile) {
            throw IllegalStateException("glibc loader missing: $loader")
        }
        if (!core.isFile) {
            throw IllegalStateException("core binary missing: $core")
        }
        val argv = ArrayList<String>()
        argv.add(loader.absolutePath)
        argv.add("--library-path")
        argv.add(libDir.absolutePath)
        if (compat.isFile) {
            argv.add("--preload")
            argv.add(compat.absolutePath)
        }
        argv.add(core.absolutePath)
        argv.add("--standalone")
        argv.add("--override_ide_name")
        argv.add("antigravity")
        argv.add("--subclient_type")
        argv.add("hub")
        argv.add("--override_ide_version")
        argv.add("2.11.0")
        argv.add("--override_user_agent_name")
        argv.add("antigravity")
        argv.add("--https_server_port")
        argv.add(getHttpsPort().toString())
        argv.add("--csrf_token")
        argv.add(csrfToken)
        argv.add("--app_data_dir")
        argv.add(RuntimePaths.APP_DATA_DIR_NAME)
        argv.add("--web_bundle_path")
        argv.add(getWebDir().absolutePath)
        argv.add("--enable_sidecars")
        return argv.toTypedArray()
    }

    fun applyEnvironment(processBuilder: ProcessBuilder) {
        CoreEnvironment.apply(this, processBuilder)
    }

    fun startNetworkMonitor() {
        network.start()
    }

    fun stopNetworkMonitor() {
        network.unregister()
    }

    @Synchronized
    fun refreshNetwork() {
        writeNetworkConfig()
        if (CoreRuntimeState.serverUrl != null) {
            Log.d("RuntimeManager", "network changed; rewrote DNS config (core stays up)")
        } else {
            Log.d("RuntimeManager", "network changed; DNS config refreshed")
        }
    }

    @Synchronized
    fun writeNetworkConfig() {
        NetworkConfigWriter.write(appContext)
    }

    companion object {
        const val CORE_VERSION = "2.11.0"
    }
}
