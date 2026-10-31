package eu.example.constructionlapse

/** Tracks gaps between committed originals using a monotonic elapsed-time clock. */
class CaptureGapTracker {
    var longestGapMs: Long = 0L
        private set
    private var lastSuccessElapsedMs: Long? = null

    /** Returns null for the first commit; later results exclude failures and pauses before start. */
    fun recordSuccess(elapsedMs: Long): Long? {
        require(elapsedMs >= 0L) { "elapsedMs must be non-negative" }
        val previous = lastSuccessElapsedMs
        require(previous == null || elapsedMs >= previous) { "elapsed clock moved backwards" }
        lastSuccessElapsedMs = elapsedMs
        val gap = previous?.let { elapsedMs - it }
        if (gap != null && gap > longestGapMs) longestGapMs = gap
        return gap
    }

    fun reset() {
        lastSuccessElapsedMs = null
        longestGapMs = 0L
    }
}
