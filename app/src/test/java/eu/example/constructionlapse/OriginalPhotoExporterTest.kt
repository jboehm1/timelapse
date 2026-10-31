package eu.example.constructionlapse

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Random
import java.util.zip.ZipInputStream

class OriginalPhotoExporterTest {
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    @Test fun sequenceCopiesExactJpegsAndPngWithoutChangingSources() {
        val dir = createTempDir(prefix = "original-sequence-")
        try {
            val png = File(dir, "frame_2.png").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
            val jpg = File(dir, "frame_1.jpg").apply { writeBytes(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 8, 0xff.toByte(), 0xd9.toByte())) }
            val originals = listOf(jpg, png).associate { it.name to sha(it.readBytes()) }
            val exported = linkedMapOf<String, ByteArray>()
            val mime = mutableMapOf<String, String>()
            val progress = mutableListOf<Pair<Int, Int>>()

            val result = OriginalPhotoExporter.exportSequence(listOf(png, jpg), { name, type ->
                mime[name] = type
                object : ByteArrayOutputStream() {
                    override fun close() { exported[name] = toByteArray(); super.close() }
                }
            }) { done, total -> progress += done to total }

            assertEquals(2, result.files)
            assertEquals(jpg.length() + png.length(), result.bytes)
            assertEquals(listOf(jpg.name, png.name), exported.keys.toList())
            assertEquals(originals, exported.mapValues { sha(it.value) })
            assertEquals("image/jpeg", mime[jpg.name]); assertEquals("image/png", mime[png.name])
            assertEquals(listOf(1 to 2, 2 to 2), progress)
            assertEquals(originals, listOf(jpg, png).associate { it.name to sha(it.readBytes()) })
        } finally { dir.deleteRecursively() }
    }

    @Test fun zipContainsEveryExactOriginalAndNoParentPaths() {
        val dir = createTempDir(prefix = "original-zip-")
        try {
            val files = listOf(
                File(dir, "frame_2.png").apply { writeBytes(ByteArray(8193) { (it % 251).toByte() }) },
                File(dir, "frame_1.jpg").apply { writeBytes(ByteArray(131_077) { (it % 239).toByte() }) }
            )
            val originals = files.associate { it.name to sha(it.readBytes()) }
            val zip = ByteArrayOutputStream()
            val result = OriginalPhotoExporter.exportZip(files, zip)
            val extracted = linkedMapOf<String, String>()
            val entryNames = mutableListOf<String>()
            ZipInputStream(ByteArrayInputStream(zip.toByteArray())).use { input ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    assertFalse(entry.name.contains('/') || entry.name.contains('\\'))
                    entryNames += entry.name
                    extracted[entry.name] = sha(input.readBytes())
                }
            }
            assertEquals(2, result.files)
            assertEquals(files.sumOf { it.length() }, result.bytes)
            assertEquals(listOf("frame_1.jpg", "frame_2.png"), entryNames)
            assertEquals(originals, extracted)
            assertEquals(originals, files.associate { it.name to sha(it.readBytes()) })
        } finally { dir.deleteRecursively() }
    }

    @Test fun unsupportedEmptyAndDuplicateInputsFailBeforeOpeningOutput() {
        val dir = createTempDir(prefix = "original-invalid-")
        try {
            val valid = File(dir, "frame.jpg").apply { writeBytes(byteArrayOf(1)) }
            val unsupported = File(dir, "frame.gif").apply { writeBytes(byteArrayOf(1)) }
            var opened = false
            assertTrue(runCatching { OriginalPhotoExporter.exportSequence(emptyList()) { _, _ -> opened = true; ByteArrayOutputStream() } }.isFailure)
            assertTrue(runCatching { OriginalPhotoExporter.exportSequence(listOf(valid, unsupported)) { _, _ -> opened = true; ByteArrayOutputStream() } }.isFailure)
            assertTrue(runCatching { OriginalPhotoExporter.exportSequence(listOf(valid, File(dir, "frame.jpg"))) { _, _ -> opened = true; ByteArrayOutputStream() } }.isFailure)
            assertFalse(opened)
        } finally { dir.deleteRecursively() }
    }

    @Test fun failedSequenceAndZipLeaveEveryOriginalUnchanged() {
        val dir = createTempDir(prefix = "original-export-failure-")
        try {
            val random = Random(1234)
            val photos = (1..3).map { index ->
                File(dir, "frame_${index.toString().padStart(2, '0')}.jpg").apply {
                    writeBytes(ByteArray(90_000).also(random::nextBytes))
                }
            }
            val before = photos.associate { it.name to sha(it.readBytes()) }
            var opened = 0
            val sequenceFailed = runCatching {
                OriginalPhotoExporter.exportSequence(photos, { _, _ ->
                    opened++
                    if (opened == 2) FailingOutputStream(12) else ByteArrayOutputStream()
                })
            }.isFailure
            assertTrue(sequenceFailed)
            assertEquals("Stop after the failing destination", 2, opened)
            assertTrue(runCatching { OriginalPhotoExporter.exportZip(photos, FailingOutputStream(12)) }.isFailure)
            assertEquals(before, photos.associate { it.name to sha(it.readBytes()) })
        } finally { dir.deleteRecursively() }
    }

    private class FailingOutputStream(private val maxBytes: Int) : OutputStream() {
        private var written = 0
        override fun write(value: Int) {
            if (++written > maxBytes) throw IOException("simulated destination failure")
        }
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            if (written + length > maxBytes) throw IOException("simulated destination failure")
            written += length
        }
    }
}
