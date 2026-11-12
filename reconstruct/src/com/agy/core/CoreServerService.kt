package com.agy.core

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.agy.gateway.SystemGatewayServer
import com.agy.log.LogRingBuffer
import com.agy.runtime.RuntimeManager
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Foreground service that owns the whole engine stack: system gateway on port
 * 48258, the supervised core process on 48255, URL watching, wakelock and the
 * persistent notification.
 *
 * Ported 1:1 from decompiled `com/agy/core/CoreServerService.java`, including
 * the `agy-daemon` background companion (started 1500ms after create, stopped
 * with a 1500ms wait on destroy).
 */
class CoreServerService : Service() {
    private var destroyed = false
    private var gatewayServer: SystemGatewayServer? = null
    private var logBuffer: LogRingBuffer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var openUrlWatcher: OpenUrlWatcher? = null
    private var runtime: RuntimeManager? = null
    private var supervisor: CoreProcessSupervisor? = null
    private var wakeLock: WakeLockHolder? = null

    override fun onCreate() {
        super.onCreate()
        val runtimeManager = RuntimeManager(this)
        runtime = runtimeManager
        val ringBuffer = LogRingBuffer()
        logBuffer = ringBuffer
        val gateway = SystemGatewayServer(this, ringBuffer, 48258)
        gatewayServer = gateway
        gateway.start()
        Log.i(
            TAG,
            "state paths: HOME=" + filesDir.absolutePath +
                " appData=" + runtimeManager.getAppDataDir().absolutePath +
                " projects=" + runtimeManager.getProjectsDir().absolutePath +
                " runtime=" + runtimeManager.getRuntimeDir().absolutePath
        )
        CoreRuntimeState.httpsPort = runtimeManager.getHttpsPort()
        CoreRuntimeState.csrfToken = runtimeManager.getCsrfToken()
        CoreRuntimeState.restarting = false
        CoreRuntimeState.lastError = null
        CoreRuntimeState.serverUrl = null
        ForegroundNotification.start(this)
        val lock = WakeLockHolder()
        wakeLock = lock
        lock.acquire(this)
        val watcher = OpenUrlWatcher(this)
        openUrlWatcher = watcher
        watcher.start()
        val supervisedSupervisor = CoreProcessSupervisor(
            this,
            runtimeManager,
            watcher,
            ringBuffer,
        ) { onSupervisorTerminated() }
        supervisor = supervisedSupervisor
        supervisedSupervisor.start()
        startBackgroundDaemon()
    }

    private fun startBackgroundDaemon() {
        Thread({
            try {
                Thread.sleep(1500L)
                val daemon = File(filesDir, "usr/bin/agy-daemon")
                if (daemon.canExecute()) {
                    Log.i(TAG, "Starting autonomous background daemon...")
                    val processBuilder = ProcessBuilder(daemon.absolutePath, "start")
                    processBuilder.directory(filesDir)
                    processBuilder.environment()["PATH"] =
                        filesDir.absolutePath + "/usr/bin:" + System.getenv("PATH")
                    processBuilder.environment()["HOME"] = filesDir.absolutePath
                    processBuilder.start()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start agy-daemon: " + e.message)
            }
        }, "AgyDaemonStarter").start()
    }

    private fun stopBackgroundDaemon() {
        try {
            val daemon = File(filesDir, "usr/bin/agy-daemon")
            if (daemon.canExecute()) {
                Log.i(TAG, "Stopping autonomous background daemon...")
                val processBuilder = ProcessBuilder(daemon.absolutePath, "stop")
                processBuilder.directory(filesDir)
                processBuilder.environment()["PATH"] =
                    filesDir.absolutePath + "/usr/bin:" + System.getenv("PATH")
                processBuilder.environment()["HOME"] = filesDir.absolutePath
                val process = processBuilder.start()
                if (!process.waitFor(1500L, TimeUnit.MILLISECONDS)) {
                    Log.w(TAG, "agy-daemon stop timed out after 1500ms; destroying forcibly")
                    process.destroyForcibly()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to stop agy-daemon: " + e.message)
        }
    }

    private fun onSupervisorTerminated() {
        mainHandler.post {
            if (destroyed) return@post
            CoreRuntimeState.serverUrl = null
            CoreRuntimeState.restarting = false
            if (CoreRuntimeState.lastError == null) {
                CoreRuntimeState.lastError = "core supervisor stopped unexpectedly"
            }
            wakeLock?.release()
            runtime?.stopNetworkMonitor()
            openUrlWatcher?.stop()
            ForegroundNotification.update(this, "Core stopped — open app to retry")
        }
    }

    override fun onDestroy() {
        stopBackgroundDaemon()
        gatewayServer?.stop()
        destroyed = true
        mainHandler.removeCallbacksAndMessages(null)
        supervisor?.stop()
        openUrlWatcher?.stop()
        wakeLock?.release()
        runtime?.stopNetworkMonitor()
        CoreRuntimeState.serverUrl = null
        CoreRuntimeState.lastError = null
        CoreRuntimeState.restarting = false
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private companion object {
        const val TAG = "CoreServer"
    }
}
