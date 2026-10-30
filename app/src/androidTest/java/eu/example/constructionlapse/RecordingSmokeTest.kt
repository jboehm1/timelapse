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
        device.wakeUp()
        device.executeShellCommand("wm dismiss-keyguard")
        device.pressMenu()
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
            val cancelled = File(exports, "cancelled.partial")
            ParcelFileDescriptor.open(cancelled, ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_READ_WRITE).use { output ->
                assertTrue(runCatching { VideoExporter.encode(originals.take(4), output.fileDescriptor, 640, 360, 30,
                    VideoEffect.NONE, 1, 1_000_000, cancelled = { true }, progress = {}) }.exceptionOrNull() is java.util.concurrent.CancellationException)
            }
            assertEquals(hashes, originals.associate { it.name to digest(it) })
            // Recreate Activity, reconcile the persistent filesystem index and compare every hash.
            device.pressHome()
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
            File(evidence, "invalid-camera.txt").writeText(service.status)
            device.takeScreenshot(File(evidence, "invalid-camera.png"))
        } finally {
            context.startService(Intent(context, CaptureService::class.java).apply { action = CaptureService.ACTION_STOP })
        }
    }

}
