import eu.example.constructionlapse.TimelapseLogic

private fun checkEquals(expected: Any, actual: Any, label: String) {
    check(expected == actual) { "$label: expected $expected, got $actual" }
}
private inline fun <reified T : Throwable> mustThrow(label: String, block: () -> Unit) {
    check(runCatching(block).exceptionOrNull() is T) { "$label: did not throw ${T::class.simpleName}" }
}

fun main() {
    val window = TimelapseLogic::blurWindow
    checkEquals(0..0, window(0, 4, 1), "No-blur first frame")
    checkEquals(2..2, window(2, 4, 1), "No-blur middle frame")
    checkEquals(0..1, window(0, 4, 3), "3-blend start boundary")
    checkEquals(0..2, window(1, 4, 3), "3-blend center")
    checkEquals(2..3, window(3, 4, 3), "3-blend end boundary")
    checkEquals(0..4, window(2, 7, 5), "5-blend center")
    checkEquals(0..1, window(0, 2, 7), "7-blend with 2 input frames")
    mustThrow<IllegalArgumentException>("Even blend count") { window(0, 3, 2) }
    mustThrow<IllegalArgumentException>("Index out of bounds") { window(3, 3, 3) }
    mustThrow<IllegalArgumentException>("Zero input frames") { window(0, 0, 3) }

    val next = TimelapseLogic::nextDeadlineMs
    checkEquals(20_000L, next(0, 0, 20_000), "On time")
    checkEquals(40_000L, next(0, 21_000, 20_000), "One missed deadline")
    checkEquals(60_000L, next(0, 48_000, 20_000), "Two missed deadlines")
    checkEquals(40_000L, next(20_000, 20_000, 20_000), "Steady cadence")
    checkEquals(60_000L, next(20_000, 40_000, 20_000), "Exactly at next deadline")
    checkEquals(20_000L, next(0, -100, 20_000), "Monotonic clock anomaly")
    mustThrow<IllegalArgumentException>("Invalid interval") { next(0, 1, 0) }
    println("PASS: 17 JVM smoke assertions (motion blur windows + deadline scheduling)")
}
