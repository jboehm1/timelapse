package eu.example.constructionlapse

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CoreReliabilityTest {
    @Test fun timingSkipsMissedCaptureDeadlines() {
        assertEquals(20_000L, TimelapseLogic.nextDeadlineMs(0L, 0L, 20_000L))
        assertEquals(60_000L, TimelapseLogic.nextDeadlineMs(0L, 41_000L, 20_000L))
        assertEquals(100_000L, TimelapseLogic.nextDeadlineMs(60_000L, 80_000L, 20_000L))
    }

    @Test fun temporalWindowRespectsEndpoints() {
        assertEquals(0..2, TimelapseLogic.blurWindow(0, 100, 5))
        assertEquals(95..99, TimelapseLogic.blurWindow(99, 100, 9))
        assertEquals(0..0, TimelapseLogic.blurWindow(0, 1, 9))
    }

    @Test fun storeProtectsOriginalsAndRepairsStaging() {
        val dir=Files.createTempDirectory("android-frame-store-test").toFile()
        try {
            val store=FrameStore(dir)
            val jpg=byteArrayOf(-1,-40,1,2,3,4,5,6,-1,-39)
            val first=File(dir,"frame_0000000001_1000.pending").apply {writeBytes(jpg)}
            store.commit(first,"frame_0000000001_1000.jpg",1000L)
            assertEquals(2L,store.nextId())
            assertTrue(store.auditOriginals().isEmpty())
            val incomplete=File(dir,"frame_0000000002_1001.pending").apply {writeBytes(byteArrayOf(-1,-40,1,2))}
            val recover=store.recover()
            assertEquals(0,recover.promoted)
            assertEquals(1,recover.quarantined)
            assertTrue(File(dir,incomplete.name+".incomplete").exists())
            assertEquals(1,store.originals().size)
            assertTrue(runCatching {
                val duplicate=File(dir,"frame_0000000001_1000.pending").apply {writeBytes(jpg)}
                store.commit(duplicate,"frame_0000000001_1000.jpg",1000L)
            }.isFailure)
        } finally { dir.deleteRecursively() }
    }

    @Test fun scheduleRespectsDaylightAndNight() {
        val zone=java.time.ZoneId.of("Europe/Berlin")
        val schedule=DayWindow(true,java.time.LocalTime.of(7,0),
            java.time.LocalTime.of(18,0), zone=zone)
        assertTrue(schedule.contains(java.time.Instant.parse("2026-10-30T10:00:00Z")))
        assertFalse(schedule.contains(java.time.Instant.parse("2026-10-30T18:00:00Z")))
    }
}
