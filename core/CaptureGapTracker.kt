package eu.example.constructionlapse

import java.util.ArrayDeque

/** Tracks gaps between committed originals using a monotonic elapsed-time clock. */
class CaptureGapTracker {
    companion object { const val MAX_SAMPLES = 1_000 }

    var longestGapMs: Long = 0L
        private set
    private var lastSuccessElapsedMs: Long? = null
    private val recentGaps = ArrayDeque<Long>()
    val sampleCount: Int get() = recentGaps.size

    /** Returns null for the first commit; later results exclude failures and pauses before start. */
    fun recordSuccess(elapsedMs: Long): Long? {
        require(elapsedMs >= 0L) { "elapsedMs must be non-negative" }
        val previous = lastSuccessElapsedMs
        require(previous == null || elapsedMs >= previous) { "elapsed clock moved backwards" }
        lastSuccessElapsedMs = elapsedMs
        val gap = previous?.let { elapsedMs - it }
        if (gap != null) {
            if (gap > longestGapMs) longestGapMs = gap
            if (recentGaps.size == MAX_SAMPLES) recentGaps.removeFirst()
            recentGaps.addLast(gap)
        }
        return gap
    }

    /** Nearest-rank percentile over the most recent bounded window of committed-frame gaps. */
    fun percentileGapMs(percentile: Int): Long? {
        require(percentile in 1..100) { "percentile must be from 1 through 100" }
        if (recentGaps.isEmpty()) return null
        val ordered = recentGaps.sorted()
        val rank = ((percentile * ordered.size + 99) / 100).coerceAtLeast(1)
        return ordered[rank - 1]
    }

    fun reset() {
        lastSuccessElapsedMs = null
        longestGapMs = 0L
        recentGaps.clear()
    }
}
