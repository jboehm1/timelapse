package eu.example.constructionlapse

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import org.json.JSONObject
import org.junit.Assert.*
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.CancellationException

/** Actual camera JPEGs, bounded-cache work comparison and atomic day-batch publication/cancellation. */
object ExportPerformanceCheck {
    fun run(context: Context, evidence: File, originals: List<File>) {
        val exports = File(evidence, "exports").apply { mkdirs() }
        val sequence = List(30) { originals[it % originals.size] }
        fun encode(name: String, files: List<File>, budget: Long): ExportStatistics {
            val output = File(exports, name)
            val stats = ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_CREATE or
                ParcelFileDescriptor.MODE_TRUNCATE or ParcelFileDescriptor.MODE_READ_WRITE).use { descriptor ->
                VideoExporter.encode(files, descriptor.fileDescriptor, 640,360,30,
                    VideoEffect.MOTION_BLUR,3,1_000_000,cacheBudgetBytes = budget,progress = {})
            }
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(output.absolutePath)
                val track = (0 until extractor.trackCount).first { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
                extractor.selectTrack(track)
                var count = 0
                while (extractor.sampleTime >= 0) { count++; if (!extractor.advance()) break }
                assertEquals(files.size, count)
                assertTrue(extractor.getTrackFormat(track).getLong(MediaFormat.KEY_DURATION) > 0)
            } finally { extractor.release() }
            return stats
        }
        val uncached = encode("benchmark_uncached.mp4", sequence, 0)
        val optimized = encode("benchmark_cached.mp4", sequence, 4L * 640 * 360 * 4)
        assertEquals(88, uncached.decodedFrames); assertEquals(30, optimized.decodedFrames)
        assertEquals(58, optimized.cacheHits); assertTrue(optimized.peakCacheBytes <= 4L * 640 * 360 * 4)
        val single = encode("single_frame.mp4", originals.take(1), 4L * 640 * 360 * 4)
        assertEquals(1, single.decodedFrames)
        File(evidence,"export-performance.json").writeText(JSONObject()
            .put("frames",30).put("uncached_decodes",uncached.decodedFrames).put("cached_decodes",optimized.decodedFrames)
            .put("uncached_ms",uncached.elapsedMs).put("cached_ms",optimized.elapsedMs)
            .put("cache_hits",optimized.cacheHits).put("peak_cache_bytes",optimized.peakCacheBytes)
            .put("encoder",optimized.codecName).put("hardware",optimized.hardwareAccelerated)
            .put("single_frame_pass",true).toString(2))

        val input = File(evidence,"batch-input").apply { mkdirs() }
        val times = listOf("2026-10-29T10:00:00Z","2026-10-29T10:01:00Z","2026-10-30T10:00:00Z","2026-10-30T10:01:00Z")
        val frames = times.mapIndexed { index, time ->
            originals[index].copyTo(File(input,"frame_${index.toString().padStart(10,'0')}_${Instant.parse(time).toEpochMilli()}.jpg"),overwrite = true)
        }
        val days = ExportDayPlanner.plan(frames,ZoneId.of("UTC"))
        val settings = VideoBatchExporter.Settings(640,360,30,VideoEffect.NONE,1,1_000_000,MediaFormat.MIMETYPE_VIDEO_AVC,null)
        val id = "emulator_batch_${System.currentTimeMillis()}"
        val completed = mutableListOf<Uri>()
        fun validate(uri: Uri) {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(context,uri,null)
                val track = (0 until extractor.trackCount).first { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
                extractor.selectTrack(track)
                var count = 0
                while (extractor.sampleTime >= 0) { count++; if (!extractor.advance()) break }
                assertEquals(2,count)
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context,uri)
                    val bitmap = requireNotNull(retriever.getFrameAtTime(0))
                    assertEquals(640,bitmap.width); assertEquals(360,bitmap.height); bitmap.recycle()
                } finally { retriever.release() }
            } finally { extractor.release() }
        }
        try {
            val result = VideoBatchExporter.export(context,id,days,"UTC",settings,{false},{})
            completed.addAll(result.outputs)
            assertEquals(2,result.outputs.size); assertEquals(4,result.frames)
            result.outputs.forEachIndexed { index, uri ->
                validate(uri)
                context.contentResolver.openInputStream(uri)!!.use { source ->
                    File(exports,"batch_day_${index + 1}.mp4").outputStream().use { source.copyTo(it) }
                }
            }
            var cancel = false
            val failure = runCatching {
                VideoBatchExporter.export(context,id,days,"UTC",settings,{cancel}) { progress ->
                    if (progress.published) {
                        val report = JSONObject(File(context.filesDir,"last-video-export.json").readText())
                        completed.add(Uri.parse(report.getJSONArray("completed").getJSONObject(0).getString("uri")))
                    }
                    if (progress.part == 2 && progress.frame == 1) cancel = true
                }
            }.exceptionOrNull()
            assertTrue(failure is CancellationException)
            val report = JSONObject(File(context.filesDir,"last-video-export.json").readText())
            assertEquals("CANCELLED",report.getString("state"))
            assertEquals(1,report.getJSONArray("completed").length())
            validate(completed.last())
            var rows = 0
            var pending = 0
            context.contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Video.Media.IS_PENDING),MediaStore.Video.Media.RELATIVE_PATH + " = ?",
                arrayOf("Movies/Timelapse/$id/"),null)!!.use { cursor ->
                while (cursor.moveToNext()) { rows++; pending += cursor.getInt(0) }
            }
            assertEquals(3,rows); assertEquals(0,pending)
            File(evidence,"batch-export-validation.json").writeText(JSONObject()
                .put("days",2).put("frames_per_day",2).put("kept_after_cancel",1)
                .put("pending_after_cancel",pending).put("state",report.getString("state")).toString(2))
        } finally { completed.distinct().forEach { context.contentResolver.delete(it,null,null) } }
    }
}
