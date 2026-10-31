package eu.example.constructionlapse

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Environment
import android.os.SystemClock
import android.os.ParcelFileDescriptor
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import org.json.JSONArray
import org.json.JSONObject
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.regex.Pattern
import java.util.zip.ZipInputStream

/** Real UI -> foreground service -> emulated camera -> durable JPEG. No fixture images. */
@RunWith(AndroidJUnit4::class)
class RecordingSmokeTest {
    @get:Rule val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.CAMERA, android.Manifest.permission.POST_NOTIFICATIONS)

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val evidence = File(context.filesDir, "recording-evidence").apply { mkdirs() }

    @After fun saveFinalUi() {
        runCatching { device.takeScreenshot(File(evidence, "last-ui.png")); device.dumpWindowHierarchy(File(evidence, "last-ui.xml")) }
    }
    private fun unlockTestDevice() {
        EmulatorTestDevice.prepare(device, evidence)
    }

    private fun waitFor(label: String, timeout: Long = 60_000L, ready: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + timeout
        while (SystemClock.elapsedRealtime() < end) {
            if (ready()) return
            SystemClock.sleep(250)
        }
        fail("Timed out: $label. Service status: ${CaptureService.activeService?.status}")
    }

    private fun click(description: String) {
        val item = device.wait(Until.findObject(By.desc(description)), 10_000)
        assertNotNull("Missing UI action $description", item)
        item.click()
        device.waitForIdle()
    }

    private fun frames(directory: File) = directory.listFiles().orEmpty()
        .filter { it.name.endsWith(".jpg") }.sortedBy { it.name }

    private fun digest(file: File): String {
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(65536)
            while (true) { val n = stream.read(buffer); if (n < 0) break; hash.update(buffer, 0, n) }
        }
        return hash.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    @Test fun actualStartWritesTenJpegsAndSurvivesBackgroundAndReopen() {
        val root = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "projects")
        val before = root.listFiles().orEmpty().map { it.name }.toSet()
        val launch = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        unlockTestDevice()
        context.startActivity(launch)
        waitFor("preview service") { CaptureService.activeService?.isPreviewing == true }
        device.takeScreenshot(File(evidence, "idle.png"))
        // Create through UI, rather than injecting project/config service intents.
        val newProject = device.wait(Until.findObject(By.text("+ PROJECT")), 10_000)
        assertNotNull(newProject)
        newProject.click()
        waitFor("new project") { root.listFiles().orEmpty().any { it.name !in before } }
        val project = root.listFiles().orEmpty().single { it.name !in before }
        click("Capture settings")
        val interval = device.wait(Until.findObject(By.desc("Capture interval seconds")), 10_000)
        assertNotNull(interval)
        interval.text = "2"
        device.pressBack() // dismiss keyboard if present
        if (device.hasObject(By.desc("Capture interval seconds"))) device.pressBack()
        click("Start capture")
        device.takeScreenshot(File(evidence, "starting.png"))
        try {
            waitFor("ten independently saved JPEGs", 90_000) { frames(project).size >= 10 }
            waitFor("successful-frame gap telemetry") { CaptureService.activeService?.maxSuccessfulFrameGapMs != null }
            val liveCapture = requireNotNull(CaptureService.activeService)
            val longestSuccessfulGap = requireNotNull(liveCapture.maxSuccessfulFrameGapMs)
            assertTrue("Two or more committed frames must produce a measured monotonic gap", longestSuccessfulGap > 0L)
            assertTrue("Attempts cannot be fewer than committed originals", liveCapture.attempts >= liveCapture.savedCount)
            File(evidence, "capture-gap-metrics.json").writeText(JSONObject()
                .put("scope", "Android emulator only")
                .put("saved_frames", liveCapture.savedCount)
                .put("attempts", liveCapture.attempts)
                .put("skipped_slots", liveCapture.skippedSlots)
                .put("longest_successful_gap_ms", longestSuccessfulGap)
                .toString(2) + "\n")
            assertTrue(device.wait(Until.hasObject(By.text(Pattern.compile(".*[1-9][0-9]+ photos.*"))), 5000))
            device.takeScreenshot(File(evidence, "recording.png"))
            val visibleCount = frames(project).size
            assertEquals(visibleCount.toLong(), CaptureService.activeService?.savedCount)
            // Activity stopped and PreviewView detached: service must keep saving.
            device.pressHome()
            waitFor("additional background JPEGs", 30_000) { frames(project).size >= visibleCount + 2 }
            unlockTestDevice()
        context.startActivity(launch)
            click("Stop capture")
            waitFor("stopped") { CaptureService.activeService?.isCapturing != true }
            // Let a callback already saving finish; it must preserve its original.
            SystemClock.sleep(2000)
            val originals = frames(project)
            assertTrue(originals.size >= 12)
            val hashes = originals.associate { it.name to digest(it) }
            // Exercise the lossless original-photo delivery paths with camera-generated JPEGs.
            val sequenceDirectory = File(evidence, "original-sequence").apply { mkdirs() }
            val sequenceResult = OriginalPhotoExporter.exportSequence(
                photos = originals,
                openOutput = { name, _ -> File(sequenceDirectory, name).outputStream() },
                deleteOutput = { name -> File(sequenceDirectory, name).delete() }
            )
            assertEquals(originals.size, sequenceResult.files)
            assertEquals(originals.sumOf { it.length() }, sequenceResult.bytes)
            assertEquals(hashes, sequenceDirectory.listFiles().orEmpty().associate { it.name to digest(it) })
            originals.forEach { original ->
                val copy = File(sequenceDirectory, original.name)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(copy.absolutePath, bounds)
                assertTrue("Exported JPEG failed to decode: ${copy.name}", bounds.outWidth > 0 && bounds.outHeight > 0)
            }
            val zipFile = File(evidence, "originals.zip")
            val zipResult = zipFile.outputStream().use { OriginalPhotoExporter.exportZip(originals, it) }
            assertEquals(originals.size, zipResult.files)
            assertEquals(originals.sumOf { it.length() }, zipResult.bytes)
            val zippedHashes = linkedMapOf<String, String>()
            ZipInputStream(zipFile.inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    assertFalse("ZIP path escaped project folder", entry.name.contains('/') || entry.name.contains('\\'))
                    zippedHashes[entry.name] = digest(zip.readBytes())
                }
            }
            assertEquals(hashes, zippedHashes)
            assertEquals(hashes, originals.associate { it.name to digest(it) })
            File(evidence, "original-export-validation.json").writeText(JSONObject()
                .put("source_images", originals.size).put("sequence_images", sequenceResult.files)
                .put("sequence_bytes", sequenceResult.bytes).put("zip_images", zipResult.files)
                .put("zip_bytes", zipResult.bytes).put("all_source_hashes_preserved", true)
                .put("all_output_hashes_match", true).toString(2))
            val ids = originals.map { it.name.split('_')[1].toLong() }
            assertEquals(ids.size, ids.toSet().size)
            assertEquals(ids.sorted(), ids)
            val archived = File(evidence, "originals").apply { mkdirs() }
            val metrics = originals.map { file ->
                assertTrue(file.length() > 100)
                val bitmap = requireNotNull(BitmapFactory.decodeFile(file.absolutePath)) {
                    "JPEG failed full decoding: ${file.name}"
                }
                val dimensions = "${bitmap.width}x${bitmap.height}"
                bitmap.recycle()
                file.copyTo(File(archived, file.name), overwrite = false)
                "${file.name}\t${file.length()}\t$dimensions\t${hashes[file.name]}"
            }
            // Real camera originals -> three playable MP4 effects, never synthetic fixture input.
            val exports = File(evidence, "exports").apply { mkdirs() }
            val videoMetrics = JSONArray()
            for (effect in VideoEffect.entries) {
                val video = File(exports, "${effect.name.lowercase()}.mp4")
                ParcelFileDescriptor.open(video, ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE or ParcelFileDescriptor.MODE_READ_WRITE).use { output ->
                    VideoExporter.encode(originals.take(4), output.fileDescriptor, 640, 360, 30, effect, 3, 1_000_000, progress = {})
                }
                val extractor = MediaExtractor()
                val times = mutableListOf<Long>()
                try {
                    extractor.setDataSource(video.absolutePath)
                    val track = (0 until extractor.trackCount).first { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
                    val format = extractor.getTrackFormat(track)
                    assertEquals(640, format.getInteger(MediaFormat.KEY_WIDTH))
                    assertEquals(360, format.getInteger(MediaFormat.KEY_HEIGHT))
                    assertEquals("video/avc", format.getString(MediaFormat.KEY_MIME))
                    extractor.selectTrack(track)
                    while (extractor.sampleTime >= 0) { times.add(extractor.sampleTime); if (!extractor.advance()) break }
                    assertEquals(4, times.size)
                    assertTrue(times.zipWithNext().all { (a, b) -> b > a })
                    val duration = format.getLong(MediaFormat.KEY_DURATION)
                    assertTrue(kotlin.math.abs(duration - 4_000_000L / 30) <= 34_000)
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(video.absolutePath)
                        val bitmap = requireNotNull(retriever.getFrameAtTime(0)) { "MP4 cannot be decoded" }
                        assertEquals(640, bitmap.width); assertEquals(360, bitmap.height); bitmap.recycle()
                    } finally { retriever.release() }
                    videoMetrics.put(JSONObject().put("effect", effect.name).put("file", video.name).put("bytes", video.length())
                        .put("width", 640).put("height", 360).put("samples", times.size).put("duration_us", duration).put("sha256", digest(video)))
                } finally { extractor.release() }
            }
            File(evidence, "video-validation.json").writeText(videoMetrics.toString(2))
            val fpsOutputs = JSONArray()
            for (rate in listOf(24, 25, 30, 60)) {
                assertTrue("Requested $rate fps must be supported", VideoExporter.canEncode(640, 360, rate))
                val video = File(exports, "fps-$rate.mp4")
                ParcelFileDescriptor.open(video, ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE or ParcelFileDescriptor.MODE_READ_WRITE).use { output ->
                    VideoExporter.encode(originals.take(4), output.fileDescriptor, 640, 360, rate,
                        VideoEffect.NONE, 1, 1_000_000, progress = {})
                }
                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(video.absolutePath)
                    val track = (0 until extractor.trackCount).first {
                        extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
                    }
                    val format = extractor.getTrackFormat(track)
                    assertEquals(640, format.getInteger(MediaFormat.KEY_WIDTH))
                    assertEquals(360, format.getInteger(MediaFormat.KEY_HEIGHT))
                    assertEquals("video/avc", format.getString(MediaFormat.KEY_MIME))
                    extractor.selectTrack(track)
                    val times = mutableListOf<Long>()
                    while (extractor.sampleTime >= 0) { times.add(extractor.sampleTime); if (!extractor.advance()) break }
                    assertEquals(4, times.size)
                    val expectedPeriodUs = 1_000_000L / rate
                    assertTrue(times.zipWithNext().all { (a, b) -> kotlin.math.abs((b - a) - expectedPeriodUs) <= 2_000L })
                    val duration = format.getLong(MediaFormat.KEY_DURATION)
                    assertTrue(kotlin.math.abs(duration - 4_000_000L / rate) <= expectedPeriodUs)
                    fpsOutputs.put(JSONObject().put("fps", rate).put("file", video.name).put("bytes", video.length())
                        .put("sha256", digest(video)).put("width", 640).put("height", 360)
                        .put("samples", times.size).put("duration_us", duration))
                } finally { extractor.release() }
            }
            File(evidence, "fps-output-validation.json").writeText(fpsOutputs.toString(2) + "\n")
            ExportPerformanceCheck.run(context, evidence, originals)
            val cancelled = File(exports, "cancelled.partial")
            ParcelFileDescriptor.open(cancelled, ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_READ_WRITE).use { output ->
                assertTrue(runCatching { VideoExporter.encode(originals.take(4), output.fileDescriptor, 640, 360, 30,
                    VideoEffect.NONE, 1, 1_000_000, cancelled = { true }, progress = {}) }.exceptionOrNull() is java.util.concurrent.CancellationException)
            }
            assertEquals(hashes, originals.associate { it.name to digest(it) })
            // Recreate Activity, reconcile the persistent filesystem index and compare every hash.
            device.pressHome()
            unlockTestDevice()
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK))
            device.wait(Until.hasObject(By.desc("Start capture")), 10_000)
            assertEquals(hashes, frames(project).associate { it.name to digest(it) })
            assertEquals(originals.size, FrameStore(project).recover().originals)
            assertTrue(FrameStore(project).auditOriginals().isEmpty())
            device.takeScreenshot(File(evidence, "reopened.png"))
            File(evidence, "jpeg-validation.tsv").writeText(
                "file\tbytes\tdimensions\tsha256\n" + metrics.joinToString("\n") + "\n")
        } finally {
            context.startService(Intent(context, CaptureService::class.java).apply { action = CaptureService.ACTION_STOP })
        }
    }
    @Test fun unavailableCameraReportsFailureWithoutClaimingRecording() {
        val launch = Intent(context, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        unlockTestDevice()
        context.startActivity(launch)
        assertTrue(device.wait(Until.hasObject(By.desc("Start capture")), 10_000))
        val project = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
            "projects/fault_${System.currentTimeMillis()}").apply { mkdirs() }
        // Deliberate fault injection, never a fixture or a production debug endpoint.
        instrumentation.runOnMainSync {
            context.startService(Intent(context, CaptureService::class.java).apply {
                action = CaptureService.ACTION_STOP
            })
            context.startForegroundService(Intent(context, CaptureService::class.java).apply {
                action = CaptureService.ACTION_START
                putExtra(CaptureService.EXTRA_PROJECT, project.name)
                putExtra(CaptureService.EXTRA_INTERVAL, 2000L)
                putExtra(CaptureService.EXTRA_DAY_ONLY, false)
                putExtra(CaptureService.EXTRA_CAMERA_ID, "NONEXISTENT_TEST_CAMERA")
            })
        }
        try {
            waitFor("bounded actionable camera failure", 40_000) {
                CaptureService.activeService?.status?.startsWith("NEEDS_USER_ACTION") == true
            }
            val service = requireNotNull(CaptureService.activeService)
            assertFalse(service.hasSessionFrame)
            assertEquals(0L, service.savedCount)
            assertTrue(frames(project).isEmpty())
            assertFalse(service.status.startsWith("RECORDING"))
            assertTrue(service.status.contains("CAMERA_BIND") || service.status.contains("no JPEG"))
            assertTrue("Camera failure count must be separate from saved photos", service.failures > 0)
            assertTrue("The live UI must show saved and failed counts separately",
                device.wait(Until.hasObject(By.text(Pattern.compile(".*0 photos · [1-9][0-9]* errors.*"))), 10_000))
            File(evidence, "invalid-camera.txt").writeText(JSONObject()
                .put("status", service.status)
                .put("saved_frames", service.savedCount)
                .put("attempts", service.attempts)
                .put("failures", service.failures)
                .put("skipped_slots", service.skippedSlots)
                .toString(2) + "\n")
            device.takeScreenshot(File(evidence, "invalid-camera.png"))
        } finally {
            context.startService(Intent(context, CaptureService::class.java).apply { action = CaptureService.ACTION_STOP })
        }
    }

}
