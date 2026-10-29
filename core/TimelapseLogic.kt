package eu.example.constructionlapse

/**
 * Android-free scheduling and temporal effects, independently JVM-tested.
 * Real capture deadlines always use SystemClock.elapsedRealtime(), never wall time.
 */
object TimelapseLogic {
    /** Skip elapsed slots: a stalled camera must not produce catch-up photo bursts. */
    fun nextDeadlineMs(previousDeadlineMs: Long, finishedAtMs: Long, intervalMs: Long): Long {
        require(intervalMs > 0L) { "intervalMs must be positive" }
        val elapsed = (finishedAtMs - previousDeadlineMs).coerceAtLeast(0L)
        val intervals = elapsed / intervalMs + 1L
        return Math.addExact(previousDeadlineMs, Math.multiplyExact(intervals, intervalMs))
    }

    /** Bound a Motion Blur or Light Trails window to real source frame indices. */
    fun blurWindow(frameIndex: Int, totalFrames: Int, blendCount: Int): IntRange {
        require(totalFrames > 0) { "No input frames" }
        require(frameIndex in 0 until totalFrames) { "Invalid frame index" }
        require(blendCount == 1 || (blendCount >= 3 && blendCount % 2 == 1)) {
            "Blend count must be 1 or an odd integer >= 3"
        }
        val radius = blendCount / 2
        return maxOf(0, frameIndex - radius)..minOf(totalFrames - 1, frameIndex + radius)
    }
}
