package eu.example.constructionlapse

fun main() {
    val tracker = CaptureGapTracker()
    check(tracker.recordSuccess(2_000L) == null)
    check(tracker.longestGapMs == 0L)
    check(tracker.recordSuccess(4_500L) == 2_500L)
    check(tracker.recordSuccess(10_000L) == 5_500L)
    check(tracker.recordSuccess(11_000L) == 1_000L)
    check(tracker.longestGapMs == 5_500L)
    check(tracker.sampleCount == 3)
    check(tracker.percentileGapMs(50) == 2_500L)
    check(tracker.percentileGapMs(95) == 5_500L)
    check(tracker.percentileGapMs(99) == 5_500L)
    check(runCatching { tracker.recordSuccess(10_999L) }.isFailure)
    check(tracker.longestGapMs == 5_500L)
    tracker.reset()
    check(tracker.longestGapMs == 0L)
    check(tracker.sampleCount == 0)
    check(tracker.percentileGapMs(50) == null)
    check(tracker.recordSuccess(100L) == null)
    val bounded = CaptureGapTracker()
    repeat(CaptureGapTracker.MAX_SAMPLES + 1) { bounded.recordSuccess(it.toLong()) }
    check(bounded.sampleCount == CaptureGapTracker.MAX_SAMPLES)
    check(bounded.percentileGapMs(50) == 1L)
    println("PASS: committed-frame gap telemetry uses monotonic time and resets per capture session")
}
