package eu.example.constructionlapse

import kotlin.math.ceil

/** Conservative disk preflight. Originals are never included in reclaimable space. */
object ExportBudget {
    fun requiredFreeBytes(frameCount: Int, fps: Int, bitrate: Int, volumeBytes: Long): Long {
        require(frameCount >= 1 && fps in 1..60 && bitrate > 0 && volumeBytes > 0)
        val expected = ceil(frameCount.toDouble() / fps * bitrate / 8).toLong()
        val overhead = maxOf(20L * 1024 * 1024, ceil(expected * 0.25).toLong())
        val reserve = maxOf(512L * 1024 * 1024, (volumeBytes * 0.05).toLong())
        return Math.addExact(reserve, Math.addExact(expected, overhead))
    }
}
