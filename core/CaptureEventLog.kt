package eu.example.constructionlapse

import java.io.File

/** Bounded local diagnostics only. Never touches a project or an original frame. */
class CaptureEventLog(private val directory: File, private val maxBytes: Long = 256 * 1024) {
    init { require(maxBytes >= 4096); require(directory.isDirectory || directory.mkdirs()) }
    private val current = File(directory, "capture.log")
    private val previous = File(directory, "capture.previous.log")

    @Synchronized fun append(event: String) {
        // Bound individual exception messages and prevent multiline log injection.
        val bytes = (event.take(1000).replace('\n', ' ').replace('\r', ' ') + "\n").toByteArray()
        if (current.length() + bytes.size > maxBytes) {
            if (previous.exists()) check(previous.delete()) { "Cannot rotate diagnostics" }
            if (current.exists()) check(current.renameTo(previous)) { "Cannot rotate diagnostics" }
        }
        current.appendBytes(bytes)
    }

    @Synchronized fun snapshot(): String = listOf(previous, current)
        .filter { it.isFile }.joinToString("\n") { it.readText() }
}
