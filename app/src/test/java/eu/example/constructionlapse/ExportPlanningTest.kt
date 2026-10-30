package eu.example.constructionlapse

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ExportPlanningTest {
    private fun frame(index: Int, time: String) = File("frame_${index.toString().padStart(10, '0')}_${Instant.parse(time).toEpochMilli()}.jpg")
    @Test fun calendarDaysRespectDstAndPreserveSequence() {
        val files = listOf(frame(1,"2026-10-24T22:30:00Z"), frame(2,"2026-10-25T00:30:00Z"),
            frame(3,"2026-10-25T01:30:00Z"), frame(4,"2026-10-25T23:30:00Z"))
        val days = ExportDayPlanner.plan(files, ZoneId.of("Europe/Berlin"))
        assertEquals(listOf(LocalDate.parse("2026-10-25"), LocalDate.parse("2026-10-26")), days.map { it.day })
        assertEquals(files.take(3), days.first().files)
        assertEquals(listOf(files.last()), days.last().files)
    }
    @Test fun springGapAndInclusiveRangeDoNotLoseFrames() {
        val files = listOf(frame(1,"2026-03-28T23:30:00Z"), frame(2,"2026-03-29T00:30:00Z"), frame(3,"2026-03-29T01:30:00Z"))
        val day = LocalDate.parse("2026-03-29")
        assertEquals(files, ExportDayPlanner.plan(files, ZoneId.of("Europe/Berlin"), day, day).single().files)
        assertTrue(ExportDayPlanner.plan(files, ZoneId.of("Europe/Berlin"), day.plusDays(1)).isEmpty())
    }
    @Test(expected = IllegalArgumentException::class) fun reversedRangeIsRejected() {
        ExportDayPlanner.plan(emptyList(), ZoneId.of("UTC"), LocalDate.parse("2026-10-31"), LocalDate.parse("2026-10-30"))
    }
    @Test fun importedFilesUseModificationTimeWhenNoCaptureNameExists() {
        val file = File.createTempFile("imported", ".jpg")
        try {
            val time = Instant.parse("2026-10-30T23:30:00Z").toEpochMilli()
            assertTrue(file.setLastModified(time))
            assertEquals(LocalDate.parse("2026-10-31"), ExportDayPlanner.plan(listOf(file), ZoneId.of("Europe/Berlin" )).single().day)
        } finally { file.delete() }
    }
    @Test fun slidingWindowDecodesEachFrameOnceWithRoomForWindow() {
        val disposed = mutableListOf<Int>()
        val cache = ExportFrameWindow<Int>(3, { 1 }, { disposed.add(it) })
        repeat(30) { index ->
            val range = TimelapseLogic.blurWindow(index, 30, 3)
            cache.retain(range)
            range.forEach { source -> cache.use(source, { source }) { assertEquals(source, it) } }
        }
        assertEquals(30, cache.loads); assertEquals(58, cache.hits)
        assertEquals(3L, cache.peakBytes)
        cache.close()
        assertEquals((0..29).toList(), disposed.sorted()); assertEquals(0L, cache.bytes)
    }
    @Test fun smallBudgetNeverThrashesUsefulNeighboursOrExceedsBudget() {
        val disposed = mutableSetOf<Int>()
        var serial = 0
        val cache = ExportFrameWindow<Pair<Int,Int>>(2, { 1 }, { assertTrue(disposed.add(it.second)) })
        repeat(20) { index ->
            val range = TimelapseLogic.blurWindow(index, 20, 9)
            cache.retain(range)
            range.forEach { source -> cache.use(source, { source to serial++ }) { assertEquals(source, it.first) } }
            assertTrue(cache.bytes <= 2)
        }
        assertTrue(cache.hits > 0); cache.close(); assertEquals(serial, disposed.size)
    }
    @Test fun zeroBudgetAndFailureDisposeTemporaryFrames() {
        val disposed = mutableListOf<Int>()
        val cache = ExportFrameWindow<Int>(0, { 1 }, { disposed.add(it) })
        assertTrue(runCatching { cache.use(7, { 7 }) { error("renderer failure") } }.isFailure)
        assertEquals(listOf(7), disposed); assertEquals(0L, cache.peakBytes)
        cache.close(); assertEquals(listOf(7), disposed)
    }
}
