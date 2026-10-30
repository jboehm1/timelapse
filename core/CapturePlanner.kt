package eu.example.constructionlapse

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.ceil
import kotlin.math.max

/** Nominal slot estimates; slow cameras skip slots instead of accumulating a queue. */
object CapturePlanner {
    fun intervalMillis(seconds: String): Long {
        val value = seconds.trim().replace(',', '.').toBigDecimal()
        require(value >= BigDecimal("0.5") && value <= BigDecimal("3600")) { "Interval must be 0.5–3600 seconds" }
        return value.multiply(BigDecimal(1000)).setScale(0, RoundingMode.UNNECESSARY).longValueExact()
    }

    data class Estimate(
        val framesPerDay: Long, val projectedFrames: Long, val photoBytes: Long,
        val videoSeconds: Double, val videoBytes: Long, val usableBytes: Long,
        val safeRemainingFrames: Long, val safeRemainingDays: Double,
        val bytesPerPhoto: Long, val measured: Boolean
    )

    fun estimate(intervalMs: Long, activeHours: Double, days: Int, fps: Double,
                 fallbackPhotoBytes: Long, measuredPhotoBytes: List<Long>,
                 availableBytes: Long, volumeBytes: Long, bitrate: Long = 10_000_000, maxFrames: Long = 0): Estimate {
        require(intervalMs in 500..3_600_000 && activeHours.isFinite() && activeHours > 0 && activeHours <= 24)
        require(maxFrames in 0..10_000_000)
        require(days in 1..3650 && fps.isFinite() && fps > 0 && fps <= 120)
        require(fallbackPhotoBytes in 1..1_000_000_000 && bitrate in 1..1_000_000_000)
        require(availableBytes >= 0 && volumeBytes > 0 && availableBytes <= volumeBytes)
        val samples = measuredPhotoBytes.filter { it > 0 }.sorted()
        val bytes = if (samples.isEmpty()) fallbackPhotoBytes else samples[samples.size / 2]
        val frames = ceil(activeHours * 3_600_000 / intervalMs).toLong()
        val total = if (maxFrames > 0) maxFrames else Math.multiplyExact(frames, days.toLong())
        val photoBytes = Math.multiplyExact(bytes, total)
        val reserve = max(512L * 1024 * 1024, (volumeBytes * 0.05).toLong())
        val usable = (availableBytes - reserve).coerceAtLeast(0)
        val safeFrameBytes = ceil(bytes * 1.2).toLong() // conservative 20% JPEG variability allowance
        val remaining = usable / safeFrameBytes
        val videoSeconds = total / fps
        val videoBytes = ceil(videoSeconds * bitrate / 8).toLong()
        return Estimate(frames, total, photoBytes, videoSeconds, videoBytes, usable, remaining,
            remaining.toDouble() / frames, bytes, samples.isNotEmpty())
    }
}
