package eu.example.constructionlapse

import android.Manifest
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Independent UI. Camera is owned by foreground CaptureService, not Activity.
 * Prototype screen intentionally labels incomplete Pro functions as NOT IMPLEMENTED.
 */
class MainActivity : ComponentActivity() {
    private val exporter = Executors.newSingleThreadExecutor()
    private var service: CaptureService? = null
    private var bound = false
    private var exporting = false
    private lateinit var status: TextView
    private lateinit var preview: PreviewView
    private lateinit var projects: Spinner
    private lateinit var interval: EditText
    private lateinit var timeStart: EditText
    private lateinit var timeEnd: EditText
    private lateinit var dayOnly: CheckBox
    private lateinit var focusMode: Spinner
    private lateinit var effect: Spinner
    private lateinit var fps: Spinner
    private lateinit var quality: Spinner
    private lateinit var resolution: Spinner
    private var projectNames: List<String> = emptyList()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as? CaptureService.LocalBinder)?.service()
            attachPreview()
            refreshStatus()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            refreshStatus("Camera service disconnected")
        }
    }
    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> refreshStatus(if (granted) "Camera ready" else "Camera permission denied") }

    private val exportZip = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri == null || exporting) return@registerForActivityResult
        val photos = frameFiles()
        if (photos.isEmpty()) { refreshStatus("No saved frames"); return@registerForActivityResult }
        exporting = true
        exporter.execute {
            try {
                contentResolver.openOutputStream(uri)?.use { out ->
                    ZipOutputStream(out).use { zip ->
                        photos.forEach {
                            zip.putNextEntry(ZipEntry(it.name))
                            it.inputStream().use { input -> input.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                } ?: throw IOException("No output stream")
                runOnUiThread { refreshStatus("Exported ${photos.size} originals (ZIP)") }
            } catch (e: Exception) {
                runOnUiThread { refreshStatus("ZIP export failed: ${e.message}") }
            } finally { exporting = false }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createUi()
        selectProjects()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) cameraPermission.launch(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
              != PackageManager.PERMISSION_GRANTED) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onStart() {
        super.onStart()
        bound = bindService(Intent(this, CaptureService::class.java),
            connection, Context.BIND_AUTO_CREATE)
    }
    override fun onStop() {
        if (bound) {
            service?.setPreviewSurface(null)
            unbindService(connection)
            bound = false
            service = null
        }
        super.onStop()
    }

    private fun root(): File {
        val base = getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: filesDir
        return File(base, "projects").apply { mkdirs() }
    }
    private fun selectedProject(): File? {
        val n = projectNames.getOrNull(projects.selectedItemPosition) ?: return null
        return File(root(), n).takeIf { it.isDirectory }
    }
    private fun frameFiles(): List<File> = selectedProject()?.listFiles {
        it.isFile && (it.name.endsWith(".jpg", true) || it.name.endsWith(".jpeg", true))
    }?.sortedBy { it.name } ?: emptyList()

    private fun selectProjects(name: String? = null) {
        projectNames = root().listFiles { f -> f.isDirectory }?.map { it.name }?.sorted() ?: emptyList()
        projects.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            if (projectNames.isEmpty()) listOf("(Create a project)") else projectNames)
        val previous = name ?: getSharedPreferences("lapse", MODE_PRIVATE)
            .getString("lastProject", "")
        projectNames.indexOf(previous).takeIf { it >= 0 }?.let { projects.setSelection(it) }
    }

    private fun newProject() {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val file = File(root(), "site_$stamp")
        if (!file.mkdir()) { refreshStatus("Cannot create project directory"); return }
        getSharedPreferences("lapse", MODE_PRIVATE).edit()
            .putString("lastProject", file.name).apply()
        selectProjects(file.name)
        refreshStatus("Project created: ${file.name}")
    }

    private fun createUi() {
        val main = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 12, 16, 12) }
        preview = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    val mode = focusMode.selectedItemPosition
                    service?.meterAt(meteringPointFactory.createPoint(event.x, event.y), mode) { _, message ->
                        runOnUiThread { refreshStatus(message) }
                    } ?: refreshStatus("Start capture for touch AF/AE preview")
                    performClick()
                }
                true
            }
        }
        main.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        status = TextView(this).apply { gravity = Gravity.CENTER_VERTICAL; minHeight = 45; textSize = 13f }
        main.addView(status)
        val scroll = ScrollView(this)
        val controls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(controls)
        main.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1.6f))
        fun row(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun button(text: String, parent: LinearLayout, action: () -> Unit) {
            parent.addView(Button(this).apply {
                this.text = text; setOnClickListener { action() }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        fun labeledEdit(label: String, initial: String, parent: LinearLayout): EditText {
            val r = row()
            r.addView(TextView(this).apply { text = label; gravity = Gravity.CENTER_VERTICAL })
            val field = EditText(this).apply { setText(initial); isSingleLine = true }
            r.addView(field, LinearLayout.LayoutParams(0, -2, 1f))
            parent.addView(r)
            return field
        }
        fun spinner(options: List<String>, parent: LinearLayout): Spinner {
            val s = Spinner(this)
            s.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, options)
            parent.addView(s, LinearLayout.LayoutParams(0, -2, 1f))
            return s
        }
        val projectRow = row()
        projects = spinner(listOf("(none)"), projectRow)
        button("+ Project", projectRow) { newProject() }
        controls.addView(projectRow)
        interval = labeledEdit("Interval(s) ", "20", controls)
        interval.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        val scheduleRow = row()
        dayOnly = CheckBox(this).apply { text = "Daily time range"; isChecked = false }
        scheduleRow.addView(dayOnly)
        timeStart = EditText(this).apply { setText("07:00"); isSingleLine = true }
        timeEnd = EditText(this).apply { setText("18:00"); isSingleLine = true }
        scheduleRow.addView(timeStart, LinearLayout.LayoutParams(0, -2, 1f))
        scheduleRow.addView(timeEnd, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(scheduleRow)
        val focusRow = row()
        focusMode = spinner(listOf("Tap focus", "Tap exposure", "Tap both"), focusRow)
        controls.addView(focusRow)
        val captureRow = row()
        button("▶ Start", captureRow) { startCapture() }
        button("■ Stop", captureRow) { stopCapture() }
        button("Refresh", captureRow) { refreshStatus() }
        controls.addView(captureRow)
        val effectRow = row()
        effect = spinner(listOf("None", "Motion Blur", "Light Trails"), effectRow)
        fps = spinner(listOf("30 fps", "24 fps", "25 fps", "60 fps"), effectRow)
        controls.addView(effectRow)
        val codecRow = row()
        resolution = spinner(listOf("1080p", "4K (test)"), codecRow)
        quality = spinner(listOf("Auto bitrate", "Low", "Medium", "High"), codecRow)
        controls.addView(codecRow)
        val exportRow = row()
        button("Photos ZIP", exportRow) {
            if (service?.status?.startsWith("CAPTURING") == true) {
                refreshStatus("Stop capture before export")
            } else exportZip.launch("${selectedProject()?.name ?: "timelapse"}.zip")
        }
        button("MP4 export", exportRow) { exportVideo() }
        controls.addView(exportRow)
        controls.addView(TextView(this).apply {
            text = "ALPHA — More PRO modes are SPECIFIED, not yet available. Do not rely on unattended 7-day operation before device validation."
            textSize = 12f
        })
        setContentView(main)
        refreshStatus()
    }

    private fun startCapture() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            cameraPermission.launch(Manifest.permission.CAMERA); return
        }
        if (selectedProject() == null) newProject()
        val p = selectedProject() ?: return
        val seconds = interval.text.toString().toLongOrNull()
        if (seconds == null || seconds !in 1..3600) {
            refreshStatus("Interval must be 1–3600s"); return
        }
        try {
            val intent = Intent(this, CaptureService::class.java).apply {
                action = CaptureService.ACTION_START
                putExtra(CaptureService.EXTRA_PROJECT, p.name)
                putExtra(CaptureService.EXTRA_INTERVAL, seconds * 1000L)
                putExtra(CaptureService.EXTRA_DAY_ONLY, dayOnly.isChecked)
                putExtra(CaptureService.EXTRA_FROM, timeStart.text.toString())
                putExtra(CaptureService.EXTRA_TO, timeEnd.text.toString())
            }
            ContextCompat.startForegroundService(this, intent)
            getSharedPreferences("lapse", MODE_PRIVATE).edit()
                .putString("lastProject", p.name).apply()
            refreshStatus("Starting camera foreground service")
        } catch (e: Exception) { refreshStatus("Cannot start: ${e.message}") }
    }

    private fun stopCapture() {
        startService(Intent(this, CaptureService::class.java).apply {
            action = CaptureService.ACTION_STOP
        })
        refreshStatus("Stop requested")
    }

    private fun attachPreview() {
        service?.setPreviewSurface(preview.surfaceProvider)
    }

    private fun exportVideo() {
        if (exporting) return
        val photos = frameFiles()
        if (photos.size < 2) { refreshStatus("At least 2 frames needed"); return }
        val (w,h) = if (resolution.selectedItemPosition == 0) 1920 to 1080 else 3840 to 2160
        val rate = listOf(30,24,25,60)[fps.selectedItemPosition]
        val effectSelected = VideoEffect.entries[effect.selectedItemPosition]
        val bitrate = VideoExporter.bitrateFor(w,h,rate,quality.selectedItemPosition)
        if (!VideoExporter.canEncode(w,h,rate)) {
            refreshStatus("No compatible AVC encoder for ${w}x${h}/${rate}fps"); return
        }
        exporting = true
        refreshStatus("Encoding ${photos.size} frames...")
        exporter.execute {
            var uri: android.net.Uri? = null
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, "${selectedProject()?.name}_${effectSelected.name}.mp4")
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Timelapse")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IOException("Cannot create video")
                contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                    VideoExporter.encode(photos,pfd.fileDescriptor,w,h,rate,effectSelected,5,bitrate) {
                        if (it % 20 == 0) runOnUiThread { refreshStatus("Encoded $it/${photos.size}") }
                    }
                } ?: throw IOException("Output unavailable")
                contentResolver.update(uri, ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                },null,null)
                runOnUiThread { refreshStatus("MP4 saved: Movies/Timelapse") }
            } catch (e: Exception) {
                uri?.let { contentResolver.delete(it,null,null) }
                runOnUiThread { refreshStatus("Export failed: ${e.message}") }
            } finally { exporting = false }
        }
    }
    private fun refreshStatus(message: String? = null) {
        if (::status.isInitialized) status.text = message ?:
            "${service?.status ?: "NOT CAPTURING"} · ${service?.savedCount ?: frameFiles().size} photos · ${service?.failures ?: 0} errors"
    }
    override fun onDestroy() {
        exporter.shutdown()
        super.onDestroy()
    }
}
