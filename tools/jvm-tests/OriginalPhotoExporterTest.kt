package eu.example.constructionlapse

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipInputStream

private fun originalSha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }

fun main() {
    val root = Files.createTempDirectory("timelapse-original-export").toFile()
    try {
        val source = File(root, "source").apply { mkdirs() }
        val destination = File(root, "destination").apply { mkdirs() }
        val jpg = File(source, "frame_1.jpg").apply { writeBytes(ByteArray(70_001) { (it % 241).toByte() }) }
        val png = File(source, "frame_2.png").apply { writeBytes(ByteArray(8_193) { (it % 239).toByte() }) }
        val sources = listOf(jpg, png)
        val before = sources.associate { it.name to originalSha(it.readBytes()) }

        val mime = mutableMapOf<String, String>()
        val result = OriginalPhotoExporter.exportSequence(sources, openOutput = { name, type ->
            mime[name] = type
            File(destination, name).outputStream()
        })
        check(result.files == 2 && result.bytes == sources.sumOf { it.length() })
        check(mime == mapOf("frame_1.jpg" to "image/jpeg", "frame_2.png" to "image/png"))
        check(before == destination.listFiles()!!.associate { it.name to originalSha(it.readBytes()) })

        val zipBytes = ByteArrayOutputStream()
        var zipDeleted = false
        val zipResult = OriginalPhotoExporter.exportZip(sources, zipBytes, deleteOutput = { zipDeleted = true })
        val zipHashes = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(zipBytes.toByteArray())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                check('/' !in entry.name && '\\' !in entry.name)
                zipHashes[entry.name] = originalSha(zip.readBytes())
            }
        }
        check(zipResult.files == 2 && zipResult.bytes == result.bytes)
        check(before == zipHashes)
        check(!zipDeleted)

        zipDeleted = false
        check(runCatching {
            OriginalPhotoExporter.exportZip(sources, OutputStreamFailure(16), deleteOutput = { zipDeleted = true })
        }.isFailure)
        check(zipDeleted)

        destination.listFiles()!!.forEach { check(it.delete()) }
        var opened = 0
        val failed = runCatching {
            OriginalPhotoExporter.exportSequence(sources, openOutput = { name, _ ->
                opened++
                val file = File(destination, name)
                val raw = file.outputStream()
                if (opened == 2) object : FilterOutputStream(raw) {
                    private var left = 16
                    override fun write(bytes: ByteArray, offset: Int, length: Int) {
                        if (length > left) {
                            if (left > 0) out.write(bytes, offset, left)
                            left = 0
                            throw IOException("injected destination failure")
                        }
                        out.write(bytes, offset, length)
                        left -= length
                    }
                    override fun write(value: Int) {
                        if (left <= 0) throw IOException("injected destination failure")
                        out.write(value)
                        left--
                    }
                } else raw
            }, deleteOutput = { name -> File(destination, name).delete() })
        }.isFailure
        check(failed && opened == 2)
        check(File(destination, "frame_1.jpg").isFile)
        check(!File(destination, "frame_2.png").exists())
        check(before == sources.associate { it.name to originalSha(it.readBytes()) })

        var invalidOpened = false
        check(runCatching {
            OriginalPhotoExporter.exportSequence(emptyList(), openOutput = { _, _ ->
                invalidOpened = true
                ByteArrayOutputStream()
            })
        }.isFailure)
        check(!invalidOpened)
        println("PASS: original JPEG/PNG sequence and ZIP hashes; partial destination cleanup; immutable sources")
    } finally {
        root.deleteRecursively()
    }
}

private class OutputStreamFailure(private val limit: Int) : OutputStream() {
    private var written = 0
    override fun write(value: Int) {
        if (++written > limit) throw IOException("injected ZIP destination failure")
    }
    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        if (written + length > limit) throw IOException("injected ZIP destination failure")
        written += length
    }
}
