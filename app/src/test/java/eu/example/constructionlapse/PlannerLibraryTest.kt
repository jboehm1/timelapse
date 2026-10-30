package eu.example.constructionlapse

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PlannerLibraryTest {
    private fun plan(interval: Long = 500, sizes: List<Long> = emptyList(), free: Long = 100_000_000_000, maxFrames: Long = 0) =
        CapturePlanner.estimate(interval, 8.0, 7, 30.0, 8_000_000, sizes, free, 128_000_000_000, maxFrames = maxFrames)

    @Test fun halfSecondEightHourAndTwoSecondEstimatesAreExact() {
        val fast = plan()
        assertEquals(57_600L, fast.framesPerDay)
        assertEquals(403_200L, fast.projectedFrames)
        assertEquals(3_225_600_000_000L, fast.photoBytes)
        assertEquals(13_440.0, fast.videoSeconds, 0.0001)
        assertEquals(14_400L, plan(2000).framesPerDay)
    }
    @Test fun fixedFrameLimitControlsPhotoAndVideoTotals() {
        val fixed = plan(maxFrames = 1000)
        assertEquals(1000L, fixed.projectedFrames)
        assertEquals(8_000_000_000L, fixed.photoBytes)
        assertEquals(1000.0 / 30, fixed.videoSeconds, 0.0001)
    }
    @Test fun measuredMedianRejectsOutlierAndReservePreventsFalseRunway() {
        val measured = plan(sizes = listOf(6_000_000, 7_000_000, 900_000_000))
        assertTrue(measured.measured)
        assertEquals(7_000_000L, measured.bytesPerPhoto)
        assertEquals(0L, plan(free = 5_000_000_000).safeRemainingFrames)
        assertEquals(0.0, plan(free = 5_000_000_000).safeRemainingDays, 0.0)
    }
    @Test fun decimalIntervalIsExactAndDoesNotSilentlyRound() {
        assertEquals(500L, CapturePlanner.intervalMillis("0.5"))
        assertEquals(1250L, CapturePlanner.intervalMillis("1,25"))
        assertEquals(3_600_000L, CapturePlanner.intervalMillis("3600"))
        listOf("0", "0.499", "3600.1", "1.0001", "NaN", "Infinity").forEach {
            assertTrue("Accepted invalid interval $it", runCatching { CapturePlanner.intervalMillis(it) }.isFailure)
        }
    }
    @Test fun nonDivisibleWindowCountsSlotsWithoutInventingAnEndFrame() {
        val result = CapturePlanner.estimate(3000, 0.0002777777777777778, 1, 30.0, 1000, emptyList(), 1_000_000_000, 2_000_000_000)
        assertEquals(1L, result.framesPerDay)
    }
    @Test fun cataloguePreservesOriginalsAcrossRenameTrashRestoreAndDuplicate() {
        val root = Files.createTempDirectory("projects-test").toFile()
        try {
            val library = ProjectLibrary(root)
            val preset = ProjectLibrary.Preset(500, true, "22:00", "06:00", setOf(1, 3, 5), 1000)
            val project = library.create("Chantier été", ProjectLibrary.Mode.PHOTOLAPSE, preset)
            val original = File(project.directory, "frame_0000000001_1.jpg").apply { writeText("immutable-test-original") }
            val originalBytes = original.readBytes()
            library.rename(project.id, "Solar roof")
            assertEquals(project.directory, library.read(project.id).directory)
            assertEquals("Solar roof", library.read(project.id).title)
            assertTrue(runCatching { library.archive(project.id, true, project.id) }.isFailure)
            library.archive(project.id, true)
            assertTrue(library.list().isEmpty())
            assertEquals(1, library.list(true).size)
            assertArrayEquals(originalBytes, original.readBytes())
            library.archive(project.id, false)
            val duplicate = library.duplicatePreset(project.id)
            assertEquals(preset, duplicate.preset)
            assertEquals(ProjectLibrary.Mode.PHOTOLAPSE, duplicate.mode)
            assertEquals(0, library.inventory(duplicate.id).count)
            assertEquals(1, library.inventory(project.id).count)
            assertArrayEquals(originalBytes, original.readBytes())
            val reopened = ProjectLibrary(root)
            assertEquals(preset, reopened.read(project.id).preset)
            assertEquals("Solar roof", reopened.read(project.id).title)
        } finally { root.deleteRecursively() }
    }
    @Test fun legacyProjectIsDiscoveredWithoutMigrationAndPathsAreConfined() {
        val root = Files.createTempDirectory("legacy-project-test").toFile()
        try {
            val legacy = File(root, "site_20261030_001").apply { mkdir() }
            File(legacy, "frame_0000000001_1.jpg").writeBytes(byteArrayOf(1,2,3))
            val library = ProjectLibrary(root)
            assertEquals(legacy.name, library.list().single().title)
            assertEquals(3L, library.inventory(legacy.name).bytes)
            listOf("../escape", "/tmp/escape", ".hidden", "a/b").forEach { id ->
                assertTrue(runCatching { library.directory(id) }.isFailure)
            }
            assertTrue(runCatching { library.create("\nmalicious") }.isFailure)
        } finally { root.deleteRecursively() }
    }
    @Test fun hundredProjectsAndPresetsRemainIndependent() {
        val root = Files.createTempDirectory("hundred-projects-test").toFile()
        try {
            val library = ProjectLibrary(root)
            repeat(105) { library.create("Roof $it", preset = ProjectLibrary.Preset(500L + it)) }
            assertEquals(105, ProjectLibrary(root).list().size)
            assertEquals(105, library.list().map { it.id }.toSet().size)
            assertEquals(105, library.list().map { it.preset.intervalMs }.toSet().size)
        } finally { root.deleteRecursively() }
    }
    @Test fun damagedMetadataCannotHideExistingOriginals() {
        val root = Files.createTempDirectory("damaged-metadata-test").toFile()
        try {
            val library = ProjectLibrary(root)
            val project = library.create("Keep my originals")
            File(project.directory, "frame_0000000001_1.jpg").writeText("original")
            File(project.directory, ".project.properties").writeText("intervalMs=garbage\nmode=UNKNOWN\n")
            assertEquals(1, library.list().size)
            assertNotNull(library.read(project.id).metadataWarning)
            assertEquals(1, library.inventory(project.id).count)
        } finally { root.deleteRecursively() }
    }
    @Test fun exportBudgetIncludesVideoContainerOverheadAndReserve() {
        val required = ExportBudget.requiredFreeBytes(300, 30, 10_000_000, 128_000_000_000)
        assertTrue(required > 6_400_000_000L + 12_500_000L)
        assertTrue(runCatching { ExportBudget.requiredFreeBytes(1, 30, 10_000_000, 1000) }.isFailure)
    }

}
