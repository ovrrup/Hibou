package com.agy.runtime

import android.content.Context
import java.io.File

/**
 * Filesystem layout of the app-private runtime extraction. Paths are contracts
 * with the engine bootstrap (`assets/runtime/manifest.json` hashes files under
 * `runtime/`).
 *
 * Ported 1:1 from decompiled `com/agy/runtime/RuntimePaths.java` (constants
 * and the Context constructor live on the same class, as in the original).
 */
class RuntimePaths(context: Context) {
    private val appContext: Context = context.applicationContext

    fun getRuntimeDir(): File = File(appContext.filesDir, RUNTIME_ASSET)

    fun getWebDir(): File = File(appContext.filesDir, WEB_ASSET)

    fun getBinDir(): File = File(appContext.filesDir, BIN_DIR)

    fun getLibDir(): File = File(appContext.filesDir, LIB_DIR)

    fun getCertFile(): File = File(appContext.filesDir, CERT_REL)

    fun getGeminiDir(): File = File(appContext.filesDir, GEMINI_DIR)

    fun getAppDataDir(): File = File(getGeminiDir(), APP_DATA_DIR_NAME)

    fun getProjectsDir(): File = File(getGeminiDir(), CONFIG_PROJECTS)

    companion object {
        const val APP_DATA_DIR_NAME = "antigravity-app"
        const val BIN_DIR = "runtime/bin"
        const val CERT_REL = "runtime/certs/ca-certificates.crt"
        const val CONFIG_PROJECTS = "config/projects"
        const val CORE_NAME = "liblanguage_server.so"
        const val GEMINI_DIR = ".gemini"
        const val LIB_DIR = "runtime/lib"
        const val LOADER_NAME = "libloader.so"
        const val MANIFEST_ASSET = "runtime/manifest.json"
        const val RUNTIME_ASSET = "runtime"
        const val RUNTIME_MARKER = ".installed"
        const val SEED_ASSET = "runtime/seed"
        const val WEB_ASSET = "web"
        const val WEB_MARKER = ".web_installed"
    }
}
