package eu.example.constructionlapse

fun main() {
    val tracker = CaptureGapTracker()
    check(tracker.recordSuccess(2_000L) == null)
    check(tracker.longestGapMs == 0L)
    check(tracker.recordSuccess(4_500L) == 2_500L)
    check(tracker.recordSuccess(10_000L) == 5_500L)
    check(tracker.recordSuccess(11_000L) == 1_000L)
    check(tracker.longestGapMs == 5_500L)
    check(runCatching { tracker.recordSuccess(10_999L) }.isFailure)
    check(tracker.longestGapMs == 5_500L)
    tracker.reset()
    check(tracker.longestGapMs == 0L)
    check(tracker.recordSuccess(100L) == null)
    println("PASS: committed-frame gap telemetry uses monotonic time and resets per capture session")
}
