package com.agy.install

import android.content.res.AssetManager
import android.util.Log
import com.agy.util.FileModeUtil
import java.io.File
import java.io.IOException

/**
 * Installs the `libexec/bus` event-bus scripts from `assets/bus`.
 *
 * Ported 1:1 from decompiled `com/agy/install/BusInstaller.java`.
 */
object BusInstaller {
    private const val TAG = "BusInstaller"

    @JvmStatic
    fun install(assetManager: AssetManager, prefix: File) {
        try {
            val list = assetManager.list(EnvPaths.ASSET_BUS)
            if (list == null || list.isEmpty()) {
                Log.w(TAG, "no assets/bus tree; skipping bus install")
                return
            }
            val busDir = File(prefix, "libexec/bus")
            OverlayCopier.copyTree(assetManager, EnvPaths.ASSET_BUS, busDir)
            val busScript = File(busDir, "bus.sh")
            if (busScript.isFile) {
                FileModeUtil.setExec(busScript)
            } else {
                Log.w(TAG, "bus.sh missing after install: $busScript")
            }
            Log.i(TAG, "bus installed: $busDir")
        } catch (e: IOException) {
            Log.w(TAG, "cannot list assets/bus: $e")
        }
    }
}
