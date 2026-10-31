package eu.example.constructionlapse

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CoreReliabilityTest {
    @Test fun committedFrameGapsUseElapsedTimeAndResetPerSession() {
        val tracker = CaptureGapTracker()
        assertNull(tracker.recordSuccess(2000L))
        assertEquals(2500L, tracker.recordSuccess(4500L))
        assertEquals(5500L, tracker.recordSuccess(10000L))
        assertEquals(1000L, tracker.recordSuccess(11000L))
        assertEquals(5500L, tracker.longestGapMs)
        assertTrue(runCatching { tracker.recordSuccess(10999L) }.isFailure)
        tracker.reset()
        assertEquals(0L, tracker.longestGapMs)
        assertNull(tracker.recordSuccess(500L))
    }

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

    @Test fun integrityAuditFindsMissingJournaledOriginal() {
        val dir = Files.createTempDirectory("missing-original-test").toFile()
        try {
            val store = FrameStore(dir)
            val bytes = byteArrayOf(-1, -40, 1, 2, 3, 4, 5, 6, -1, -39)
            val staging = File(dir, "frame_0000000001_1000.pending").apply { writeBytes(bytes) }
            val original = store.commit(staging, "frame_0000000001_1000.jpg", 1000L)
            assertTrue(store.auditOriginals().isEmpty())
            assertTrue(original.delete())
            assertEquals(listOf(original.name), store.auditOriginals())
        } finally { dir.deleteRecursively() }
    }

    @Test fun committedPhotoIsRecoveredWhenJournalAppendFails() {
        val dir = Files.createTempDirectory("journal-failure-test").toFile()
        try {
            val store = FrameStore(dir)
            val bytes = byteArrayOf(-1, -40, 7, 6, 5, 4, 3, 2, -1, -39)
            val staging = File(dir, "frame_0000000001_1000.pending").apply { writeBytes(bytes) }
            assertTrue(File(dir, "frames.journal").mkdir())
            val name = "frame_0000000001_1000.jpg"
            assertTrue(runCatching { store.commit(staging, name, 1000L) }.isFailure)
            val published = File(dir, name)
            assertTrue(published.isFile)
            assertArrayEquals(bytes, published.readBytes())
            assertFalse(staging.exists())
            File(dir, "frames.journal").deleteRecursively()
            assertEquals(1, store.recover().originals)
            assertTrue(store.auditOriginals().isEmpty())
        } finally { dir.deleteRecursively() }
    }

    @Test fun recoverySeparatesTornJournalRowFromRebuiltIntegrityRecord() {
        val dir = Files.createTempDirectory("torn-journal-recovery-test").toFile()
        try {
            val bytes = byteArrayOf(-1, -40, 1, 2, 3, 4, 5, 6, -1, -39)
            val original = File(dir, "frame_0000000001_2000.jpg").apply { writeBytes(bytes) }
            File(dir, "frames.journal").writeText("FRAME\t${original.name}\t${original.length()}\t2000\tpartial")
            val freshStore = FrameStore(dir)
            assertEquals(1, freshStore.recover().originals)
            assertTrue(freshStore.auditOriginals().isEmpty())
            assertEquals(2, File(dir, "frames.journal").readLines().size)
            assertEquals(1, FrameStore(dir).recover().originals)
            assertTrue(FrameStore(dir).auditOriginals().isEmpty())
        } finally { dir.deleteRecursively() }
    }

    @Test fun diagnosticLogIsBoundedAndKeepsRecentFailure() {
        val dir = Files.createTempDirectory("capture-log-test").toFile()
        try {
            val log = CaptureEventLog(dir, 4096)
            repeat(100) { log.append("event=$it " + "x".repeat(5000)) }
            log.append("FAILURE JPEG_COMMIT\ninjected-line")
            assertTrue(dir.listFiles()!!.sumOf { it.length() } <= 8192L)
            val snapshot = log.snapshot()
            assertTrue(snapshot.contains("event=99"))
            assertTrue(snapshot.contains("FAILURE JPEG_COMMIT injected-line"))
            assertFalse(snapshot.contains("event=0 "))
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
