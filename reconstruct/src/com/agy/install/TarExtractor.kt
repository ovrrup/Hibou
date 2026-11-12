package com.agy.install

import android.system.ErrnoException
import android.system.Os
import com.agy.util.FileModeUtil
import com.agy.util.PathSafety
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream

/**
 * Path-safe streaming tar extractor (512-byte blocks, GNU longname support,
 * symlink validation against prefix escape, mode application).
 *
 * Ported 1:1 from decompiled `com/agy/install/TarExtractor.java`. Returns the
 * number of symlinks created (the bootstrap sanity check counts on it).
 */
object TarExtractor {
    private const val TAG = "TarExtractor"
    private const val BLOCK = 512 // kotlin.io.ConstantsKt.MINIMUM_BLOCK_SIZE

    @Throws(IOException::class)
    fun untarGz(input: InputStream, targetDir: File): Int {
        val gzip = GZIPInputStream(BufferedInputStream(input, 65536), 65536)
        try {
            val symlinks = untar(gzip, targetDir)
            gzip.close()
            return symlinks
        } catch (t: Throwable) {
            try {
                gzip.close()
            } catch (suppressed: Throwable) {
                t.addSuppressed(suppressed)
            }
            throw t
        }
    }

    @Throws(IOException::class)
    fun untar(input: InputStream, targetDir: File): Int {
        val block = ByteArray(BLOCK)
        var symlinks = 0
        var pendingName: String? = null
        while (TarHeader.readBlock(input, block) && !TarHeader.isZeroBlock(block)) {
            val name = TarHeader.readField(block, 0, 100)
            val mode = TarHeader.readOctal(block, 100, 8)
            val size = TarHeader.readOctal(block, 124, 12)
            var type = block[156].toInt() and 0xFF
            val linkTarget = TarHeader.readField(block, 157, 100)
            val prefixDir = TarHeader.readField(block, 345, 155)
            if (type == 0) {
                type = 48
            }
            if (type == 76) { // 'L' GNU long name
                pendingName = String(readEntry(input, size), StandardCharsets.UTF_8).trim()
            } else if (type == 75 || type == 120 || type == 103) { // 'K', 'x', 'g'
                skipEntry(input, size)
            } else {
                val entryName: String = pendingName ?: name
                pendingName = null
                var memberPath = entryName
                if (prefixDir.isNotEmpty()) {
                    memberPath = "$prefixDir/$memberPath"
                }
                val normalized = TarPath.normalize(memberPath)
                    ?: throw IOException("unsafe tar entry path")
                if (normalized.isEmpty()) {
                    skipEntry(input, size)
                } else {
                    val target = File(targetDir, normalized)
                    ensureParentInside(targetDir, target)
                    if (type == 53) { // directory
                        skipEntry(input, size)
                        removeConflictingLeaf(target, true)
                        if (!target.isDirectory && !target.mkdirs() && !target.isDirectory) {
                            throw IOException("cannot create dir $target")
                        }
                        FileModeUtil.applyMode(target, mode)
                    } else if (type == 48) { // regular file
                        removeConflictingLeaf(target, false)
                        writeFile(input, target, size, targetDir)
                        FileModeUtil.applyMode(target, mode)
                    } else if (type == 50) { // symlink
                        skipEntry(input, size)
                        validateLinkTarget(targetDir, target, linkTarget)
                        removeConflictingLeaf(target, false)
                        try {
                            Os.symlink(linkTarget, target.absolutePath)
                            symlinks++
                        } catch (e: ErrnoException) {
                            throw IOException("cannot create symlink $target -> $linkTarget", e)
                        }
                    } else {
                        skipEntry(input, size)
                        throw IOException("unsupported tar entry type " + type.toChar() + " at $normalized")
                    }
                }
            }
        }
        return symlinks
    }

    @Throws(IOException::class)
    private fun validateLinkTarget(root: File, target: File, linkTarget: String) {
        if (linkTarget.isEmpty() || linkTarget.indexOf(0.toChar()) >= 0) {
            throw IOException("empty/invalid tar symlink target at $target")
        }
        if (linkTarget.startsWith("/")) {
            if (!EnvPaths.TERMUX_ANY_PREFIX.matcher(linkTarget).lookingAt()) {
                throw IOException("absolute tar symlink escapes env prefix: $linkTarget")
            }
        } else if (!PathSafety.isInside(root, File(target.parentFile, linkTarget))) {
            throw IOException("tar symlink escapes env prefix: $target -> $linkTarget")
        }
    }

    @Throws(IOException::class)
    private fun ensureParentInside(root: File, target: File) {
        val parent = target.parentFile
        if (parent != null && !PathSafety.isInside(root, parent)) {
            throw IOException("tar entry parent escapes env prefix: $target")
        }
        if (parent != null && !parent.isDirectory && !parent.mkdirs() && !parent.isDirectory) {
            throw IOException("cannot create parent $parent")
        }
    }

    @Throws(IOException::class)
    private fun removeConflictingLeaf(target: File, forDirectory: Boolean) {
        if (TextFileDetector.isSymlink(target)) {
            try {
                Os.remove(target.absolutePath)
            } catch (e: ErrnoException) {
                throw IOException("cannot replace tar symlink $target", e)
            }
        } else {
            if (!target.exists()) {
                return
            }
            if (target.isDirectory) {
                if (!forDirectory && !target.delete()) {
                    throw IOException("cannot replace non-empty tar directory $target")
                }
            } else if (!target.delete()) {
                throw IOException("cannot replace tar file $target")
            }
        }
    }

    @Throws(IOException::class)
    private fun readEntry(input: InputStream, size: Long): ByteArray {
        if (size < 0 || size > 8388608) {
            throw IOException("unreasonable tar entry size: $size")
        }
        val out = ByteArrayOutputStream(size.toInt())
        copy(input, out, size)
        skipPadding(input, size)
        return out.toByteArray()
    }

    @Throws(IOException::class)
    private fun skipEntry(input: InputStream, size: Long) {
        skipExactly(input, size)
        skipPadding(input, size)
    }

    @Throws(IOException::class)
    private fun writeFile(input: InputStream, target: File, size: Long, root: File) {
        ensureParentInside(root, target)
        val out = FileOutputStream(target)
        try {
            copy(input, out, size)
            out.close()
            skipPadding(input, size)
        } catch (t: Throwable) {
            try {
                out.close()
            } catch (suppressed: Throwable) {
                t.addSuppressed(suppressed)
            }
            throw t
        }
    }

    @Throws(IOException::class)
    private fun copy(input: InputStream, output: OutputStream, size: Long) {
        if (size < 0) {
            throw IOException("negative tar entry size: $size")
        }
        var remaining = size
        val buffer = ByteArray(65536)
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(65536, remaining).toInt())
            if (read < 0) {
                throw IOException("truncated tar entry")
            }
            output.write(buffer, 0, read)
            remaining -= read
        }
    }

    @Throws(IOException::class)
    private fun skipExactly(input: InputStream, size: Long) {
        if (size <= 0) {
            return
        }
        var remaining = size
        val buffer = ByteArray(8192) // kotlin.io.DEFAULT_BUFFER_SIZE
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(8192, remaining).toInt())
            if (read < 0) {
                throw IOException("truncated tar entry")
            }
            remaining -= read
        }
    }

    @Throws(IOException::class)
    private fun skipPadding(input: InputStream, size: Long) {
        skipExactly(input, (512 - size % 512) % 512)
    }
}
