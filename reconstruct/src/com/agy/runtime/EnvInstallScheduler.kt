package com.agy.runtime

import android.util.Log
import com.agy.install.EnvBundleManifest
import com.agy.install.EnvInstaller
import com.agy.install.SeedPackageInstaller
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs the bionic env bootstrap once per process on a daemon thread, then
 * refreshes the runtime/bin shell bridges.
 *
 * Ported from decompiled `com/agy/runtime/EnvInstallScheduler.java`. The
 * decompile duplicated the install/log statements as a control-flow artifact;
 * the reconstructed semantics are: bridge rewrite is forced when either the
 * env extraction or the seed-package pass did work, else the marker-based
 * check decides.
 */
object EnvInstallScheduler {
    private const val TAG = "EnvInstallScheduler"

    @JvmStatic
    fun schedule(runtimeManager: RuntimeManager, started: AtomicBoolean) {
        if (!started.compareAndSet(false, true)) {
            Log.i(TAG, "env install already started; skipping duplicate")
            return
        }
        val thread = Thread {
            try {
                val install = EnvInstaller.install(runtimeManager.appContext())
                val manifest = EnvBundleManifest.load(runtimeManager.appContext().assets)
                val installDefaults = SeedPackageInstaller.installDefaults(
                    runtimeManager.appContext(),
                    EnvInstaller.prefix(runtimeManager.appContext()),
                    manifest.seedPackages,
                )
                ShellBridgeInstaller.install(runtimeManager, install || installDefaults)
                Log.i(
                    TAG,
                    "env ready; bundle=" + manifest.bundleId + " seeded=" + installDefaults + "; runtime/bin bridges refreshed"
                )
            } catch (t: Throwable) {
                started.set(false)
                Log.e(
                    TAG,
                    "env install failed; core continues without bionic tools: " +
                        (t.message ?: t.javaClass.simpleName),
                    t,
                )
            }
        }
        thread.name = "EnvInstallThread"
        thread.isDaemon = true
        thread.start()
    }
}
