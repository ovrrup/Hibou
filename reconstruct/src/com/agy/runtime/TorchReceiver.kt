package com.agy.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log

/**
 * Receives `com.agy.ACTION_TORCH` broadcasts (sent by the `torch` shell shims)
 * and toggles the camera flash.
 *
 * Ported from decompiled `com/agy/runtime/TorchReceiver.java`, whose body the
 * decompiler could not fully reconstruct. The recovered semantics: `enabled`
 * defaults to true; a `state` extra with `off`/`0`/`false` (any case) forces
 * off, `on`/`1`/`true` forces on, and any other value leaves the default.
 */
class TorchReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        if (intent.action != "com.agy.ACTION_TORCH") return
        var enabled = intent.getBooleanExtra("enabled", true)
        if (intent.hasExtra("state")) {
            val state = intent.getStringExtra("state")
            enabled = when {
                "off".equals(state, ignoreCase = true) -> false
                "0" == state -> false
                "false".equals(state, ignoreCase = true) -> false
                "on".equals(state, ignoreCase = true) -> true
                "1" == state -> true
                "true".equals(state, ignoreCase = true) -> true
                else -> enabled
            }
        }
        setTorchMode(context, enabled)
    }

    companion object {
        private const val TAG = "TorchReceiver"

        @JvmStatic
        fun setTorchMode(context: Context?, enabled: Boolean) {
            if (context == null) return
            try {
                val cameraManager = context.getSystemService("camera") as? CameraManager
                if (cameraManager != null) {
                    for (id in cameraManager.cameraIdList) {
                        try {
                            val characteristics = cameraManager.getCameraCharacteristics(id)
                            val flashAvailable =
                                characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) as? Boolean
                            val facing = characteristics.get(CameraCharacteristics.LENS_FACING) as? Int
                            if (flashAvailable == true) {
                                cameraManager.setTorchMode(id, enabled)
                                Log.i(TAG, "setTorchMode($id, $enabled) succeeded (facing=$facing)")
                                return
                            }
                        } catch (t: Throwable) {
                            Log.d(TAG, "Camera $id check/set failed: " + t.message)
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "failed to toggle torch: " + t.message)
            }
        }
    }
}
