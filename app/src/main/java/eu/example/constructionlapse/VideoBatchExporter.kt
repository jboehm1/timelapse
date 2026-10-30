package eu.example.constructionlapse

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.CancellationException

/** Sequential calendar-day jobs. Publish only finalized MP4s; failure deletes only the active pending row. */
object VideoBatchExporter {
    data class Settings(val width: Int, val height: Int, val fps: Int, val effect: VideoEffect,
                        val window: Int, val bitrate: Int, val mime: String, val overlay: TimestampOverlay?,
                        val projectTitle: String? = null)
    data class Progress(val part: Int, val parts: Int, val day: String, val frame: Int, val frames: Int,
                        val published: Boolean = false)
    data class Result(val outputs: List<Uri>, val frames: Int, val elapsedMs: Long, val relativePath: String)

    fun export(context: Context, projectId: String, days: List<ExportDay>, zone: String,
               settings: Settings, cancelled: () -> Boolean, progress: (Progress) -> Unit): Result {
        require(days.isNotEmpty() && days.all { it.files.isNotEmpty() })
        val resolver = context.contentResolver
        val outputs = mutableListOf<Uri>()
        val records = JSONArray()
        val report = AtomicFile(File(context.filesDir, "last-video-export.json"))
        val began = android.os.SystemClock.elapsedRealtime()
        val safeId = projectId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val title = settings.projectTitle?.map { if (it.isLetterOrDigit() || it in "._ -") it else '_' }
            ?.joinToString("")?.trim()?.take(80)?.ifBlank { "Timelapse" }
        val folder = if (title == null) safeId else "${title}_${safeId.takeLast(8)}"
        val relativePath = "Movies/Timelapse/$folder"
        val manifest = JSONObject().put("project", projectId).put("zone", zone)
            .put("width", settings.width).put("height", settings.height).put("fps", settings.fps)
            .put("effect", settings.effect.name).put("window", settings.window).put("bitrate", settings.bitrate)
            .put("mime", settings.mime).put("path", relativePath)
            .put("timestamp", settings.overlay?.let { JSONObject().put("format", it.format).put("font_size", it.fontSizePx)
                .put("color", it.color).put("position", it.position.name) } ?: JSONObject.NULL)
            .put("requested_parts", days.size).put("completed", records)
        fun save(state: String, error: String? = null) {
            manifest.put("state", state).put("elapsed_ms", android.os.SystemClock.elapsedRealtime() - began)
            if (error != null) manifest.put("error", error)
            val stream = report.startWrite()
            try {
                stream.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
                report.finishWrite(stream)
            } catch (e: Exception) { report.failWrite(stream); throw e }
        }
        var pending: Uri? = null
        var frameCount = 0
        try {
            save("RUNNING")
            days.forEachIndexed { index, day ->
                if (cancelled()) throw CancellationException("Export cancelled")
                val storage = StatFs((context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir).absolutePath)
                require(storage.availableBytes >= ExportBudget.requiredFreeBytes(day.files.size, settings.fps, settings.bitrate, storage.totalBytes)) {
                    "Not enough storage for ${day.day ?: "project"} plus safety reserve"
                }
                val dayName = day.day?.toString() ?: "all-days"
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, "${title ?: safeId}_${dayName}_${settings.effect.name}.mp4")
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, relativePath)
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IOException("Cannot create $dayName MP4")
                pending = uri
                progress(Progress(index + 1, days.size, dayName, 0, day.files.size))
                val stats = resolver.openFileDescriptor(uri, "rw")?.use { descriptor ->
                    VideoExporter.encode(day.files, descriptor.fileDescriptor, settings.width, settings.height,
                        settings.fps, settings.effect, settings.window, settings.bitrate, settings.mime,
                        settings.overlay, cancelled = cancelled) { frame ->
                        progress(Progress(index + 1, days.size, dayName, frame, day.files.size))
                    }
                } ?: throw IOException("Output unavailable")
                if (cancelled()) throw CancellationException("Export cancelled before publication")
                check(resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null) == 1) {
                    "Cannot publish finalized MP4"
                }
                // This row is now complete. Never remove it when a later day fails/cancels.
                pending = null
                outputs.add(uri); frameCount += day.files.size
                records.put(JSONObject().put("day", dayName).put("uri", uri.toString()).put("frames", day.files.size)
                    .put("decoded_frames", stats.decodedFrames).put("cache_hits", stats.cacheHits)
                    .put("peak_cache_bytes", stats.peakCacheBytes).put("elapsed_ms", stats.elapsedMs)
                    .put("encoder", stats.codecName).put("hardware", stats.hardwareAccelerated))
                save("RUNNING")
                progress(Progress(index + 1, days.size, dayName, day.files.size, day.files.size, true))
            }
            save("COMPLETE")
            return Result(outputs.toList(), frameCount, android.os.SystemClock.elapsedRealtime() - began, relativePath)
        } catch (e: Exception) {
            pending?.let { uri -> runCatching { resolver.delete(uri, null, null) } }
            val next = days.getOrNull(outputs.size)?.day?.toString() ?: "project"
            val message = "${outputs.size}/${days.size} MP4s kept; next $next: ${e.message}"
            runCatching { save(if (e is CancellationException) "CANCELLED" else "FAILED", message) }
            if (e is CancellationException) throw CancellationException(message).apply { initCause(e) }
            throw IOException(message, e)
        }
    }
}
