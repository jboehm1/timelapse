package eu.example.constructionlapse

import java.io.BufferedOutputStream
import java.io.File
import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class OriginalExportResult(val files: Int, val bytes: Long)

/** Streams immutable project originals to a caller-owned folder or optional ZIP destination. */
object OriginalPhotoExporter {
    private const val BUFFER_SIZE = 64 * 1024
    private val SUPPORTED_EXTENSIONS = setOf("jpg", "jpeg", "png")

    fun exportSequence(
        photos: List<File>,
        openOutput: (name: String, mimeType: String) -> OutputStream,
        onFile: (completed: Int, total: Int) -> Unit = { _, _ -> }
    ): OriginalExportResult {
        val originals = validate(photos)
        var bytes = 0L
        originals.forEachIndexed { index, photo ->
            val mime = when (photo.extension.lowercase(Locale.ROOT)) {
                "png" -> "image/png"
                else -> "image/jpeg"
            }
            openOutput(photo.name, mime).use { output ->
                val copied = photo.inputStream().buffered().use { it.copyTo(output, BUFFER_SIZE) }
                if (copied != photo.length()) throw IOException("Original changed while exporting ${photo.name}")
                bytes += copied
            }
            onFile(index + 1, originals.size)
        }
        return OriginalExportResult(originals.size, bytes)
    }

    fun exportZip(photos: List<File>, output: OutputStream): OriginalExportResult {
        val originals = validate(photos)
        var bytes = 0L
        ZipOutputStream(BufferedOutputStream(NonClosingOutputStream(output))).use { zip ->
            originals.forEach { photo ->
                zip.putNextEntry(ZipEntry(photo.name))
                val copied = photo.inputStream().buffered().use { it.copyTo(zip, BUFFER_SIZE) }
                if (copied != photo.length()) throw IOException("Original changed while exporting ${photo.name}")
                bytes += copied
                zip.closeEntry()
            }
        }
        return OriginalExportResult(originals.size, bytes)
    }

    private fun validate(photos: List<File>): List<File> {
        require(photos.isNotEmpty()) { "No original photos to export" }
        val originals = photos.sortedBy { it.name.lowercase(Locale.ROOT) }
        require(originals.map { it.name.lowercase(Locale.ROOT) }.toSet().size == originals.size) {
            "Duplicate original filenames"
        }
        originals.forEach { photo ->
            require(photo.name.isNotBlank() && photo.name != "." && photo.name != ".." &&
                !photo.name.contains('/') && !photo.name.contains('\\')) { "Unsafe original filename: ${photo.name}" }
            require(photo.extension.lowercase(Locale.ROOT) in SUPPORTED_EXTENSIONS) {
                "Unsupported original format: ${photo.name}"
            }
            require(photo.isFile && photo.length() > 0L) { "Missing or empty original: ${photo.name}" }
        }
        return originals
    }

    private class NonClosingOutputStream(output: OutputStream) : FilterOutputStream(output) {
        override fun close() = flush()
    }
}
