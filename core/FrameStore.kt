package eu.example.constructionlapse

import java.io.File
import java.io.FileOutputStream
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/** Filesystem-only, Android-free frame durability layer (one project directory). */
class FrameStore(private val directory: File) {
    data class Recovery(val promoted: Int, val quarantined: Int, val originals: Int, val nextId: Long)
    private val filename = Regex("^frame_([0-9]{10})_[A-Za-z0-9_-]+\\.(jpg|jpeg|png)$", RegexOption.IGNORE_CASE)

    init { require(directory.isDirectory || directory.mkdirs()) { "Cannot create project directory" } }

    fun originals(): List<File> = directory.listFiles().orEmpty()
        .filter { it.isFile && filename.matches(it.name) }.sortedBy { it.name }

    fun nextId(): Long = (originals().mapNotNull {
        filename.matchEntire(it.name)?.groupValues?.get(1)?.toLongOrNull()
    }.maxOrNull() ?: 0L) + 1L

    /** Do not trust an extension; check both beginning and end of the data. */
    fun structurallyValid(file: File, extension: String): Boolean {
        if (!file.isFile || file.length() < 8) return false
        file.inputStream().buffered().use { stream ->
            val header = ByteArray(8)
            if (stream.read(header) != 8) return false
            if (extension.lowercase() == "png") {
                val png = byteArrayOf(-119,80,78,71,13,10,26,10)
                if (!header.contentEquals(png) || file.length() < 20) return false
                file.inputStream().use { tail ->
                    tail.skip(file.length() - 8)
                    val bytes = ByteArray(8)
                    if (tail.read(bytes) != 8) return false
                    return bytes.contentEquals(byteArrayOf(73,69,78,68,-82,66,96,-126))
                }
            }
            if (extension.lowercase() !in setOf("jpg", "jpeg")) return false
            if ((header[0].toInt() and 0xff) != 0xff || (header[1].toInt() and 0xff) != 0xd8) return false
            file.inputStream().use { tail ->
                tail.skip(file.length() - 2)
                return (tail.read() == 0xff && tail.read() == 0xd9)
            }
        }
    }

    /** Atomic publish, no replacement. Fsync staging before commit and audit log afterward. */
    @Synchronized fun commit(staging: File, finalName: String, checkedAtMs: Long): File {
        require(filename.matches(finalName)) { "Frame file naming policy violated" }
        require(staging.parentFile.canonicalFile == directory.canonicalFile) { "Staging outside project" }
        val ext = finalName.substringAfterLast('.')
        require(structurallyValid(staging, ext)) { "Invalid or incomplete $ext image: ${staging.name}" }
        val dst = File(directory, finalName)
        require(!dst.exists()) { "Refusing to overwrite a previous original: $finalName" }
        FileOutputStream(staging, true).use { it.fd.sync() }
        try {
            Files.move(staging.toPath(), dst.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(staging.toPath(), dst.toPath())
        }
        syncDirectoryBestEffort()
        record(dst, checkedAtMs)
        return dst
    }

    /** Promote valid .pending/.partial files; quarantine incomplete staging files. */
    @Synchronized fun recover(): Recovery {
        var promoted = 0
        var quarantined = 0
        for (source in directory.listFiles().orEmpty().filter {
            it.isFile && (it.name.endsWith(".pending") || it.name.endsWith(".partial"))
        }.sortedBy { it.name }) {
            val finalName = when {
                source.name.endsWith(".pending") -> source.name.removeSuffix(".pending") + ".jpg"
                else -> source.name.removeSuffix(".partial")
            }
            try {
                commit(source, finalName, source.lastModified())
                promoted++
            } catch (_: Exception) {
                val dst = File(directory, source.name + ".incomplete")
                if (dst.exists() || !source.renameTo(dst)) {
                    // Retain source staging file; never delete it on recovery failure.
                } else quarantined++
            }
        }
        val frames = originals()
        return Recovery(promoted, quarantined, frames.size, nextId())
    }

    private fun record(file: File, timestampMs: Long) {
        val event = "FRAME\t${file.name}\t${file.length()}\t$timestampMs\n"
        FileOutputStream(File(directory, "frames.journal"), true).use {
            it.write(event.toByteArray(Charsets.UTF_8))
            it.fd.sync()
        }
    }

    private fun syncDirectoryBestEffort() {
        try { FileChannel.open(directory.toPath(), StandardOpenOption.READ).use { it.force(true) } }
        catch (_: Exception) { /* Directory fsync may be unsupported by filesystem. */ }
    }
}
