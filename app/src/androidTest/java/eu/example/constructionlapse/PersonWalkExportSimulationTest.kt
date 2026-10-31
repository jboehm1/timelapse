package eu.example.constructionlapse

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/** Deterministic two-person moving export fixture. Does not use or imply a physical camera. */
@RunWith(AndroidJUnit4::class)
class PersonWalkExportSimulationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val evidence = File(context.filesDir, "recording-evidence")

    @Test fun normalAndMotionBlurShowPeopleWalkingAcrossFixedScene() {
        val root = File(context.cacheDir, "person-walk-fixture-${System.currentTimeMillis()}").apply { mkdirs() }
        val exports = File(evidence, "exports").apply { mkdirs() }
        val source = ArrayList<File>()
        val personCentroids = ArrayList<Float>()
        val width = 640
        val height = 360
        val fps = 7
        try {
            repeat(7) { index ->
                val bitmap = sceneFrame(index, width, height)
                personCentroids += orangeCentroid(bitmap)
                val file = File(root, "frame_${(index + 1).toString().padStart(10, '0')}_${1_790_000_000_000L + index * 1000}.jpg")
                file.outputStream().use { output -> check(bitmap.compress(Bitmap.CompressFormat.JPEG, 98, output)) }
                bitmap.recycle()
                source += file
            }
            assertEquals(7, personCentroids.size)
            assertTrue("The synthetic person's position must advance in every source frame",
                personCentroids.zipWithNext().all { (before, after) -> after - before > 30f })

            val normal = File(exports, "person-walk-none.mp4")
            val blur = File(exports, "person-walk-motion-blur.mp4")
            val trails = File(exports, "person-walk-light-trails.mp4")
            val normalInfo = encodeAndInspect(source, normal, VideoEffect.NONE, width, height, fps)
            val blurInfo = encodeAndInspect(source, blur, VideoEffect.MOTION_BLUR, width, height, fps)
            val trailsInfo = encodeAndInspect(source, trails, VideoEffect.LIGHT_TRAILS, width, height, fps)
            val normalCentroids = source.indices.map { index ->
                val decoded = decodedFrame(normal, index * 1_000_000L / fps)
                try { orangeCentroid(decoded) } finally { decoded.recycle() }
            }
            assertEquals("Normal export must contain one decoded frame for every source", personCentroids.size, normalCentroids.size)
            assertTrue("Normal export must preserve each source moment in order", normalCentroids.zip(personCentroids)
                .all { (decodedX, sourceX) -> kotlin.math.abs(decodedX - sourceX) < 12f })
            assertTrue("Normal export must retain the source walking direction",
                normalCentroids.zipWithNext().all { (before, after) -> after - before > 30f })
            val middleUs = 3_000_000L / fps
            val normalFrame = decodedFrame(normal, middleUs)
            val blurFrame = decodedFrame(blur, middleUs)
            val trailsFrame = decodedFrame(trails, middleUs)
            try {
                val normalPixels = IntArray(width * height)
                val blurPixels = IntArray(width * height)
                normalFrame.getPixels(normalPixels, 0, width, 0, 0, width, height)
                blurFrame.getPixels(blurPixels, 0, width, 0, 0, width, height)
                var pixelsChanged = 0
                var absoluteChannelDifference = 0L
                for (i in normalPixels.indices) {
                    val a = normalPixels[i]
                    val b = blurPixels[i]
                    val difference = kotlin.math.abs(Color.red(a) - Color.red(b)) +
                        kotlin.math.abs(Color.green(a) - Color.green(b)) +
                        kotlin.math.abs(Color.blue(a) - Color.blue(b))
                    absoluteChannelDifference += difference
                    if (difference > 30) pixelsChanged++
                }
                assertTrue("Blur must visibly spread the moving person across adjacent positions", pixelsChanged > 1_000)
                val normalVestPixels = vestPixelCount(normalFrame)
                val trailVestPixels = vestPixelCount(trailsFrame)
                assertTrue("Light Trails must retain bright safety-vest positions from prior samples",
                    trailVestPixels > normalVestPixels * 2)
                val windowExports = org.json.JSONArray()
                for (window in listOf(1, 3, 5, 7, 9)) {
                    val target = if (window == 5) blur else File(exports, "person-walk-blur-window-$window.mp4")
                    val info = if (window == 5) blurInfo else encodeAndInspect(source, target,
                        VideoEffect.MOTION_BLUR, width, height, fps, window)
                    val decoded = if (window == 5) blurFrame else decodedFrame(target, middleUs)
                    val changed = try { changedPixels(normalFrame, decoded, 30) }
                    finally { if (window != 5) decoded.recycle() }
                    if (window == 1) assertTrue("One-sample blur must equal Normal", changed < 1_000)
                    else assertTrue("$window-sample blur must change the walking scene", changed > 1_000)
                    windowExports.put(JSONObject().put("window", window).put("file", info.getString("file"))
                        .put("bytes", info.getLong("bytes")).put("sha256", info.getString("sha256"))
                        .put("samples", info.getInt("samples")).put("pixels_delta_over_30", changed))
                }
                val normalPng = File(evidence, "person-walk-normal.png")
                val blurPng = File(evidence, "person-walk-blur.png")
                savePng(normalFrame, normalPng)
                savePng(blurFrame, blurPng)
                saveComparison(normalFrame, blurFrame, File(evidence, "person-walk-comparison.png"))
                File(evidence, "person-walk-validation.json").writeText(JSONObject()
                    .put("result", "PASS")
                    .put("scene", "two synthetic workers walk left to right in front of a fixed construction-site background")
                    .put("people", 2)
                    .put("source_frames", source.size)
                    .put("person_centroids_x", org.json.JSONArray(personCentroids))
                    .put("normal_decoded_centroids_x", org.json.JSONArray(normalCentroids))
                    .put("width", width).put("height", height).put("fps", fps)
                    .put("effect_window", 5)
                    .put("normal", normalInfo)
                    .put("motion_blur", blurInfo)
                    .put("light_trails", trailsInfo)
                    .put("normal_vest_pixels", normalVestPixels)
                    .put("light_trails_vest_pixels", trailVestPixels)
                    .put("window_outputs", windowExports)
                    .put("comparison_frame_us", middleUs)
                    .put("pixels_delta_over_30", pixelsChanged)
                    .put("sum_absolute_rgb_delta", absoluteChannelDifference)
                    .put("comparison_image", "person-walk-comparison.png")
                    .toString(2) + "\n")
            } finally {
                normalFrame.recycle()
                blurFrame.recycle()
                trailsFrame.recycle()
            }
        } finally {
            root.deleteRecursively()
        }
    }

    private fun sceneFrame(index: Int, width: Int, height: Int): Bitmap {
        val frame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(frame)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(Color.rgb(170, 196, 207))
        paint.color = Color.rgb(191, 184, 158)
        canvas.drawRect(0f, 95f, width.toFloat(), 248f, paint)
        paint.color = Color.rgb(120, 127, 116)
        canvas.drawRect(0f, 248f, width.toFloat(), height.toFloat(), paint)
        paint.color = Color.rgb(99, 106, 99)
        canvas.drawRect(0f, 239f, width.toFloat(), 247f, paint)
        paint.color = Color.rgb(112, 111, 102)
        for (x in 0..width step 80) canvas.drawRect(x.toFloat(), 125f, x + 2f, 230f, paint)
        drawWorker(canvas, 65f + index * 68f, index, paint)
        drawWorker(canvas, 190f + index * 68f, index + 1, paint)
        return frame
    }

    private fun drawWorker(canvas: Canvas, personX: Float, gaitIndex: Int, paint: Paint) {
        val gait = if (gaitIndex % 2 == 0) 1f else -1f
        paint.color = Color.rgb(232, 121, 32)
        canvas.drawRoundRect(RectF(personX - 15, 164f, personX + 15, 238f), 7f, 7f, paint)
        paint.color = Color.rgb(204, 155, 112)
        canvas.drawCircle(personX, 149f, 12f, paint)
        paint.color = Color.rgb(43, 52, 58)
        paint.strokeWidth = 11f
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(personX - 8, 233f, personX - 18 + gait * 14, 286f, paint)
        canvas.drawLine(personX + 8, 233f, personX + 18 - gait * 14, 286f, paint)
        paint.strokeWidth = 8f
        paint.color = Color.rgb(205, 151, 108)
        canvas.drawLine(personX - 11, 178f, personX - 28 + gait * 10, 220f, paint)
        canvas.drawLine(personX + 11, 178f, personX + 28 - gait * 10, 220f, paint)
        paint.color = Color.rgb(54, 58, 59)
        paint.strokeWidth = 9f
        canvas.drawLine(personX - 18 + gait * 14, 286f, personX - 29 + gait * 20, 289f, paint)
        canvas.drawLine(personX + 18 - gait * 14, 286f, personX + 29 - gait * 20, 289f, paint)
    }

    private fun orangeCentroid(bitmap: Bitmap): Float {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var weightedX = 0L
        var count = 0
        for (index in pixels.indices) {
            val color = pixels[index]
            if (Color.red(color) > 190 && Color.green(color) in 75..165 && Color.blue(color) < 75) {
                weightedX += (index % bitmap.width)
                count++
            }
        }
        assertTrue("Synthetic safety vest pixels must be present", count > 100)
        return weightedX.toFloat() / count
    }

    private fun vestPixelCount(bitmap: Bitmap): Int {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.count { color -> Color.red(color) > 200 &&
            Color.red(color) - Color.green(color) > 35 && Color.blue(color) < 190 }
    }

    private fun changedPixels(left: Bitmap, right: Bitmap, threshold: Int): Int {
        require(left.width == right.width && left.height == right.height)
        val leftPixels = IntArray(left.width * left.height)
        val rightPixels = IntArray(right.width * right.height)
        left.getPixels(leftPixels, 0, left.width, 0, 0, left.width, left.height)
        right.getPixels(rightPixels, 0, right.width, 0, 0, right.width, right.height)
        return leftPixels.indices.count { index ->
            val a = leftPixels[index]
            val b = rightPixels[index]
            kotlin.math.abs(Color.red(a) - Color.red(b)) +
                kotlin.math.abs(Color.green(a) - Color.green(b)) +
                kotlin.math.abs(Color.blue(a) - Color.blue(b)) > threshold
        }
    }

    private fun encodeAndInspect(files: List<File>, target: File, effect: VideoEffect,
                                 width: Int, height: Int, fps: Int, window: Int = 5): JSONObject {
        ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_CREATE or
            ParcelFileDescriptor.MODE_TRUNCATE or ParcelFileDescriptor.MODE_READ_WRITE).use { output ->
            VideoExporter.encode(files, output.fileDescriptor, width, height, fps, effect, window, 1_500_000, progress = {})
        }
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(target.absolutePath)
            val track = (0 until extractor.trackCount).first {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            }
            val format = extractor.getTrackFormat(track)
            assertEquals(width, format.getInteger(MediaFormat.KEY_WIDTH))
            assertEquals(height, format.getInteger(MediaFormat.KEY_HEIGHT))
            extractor.selectTrack(track)
            var samples = 0
            while (extractor.sampleTime >= 0) {
                samples++
                if (!extractor.advance()) break
            }
            assertEquals(files.size, samples)
            val duration = format.getLong(MediaFormat.KEY_DURATION)
            assertTrue(kotlin.math.abs(duration - files.size * 1_000_000L / fps) <= 34_000L)
            return JSONObject().put("file", target.name).put("bytes", target.length())
                .put("sha256", digest(target)).put("samples", samples)
                .put("width", width).put("height", height).put("fps", fps).put("duration_us", duration)
        } finally {
            extractor.release()
        }
    }

    private fun decodedFrame(file: File, timeUs: Long): Bitmap {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            return requireNotNull(retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST))
        } finally {
            retriever.release()
        }
    }

    private fun savePng(bitmap: Bitmap, file: File) {
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }

    private fun saveComparison(normal: Bitmap, blur: Bitmap, file: File) {
        val result = Bitmap.createBitmap(normal.width * 2, normal.height + 44, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 26f }
        canvas.drawText("NORMAL", 12f, 32f, label)
        canvas.drawText("MOTION BLUR", normal.width + 12f, 32f, label)
        canvas.drawBitmap(normal, 0f, 44f, null)
        canvas.drawBitmap(blur, normal.width.toFloat(), 44f, null)
        savePng(result, file)
        result.recycle()
    }

    private fun digest(file: File): String {
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                hash.update(buffer, 0, count)
            }
        }
        return hash.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
