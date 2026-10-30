package eu.example.constructionlapse

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.media.ExifInterface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.opengl.EGL14
import android.opengl.EGLExt
import android.opengl.GLES20
import android.opengl.GLUtils
import android.view.Surface
import java.io.File
import java.io.FileDescriptor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class VideoEffect { NONE, MOTION_BLUR, LIGHT_TRAILS }
enum class StampPosition { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }
data class TimestampOverlay(
    val format: String = "yyyy-MM-dd HH:mm:ss",
    val fontSizePx: Int = 44,
    val color: Int = Color.WHITE,
    val position: StampPosition = StampPosition.BOTTOM_LEFT
)


/** Clean-room MP4 exporter; software temporal effects + OpenGL -> hardware video encoder. */
object VideoExporter {
    fun availableCodecs(): List<Pair<String,String>> {
        val names = listOf(
            MediaFormat.MIMETYPE_VIDEO_AVC to "H.264 / AVC",
            MediaFormat.MIMETYPE_VIDEO_HEVC to "H.265 / HEVC",
            "video/av01" to "AV1"
        )
        val infos = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
        return names.filter { (mime, _) -> infos.any { codec ->
            codec.isEncoder && codec.supportedTypes.any { it.equals(mime, true) } &&
                runCatching { codec.getCapabilitiesForType(mime)
                    .colorFormats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface) }.getOrDefault(false)
        } }
    }

    private fun compatibleCodec(w: Int, h: Int, fps: Int, mime: String): String? =
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.firstOrNull { info ->
            info.isEncoder && info.supportedTypes.any {
                it.equals(mime, true)
            } && runCatching {
                info.getCapabilitiesForType(mime)
                    .videoCapabilities?.areSizeAndRateSupported(w, h, fps.toDouble()) == true
            }.getOrDefault(false)
        }?.name

    fun canEncode(w: Int, h: Int, fps: Int, mime: String = MediaFormat.MIMETYPE_VIDEO_AVC): Boolean =
        compatibleCodec(w,h,fps,mime) != null

    fun bitrateFor(w: Int, h: Int, fps: Int, preset: Int): Int {
        val baseline = (10_000_000L * w * h * fps / (1920L * 1080L * 30L))
            .coerceIn(2_000_000L, 150_000_000L)
        val factor = when(preset) { 1 -> 0.55; 2 -> 1.0; 3 -> 1.6; else -> 1.0 }
        return (baseline * factor).toLong().coerceIn(1_000_000L, 180_000_000L).toInt()
    }

    fun encode(
        files: List<File>, fd: FileDescriptor, width: Int, height: Int, fps: Int,
        effect: VideoEffect, window: Int, bitrate: Int,
        mime: String = MediaFormat.MIMETYPE_VIDEO_AVC,
        timestamp: TimestampOverlay? = null,
        cancelled: () -> Boolean = { false },
        progress: (Int) -> Unit
    ) {
        if (cancelled()) throw java.util.concurrent.CancellationException("Export cancelled")
        require(files.size >= 2 && fps in 1..60)
        require(width > 0 && height > 0 && width % 2 == 0 && height % 2 == 0)
        require(window >= 1 && window % 2 == 1 && window <= 9)
        val codecName = compatibleCodec(width,height,fps,mime)
            ?: error("No $mime encoder supporting ${width}x${height}/${fps}fps")
        // Abort preflight rather than creating an upscaled image disguised as 4K.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(files.first().absolutePath, bounds)
        require(maxOf(bounds.outWidth, bounds.outHeight) >= width &&
                minOf(bounds.outWidth, bounds.outHeight) >= height) { "Insufficient original resolution" }
        val format = MediaFormat.createVideoFormat(mime, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
        }
        val encoder = MediaCodec.createByCodecName(codecName)
        var muxer: MediaMuxer? = null
        var input: Surface? = null
        var renderer: GlEncoderSurface? = null
        var encoderStarted = false
        var muxerStarted = false
        var track = -1
        val info = MediaCodec.BufferInfo()
        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            input = encoder.createInputSurface()
            encoder.start()
            encoderStarted = true
            muxer = MediaMuxer(fd, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            renderer = GlEncoderSurface(input, width, height)
            fun drain(end: Boolean) {
                if(end) encoder.signalEndOfInputStream()
                var empty = 0
                while(true) {
                    if (cancelled()) throw java.util.concurrent.CancellationException("Export cancelled")
                    val index = encoder.dequeueOutputBuffer(info, if(end) 10_000L else 0L)
                    when(index) {
                        MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            if(!end) return
                            if(++empty > 1000) error("Video encoder hung on EOS")
                        }
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            check(!muxerStarted)
                            track = muxer!!.addTrack(encoder.outputFormat)
                            muxer!!.start()
                            muxerStarted = true
                        }
                        else -> if(index >= 0) {
                            val encoded = encoder.getOutputBuffer(index)
                                ?: error("Encoder returned null output")
                            if(info.size > 0 && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                                check(muxerStarted && track >= 0)
                                encoded.position(info.offset)
                                encoded.limit(info.offset + info.size)
                                muxer!!.writeSampleData(track, encoded, info)
                            }
                            val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            encoder.releaseOutputBuffer(index, false)
                            if(eos) return
                            empty = 0
                        }
                    }
                }
            }
            files.indices.forEach { i ->
                if (cancelled()) throw java.util.concurrent.CancellationException("Export cancelled")
                val frame = makeFrame(files,i,width,height,effect,window,timestamp)
                try {
                    renderer.render(frame, i.toLong() * 1_000_000_000L / fps)
                } finally { frame.recycle() }
                drain(false)
                progress(i+1)
            }
            drain(true)
            check(muxerStarted) { "Encoder produced no MP4 video track" }
            muxer!!.stop() // Finalization failure must propagate; never publish a corrupt MP4.
            muxerStarted = false
        } finally {
            try { renderer?.close() } catch (_: Exception) {}
            try { if(muxerStarted) muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
            try { if(encoderStarted) encoder.stop() } catch (_: Exception) {}
            try { encoder.release() } catch (_: Exception) {}
            try { input?.release() } catch (_: Exception) {}
        }
    }

    /** Source frames are immutable. Only a single output bitmap is kept. */
    private fun makeFrame(
        frames: List<File>, index: Int, width: Int, height: Int,
        effect: VideoEffect, window: Int, timestamp: TimestampOverlay?
    ): Bitmap {
        val samples = if(effect == VideoEffect.NONE) index..index
            else TimelapseLogic.blurWindow(index, frames.size, window)
        val out = Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        try {
            var count = 0
            for (i in samples) {
                val source = decodeFrame(frames[i],width,height)
                try {
                    paint.alpha = if(effect == VideoEffect.MOTION_BLUR && count > 0)
                        (255.0/(count+1)).toInt() else 255
                    paint.xfermode = if(effect == VideoEffect.LIGHT_TRAILS && count > 0)
                        PorterDuffXfermode(PorterDuff.Mode.LIGHTEN) else null
                    canvas.drawBitmap(source, 0f, 0f, paint)
                } finally { source.recycle() }
                count++
            }
            paint.xfermode = null
            if(timestamp != null) {
                val stampMillis = frames[index].name.substringAfterLast('_')
                    .substringBefore('.').toLongOrNull() ?: frames[index].lastModified()
                val text = SimpleDateFormat(timestamp.format, Locale.getDefault())
                    .format(Date(stampMillis))
                val typography = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = timestamp.color
                    textSize = timestamp.fontSizePx.coerceIn(12, 300).toFloat()
                    typeface = Typeface.DEFAULT_BOLD
                    setShadowLayer(3f, 1.5f, 1.5f, Color.BLACK)
                }
                val margin = maxOf(12f, width * 0.025f)
                val textWidth = typography.measureText(text)
                val x = if (timestamp.position == StampPosition.TOP_RIGHT ||
                            timestamp.position == StampPosition.BOTTOM_RIGHT)
                    (width - margin - textWidth).coerceAtLeast(margin) else margin
                val y = if (timestamp.position == StampPosition.TOP_LEFT ||
                            timestamp.position == StampPosition.TOP_RIGHT)
                    margin + typography.textSize else height - margin
                canvas.drawText(text,x,y,typography)
            }
            return out
        } catch(e: Throwable) {
            out.recycle()
            throw e
        }
    }

    private fun decodeFrame(file: File, w: Int, h: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Corrupt image: ${file.name}" }
        var sample=1
        while(bounds.outWidth/(sample*2) >= w && bounds.outHeight/(sample*2) >= h)
            sample*=2
        val input = BitmapFactory.decodeFile(file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize=sample; inPreferredConfig=Bitmap.Config.ARGB_8888 })
            ?: error("Decode failed: ${file.name}")
        val exif = runCatching { ExifInterface(file.absolutePath)
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
            .getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val rotation = when(exif) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val source = if(rotation == 0f) input else {
            val result = Bitmap.createBitmap(input,0,0,input.width,input.height,
                Matrix().apply { postRotate(rotation) },true)
            if(result !== input) input.recycle()
            result
        }
        val out = Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
        try {
            val factor=maxOf(w.toFloat()/source.width,h.toFloat()/source.height)
            val cropW=(w/factor).toInt().coerceIn(1,source.width)
            val cropH=(h/factor).toInt().coerceIn(1,source.height)
            val x=(source.width-cropW)/2
            val y=(source.height-cropH)/2
            Canvas(out).drawBitmap(source,Rect(x,y,x+cropW,y+cropH),Rect(0,0,w,h),
                Paint(Paint.FILTER_BITMAP_FLAG))
        } finally { source.recycle() }
        return out
    }
}

/** GLES2 renders one bitmap per output frame into MediaCodec input surface. */
private class GlEncoderSurface(target: Surface,private val width:Int,private val height:Int): AutoCloseable {
    private val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
    private val context: android.opengl.EGLContext
    private val eglSurface: android.opengl.EGLSurface
    private val program: Int
    private val texture: Int
    private val position: Int
    private val uv: Int
    private val vertices = ByteBuffer.allocateDirect(4*4*4).order(ByteOrder.nativeOrder())
        .asFloatBuffer().apply {
            put(floatArrayOf(-1f,-1f,0f,1f, 1f,-1f,1f,1f,
                -1f,1f,0f,0f, 1f,1f,1f,0f)); position(0)
        }
    init {
        check(display != EGL14.EGL_NO_DISPLAY)
        val v = IntArray(2)
        check(EGL14.eglInitialize(display,v,0,v,1))
        val attributes=intArrayOf(EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,
            EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,
            EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,
            0x3142,1,EGL14.EGL_NONE)
        val configs=arrayOfNulls<android.opengl.EGLConfig>(1)
        val count=IntArray(1)
        check(EGL14.eglChooseConfig(display,attributes,0,configs,0,1,count,0)&&count[0]>0)
        val config=checkNotNull(configs[0])
        context=EGL14.eglCreateContext(display,config,EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE),0)
        check(context != EGL14.EGL_NO_CONTEXT)
        eglSurface=EGL14.eglCreateWindowSurface(display,config,target,
            intArrayOf(EGL14.EGL_NONE),0)
        check(eglSurface != EGL14.EGL_NO_SURFACE)
        check(EGL14.eglMakeCurrent(display,eglSurface,eglSurface,context))
        val vertex=shader(GLES20.GL_VERTEX_SHADER, """
            attribute vec2 aPos;
            attribute vec2 aUv;
            varying vec2 vUv;
            void main(){gl_Position=vec4(aPos,0.0,1.0);vUv=aUv;}
        """.trimIndent())
        val fragment=shader(GLES20.GL_FRAGMENT_SHADER, """
            precision mediump float;
            uniform sampler2D uTexture;
            varying vec2 vUv;
            void main(){gl_FragColor=texture2D(uTexture,vUv);}
        """.trimIndent())
        program=GLES20.glCreateProgram()
        GLES20.glAttachShader(program,vertex);GLES20.glAttachShader(program,fragment)
        GLES20.glLinkProgram(program)
        val status=IntArray(1)
        GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,status,0)
        check(status[0]!=0)
        GLES20.glDeleteShader(vertex);GLES20.glDeleteShader(fragment)
        position=GLES20.glGetAttribLocation(program,"aPos")
        uv=GLES20.glGetAttribLocation(program,"aUv")
        val textures=IntArray(1)
        GLES20.glGenTextures(1,textures,0)
        texture=textures[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE)
    }
    fun render(bitmap: Bitmap, timestampNs: Long) {
        GLES20.glViewport(0,0,width,height)
        GLES20.glClearColor(0f,0f,0f,1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,bitmap,0)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"uTexture"),0)
        vertices.position(0)
        GLES20.glVertexAttribPointer(position,2,GLES20.GL_FLOAT,false,16,vertices)
        GLES20.glEnableVertexAttribArray(position)
        vertices.position(2)
        GLES20.glVertexAttribPointer(uv,2,GLES20.GL_FLOAT,false,16,vertices)
        GLES20.glEnableVertexAttribArray(uv)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4)
        check(GLES20.glGetError() == GLES20.GL_NO_ERROR)
        EGLExt.eglPresentationTimeANDROID(display,eglSurface,timestampNs)
        check(EGL14.eglSwapBuffers(display,eglSurface))
    }
    private fun shader(type:Int,source:String):Int{
        val id=GLES20.glCreateShader(type)
        GLES20.glShaderSource(id,source)
        GLES20.glCompileShader(id)
        val status=IntArray(1)
        GLES20.glGetShaderiv(id,GLES20.GL_COMPILE_STATUS,status,0)
        check(status[0]!=0){GLES20.glGetShaderInfoLog(id)}
        return id
    }
    override fun close(){
        GLES20.glDeleteTextures(1,intArrayOf(texture),0)
        GLES20.glDeleteProgram(program)
        EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(display,eglSurface)
        EGL14.eglDestroyContext(display,context)
        EGL14.eglReleaseThread()
        EGL14.eglTerminate(display)
    }
}
