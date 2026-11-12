package com.agy.runtime

import android.content.Context
import android.util.Log
import com.agy.util.AtomicFileWriter
import com.agy.util.IoUtil
import java.io.File
import java.io.InputStream

/**
 * Seeds onboarding state files (`jetski_state.pbtxt`, `antigravity_state.pbtxt`,
 * `AGENTS.md`, projects dir) from `assets/runtime/seed/` when missing.
 *
 * Ported 1:1 from decompiled `com/agy/runtime/OnboardingSeeder.java`.
 */
object OnboardingSeeder {
    private const val TAG = "OnboardingSeeder"

    @JvmStatic
    fun seed(context: Context, appDataDir: File, projectsDir: File) {
        try {
            if (!appDataDir.exists() && !appDataDir.mkdirs()) {
                Log.w(TAG, "cannot create $appDataDir")
                return
            }
            seedStateFile(context, "jetski_state.pbtxt", File(appDataDir, "jetski_state.pbtxt"))
            seedStateFile(context, "antigravity_state.pbtxt", File(appDataDir, "antigravity_state.pbtxt"))
            val projectsParent = projectsDir.parentFile
            if (projectsParent != null && (projectsParent.isDirectory || projectsParent.mkdirs())) {
                seedStateFile(context, "AGENTS.md", File(projectsParent, "AGENTS.md"))
            }
            if (!projectsDir.exists() && !projectsDir.mkdirs()) {
                Log.w(TAG, "cannot create $projectsDir")
                return
            }
            val outside = File(projectsDir, "outside-of-project.json")
            if (!outside.isFile) {
                AtomicFileWriter.writeText(outside, "{}\n")
            }
        } catch (e: Exception) {
            Log.w(TAG, "onboarding seed failed (continuing): " + e.message)
        }
    }

    @Throws(Exception::class)
    private fun seedStateFile(context: Context, assetName: String, target: File) {
        if (target.isFile && target.length() > 0) {
            Log.i(TAG, "state present; left untouched: $target")
            return
        }
        val input: InputStream = context.assets.open("runtime/seed/$assetName")
        try {
            AtomicFileWriter.writeText(target, IoUtil.readAll(input, "UTF-8"))
            input.close()
            Log.i(TAG, "seeded onboarding state (was missing): $target")
        } catch (t: Throwable) {
            try {
                input.close()
            } catch (suppressed: Throwable) {
                t.addSuppressed(suppressed)
            }
            throw t
        }
    }
}
