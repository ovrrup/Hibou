// SPDX-FileCopyrightText: 2026 ovrrup <ofeducationc@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package com.kestrel.diag

import android.content.Context
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diagnostic flight recorder for the launch investigation.
 *
 * Appends one line per lifecycle milestone to every writable channel —
 * `/sdcard/Download`, `/sdcard/Android/media/<pkg>/` and the app-private
 * files dir — so the boot path can be reconstructed from any shell that can
 * read shared storage.  It must never alter behaviour: every channel write
 * is individually guarded and silently skipped when unavailable.
 *
 * Install it as the very first statement of the launcher activity; the
 * uncaught-exception hook then captures any crash that the platform's own
 * recorder may have missed on this device.
 */
object FlightLog {
    private const val FILE = "kestrel-flight.log"
    private var appContext: Context? = null
    private val stamp = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    @JvmStatic
    fun install(context: Context) {
        appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                w(
                    "UNCAUGHT",
                    "thread=" + thread.name +
                        " " + throwable.javaClass.name +
                        ": " + throwable.message +
                        "\n" + throwable.stackTraceToString(),
                )
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, throwable)
        }
        w("FLIGHT", "recorder installed")
    }

    /** Appends a milestone.  Never throws, never blocks long. */
    @JvmStatic
    fun w(stage: String, detail: String = "") {
        val context = appContext ?: return
        val line = stamp.format(Date()) +
            " pid=" + android.os.Process.myPid() +
            " [" + Thread.currentThread().name + "] " + stage +
            (if (detail.isEmpty()) "" else " | " + detail.replace('\n', ' '))
        for (file in candidates(context)) {
            try {
                file.parentFile?.mkdirs()
                file.appendText(line + "\n")
            } catch (_: Throwable) {
                // channel unavailable on this device; keep going
            }
        }
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                mediaStoreAppend(context, line)
            } catch (_: Throwable) {
            }
        }
    }

    /**
     * Permission-free fallback: the app may contribute files to the public
     * Downloads collection without any storage grant.  Rewrites the file
     * wholesale each time (MediaStore cannot append).
     */
    private fun mediaStoreAppend(context: Context, line: String) {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        var existing: Uri? = null
        resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            MediaStore.MediaColumns.DISPLAY_NAME + "=?",
            arrayOf(FILE),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                existing = ContentUris.withAppendedId(collection, cursor.getLong(0))
            }
        }
        val target = existing
        if (target == null) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, FILE)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val created = resolver.insert(collection, values) ?: return
            resolver.openOutputStream(created, "w")?.use { out ->
                out.write((line + "\n").toByteArray())
            }
            return
        }
        val current = resolver.openInputStream(target)
            ?.bufferedReader()?.use { it.readText() } ?: ""
        resolver.openFileDescriptor(target, "wt")?.use { pfd ->
            java.io.FileOutputStream(pfd.fileDescriptor).use { out ->
                out.write((current + line + "\n").toByteArray())
            }
        }
    }

    private fun candidates(context: Context): List<File> {
        val out = ArrayList<File>()
        try {
            out.add(
                File(
                    Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS
                    ),
                    FILE,
                ),
            )
        } catch (_: Throwable) {
        }
        try {
            out.add(
                File(
                    Environment.getExternalStorageDirectory(),
                    "Android/media/" + context.packageName + "/" + FILE,
                ),
            )
        } catch (_: Throwable) {
        }
        try {
            out.add(File(context.filesDir, "logs/" + FILE))
        } catch (_: Throwable) {
        }
        return out
    }
}
