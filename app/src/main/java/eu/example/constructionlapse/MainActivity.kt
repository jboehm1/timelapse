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
import android.provider.DocumentsContract
import android.os.IBinder
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.provider.MediaStore
import android.view.Gravity
import android.view.MotionEvent
import android.view.KeyEvent
import android.graphics.Color
import android.view.ViewGroup
import android.graphics.drawable.GradientDrawable
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.AdapterView
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Independent UI. Camera is owned by foreground CaptureService, not Activity.
 * Prototype screen intentionally labels incomplete Pro functions as NOT IMPLEMENTED.
 */
class MainActivity : ComponentActivity() {
    private val exporter = Executors.newSingleThreadExecutor()
    private var service: CaptureService? = null
    private var bound = false
    private var exporting: Boolean
        get() = ProjectFileJobs.busy
        set(value) { ProjectFileJobs.busy = value }
    private var cancelExportRequested: Boolean
        get() = ProjectFileJobs.cancelVideo
        set(value) { ProjectFileJobs.cancelVideo = value }
    private var exportStatus: String?
        get() = ProjectFileJobs.videoStatus
        set(value) { ProjectFileJobs.videoStatus = value }
    private fun captureArmed() = CaptureService.activeService?.isCapturing == true
    private lateinit var status: TextView
    private lateinit var preview: PreviewView
    private lateinit var projects: Spinner
    private lateinit var interval: EditText
    private lateinit var timeStart: EditText
    private lateinit var timeEnd: EditText
    private lateinit var dayOnly: CheckBox
    private lateinit var focusMode: Spinner
    private lateinit var targetOverlay: MeteringOverlay
    private lateinit var keepFocus: CheckBox
    private lateinit var keepExposure: CheckBox
    private var selectionRadius = 0.12f
    private lateinit var lens: Spinner
    private var availableLenses: List<Pair<String,String>> = emptyList()
    private lateinit var effect: Spinner
    private lateinit var blurStrength: Spinner
    private lateinit var fps: Spinner
    private lateinit var quality: Spinner
    private lateinit var codec: Spinner
    private var codecOptions: List<Pair<String,String>> = emptyList()
    private lateinit var photoResolution: Spinner
    private lateinit var photoBadge: TextView
    private var supportedPhotoSizes: List<PhotoResolution> = emptyList()
    private lateinit var settingsDialog: android.app.Dialog
    private lateinit var mainRecordButton: Button
    private lateinit var projectChip: TextView
    private val statusHandler=android.os.Handler(android.os.Looper.getMainLooper())
    private val statusTicker=object:Runnable {
        override fun run() {
            refreshStatus()
            statusHandler.postDelayed(this,1500L)
        }
    }
    private lateinit var resolution: Spinner
    private lateinit var exportPerDay: CheckBox
    private lateinit var exportFromDay: EditText
    private lateinit var exportThroughDay: EditText
    private lateinit var stampEnabled: CheckBox
    private lateinit var stampPattern: EditText
    private lateinit var stampPosition: Spinner
    private lateinit var stampColor: Spinner
    private lateinit var stampSize: EditText
    private var projectNames: List<String> = emptyList()
    private var projectInfo: Map<String, ProjectLibrary.Project> = emptyMap()
    private val inventoryExecutor = Executors.newSingleThreadExecutor()
    private val inventoryPending = java.util.concurrent.atomic.AtomicBoolean(false)
    private var inventoryProject: String? = null
    private var inventoryCount = 0
    private var inventoryAt = 0L

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as? CaptureService.LocalBinder)?.service()
            attachPreview()
            requestCameraPreview()
            refreshStatus()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            refreshStatus("Camera service disconnected")
        }
    }
    private var pendingTestShot = false
    private var startAfterPermission = false
    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        refreshStatus(if (granted) "Camera ready" else "Camera permission denied")
        if (granted && startAfterPermission) {
            startAfterPermission = false
            startCapture(pendingTestShot)
        } else {
            startAfterPermission = false
            if (granted) requestCameraPreview()
        }
    }

    private val workspace = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val id = result.data?.getStringExtra("project")
        if (id != null && service?.isCapturing != true) {
            selectProjects(id)
            loadProjectPreset()
        }
        updateProjectChip()
    }

    private fun projectLibrary() = ProjectLibrary(root())
    private fun loadProjectPreset() {
        if (service?.isCapturing == true) return
        selectedProject()?.let { dir -> runCatching { projectLibrary().read(dir.name).preset }.onSuccess { preset ->
            interval.setText(java.math.BigDecimal.valueOf(preset.intervalMs, 3).stripTrailingZeros().toPlainString())
            dayOnly.isChecked = preset.dayOnly
            timeStart.setText(preset.from); timeEnd.setText(preset.until)
        } }
    }

    private val exportDiagnostics = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) Thread {
            runCatching {
                CaptureDiagnostics.initialize(this)
                val snapshot = CaptureDiagnostics.snapshot()
                requireNotNull(contentResolver.openOutputStream(uri, "wt")).bufferedWriter().use {
                    it.write(snapshot)
                }
            }.onSuccess { runOnUiThread { refreshStatus("Capture diagnostics saved") } }
                .onFailure { error -> runOnUiThread { refreshStatus("Diagnostic export failed: ${error.message}") } }
        }.start()
    }

    private val importFolder = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { tree ->
        if (tree == null) return@registerForActivityResult
        if (exporting || captureArmed()) {
            refreshStatus("Stop capture/export before importing"); return@registerForActivityResult
        }
        val dir = selectedProject() ?: run { refreshStatus("Create a project first"); return@registerForActivityResult }
        exporting = true
        exporter.execute {
            var count = 0
            try {
                val rootId = DocumentsContract.getTreeDocumentId(tree)
                val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, rootId)
                val items = mutableListOf<Pair<String,String>>()
                contentResolver.query(children,
                    arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(0)
                        val name = cursor.getString(1)
                        val mime = cursor.getString(2) ?: ""
                        if (mime == "image/jpeg" || mime == "image/png" ||
                            name.endsWith(".jpg", true) || name.endsWith(".jpeg", true) ||
                            name.endsWith(".png", true)) items.add(name to id)
                    }
                } ?: throw IOException("Cannot enumerate source folder")
                items.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.first })
                val frameStore = FrameStore(dir)
                var nextId = frameStore.nextId()
                for ((name,id) in items) {
                    val ext = if (name.endsWith(".png",true)) "png" else "jpg"
                    val target = File(dir, "frame_${(nextId++).toString().padStart(10,'0')}_import.$ext")
                    val partial = File(dir, "${target.name}.partial")
                    try {
                        val source = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                        contentResolver.openInputStream(source)?.use { input ->
                            partial.outputStream().use { output -> input.copyTo(output) }
                        } ?: throw IOException("Cannot read $name")
                        frameStore.commit(partial,target.name,System.currentTimeMillis())
                        count++
                    } catch (e: Exception) {
                        // Leave original import staging for eventual recovery/quarantine.
                        throw e
                    }
                    if (count % 50 == 0) runOnUiThread { refreshStatus("Imported $count frames") }
                }
                runOnUiThread { refreshStatus("Imported $count frames into ${dir.name}") }
            } catch (e: Exception) {
                runOnUiThread { refreshStatus("Import failed after $count frames: ${e.message}") }
            } finally { exporting = false }
        }
    }

    private val exportSequence = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { tree ->
        if (tree == null || exporting) return@registerForActivityResult
        if (captureArmed()) {
            refreshStatus("Stop capture before exporting"); return@registerForActivityResult
        }
        val frames = frameFiles()
        if (frames.isEmpty()) { refreshStatus("No frames to export"); return@registerForActivityResult }
        exporting = true
        exporter.execute {
            val createdDocuments = mutableMapOf<String, android.net.Uri>()
            try {
                val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                val result = OriginalPhotoExporter.exportSequence(frames, { name, mime ->
                    val item = DocumentsContract.createDocument(contentResolver, parent, mime, name)
                        ?: throw IOException("Cannot create $name")
                    createdDocuments[name] = item
                    contentResolver.openOutputStream(item) ?: throw IOException("Cannot write $name")
                }, onFile = { count, total ->
                    if (count % 25 == 0 || count == total) runOnUiThread { refreshStatus("Exported $count/$total") }
                }, deleteOutput = { name ->
                    createdDocuments.remove(name)?.let { contentResolver.delete(it, null, null) }
                })
                runOnUiThread { refreshStatus("Exported ${result.files} individual images (${result.bytes} bytes)") }
            } catch (e: Exception) {
                runOnUiThread { refreshStatus("Image export stopped; originals remain intact: ${e.message}") }
            } finally { exporting = false }
        }
    }

    private val exportZip = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri == null || exporting) return@registerForActivityResult
        if (captureArmed()) { refreshStatus("Stop capture before ZIP export"); return@registerForActivityResult }
        val photos = frameFiles()
        if (photos.isEmpty()) { refreshStatus("No saved frames"); return@registerForActivityResult }
        exporting = true
        exporter.execute {
            try {
                val result = try {
                    val output = contentResolver.openOutputStream(uri) ?: throw IOException("No output stream")
                    output.use { OriginalPhotoExporter.exportZip(photos, it) }
                } catch (failure: Exception) {
                    runCatching { contentResolver.delete(uri, null, null) }
                        .exceptionOrNull()?.let(failure::addSuppressed)
                    throw failure
                }
                runOnUiThread { refreshStatus("Exported ${result.files} originals (${result.bytes} bytes, ZIP)") }
            } catch (e: Exception) {
                runOnUiThread { refreshStatus("ZIP export interrupted; originals remain intact: ${e.message}") }
            } finally { exporting = false }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createUi()
        redesignUi()
        selectProjects()
        loadProjectPreset()
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
        statusHandler.removeCallbacks(statusTicker)
        statusHandler.post(statusTicker)
        bound = bindService(Intent(this, CaptureService::class.java),
            connection, Context.BIND_AUTO_CREATE)
    }
    override fun onStop() {
        statusHandler.removeCallbacks(statusTicker)
        if (bound) {
            service?.setPreviewSurface(null)
            if (service?.isPreviewing == true) startService(Intent(this, CaptureService::class.java).apply { action = CaptureService.ACTION_RELEASE_PREVIEW })
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
        it.isFile && (it.name.endsWith(".jpg", true) || it.name.endsWith(".jpeg", true) || it.name.endsWith(".png", true))
    }?.sortedBy { it.name } ?: emptyList()

    private fun selectProjects(name: String? = null) {
        val entries = projectLibrary().list()
        projectNames = entries.map { it.id }
        projectInfo = entries.associateBy { it.id }
        projects.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            if (projectNames.isEmpty()) listOf("(Create a project)") else entries.map { it.title })
        val previous = name ?: getSharedPreferences("lapse", MODE_PRIVATE)
            .getString("lastProject", "")
        projectNames.indexOf(previous).takeIf { it >= 0 }?.let { projects.setSelection(it) }
        updateProjectChip()
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
        }
        targetOverlay = MeteringOverlay(this).apply {
            onSelect = { target, marker ->
                val adjusted = marker.copy(size = selectionRadius)
                setMarker(target, adjusted)
                saveMeteringSelection()
                applyMeteringSelection(target, adjusted)
            }
        }
        val previewHolder = FrameLayout(this)
        previewHolder.addView(preview, FrameLayout.LayoutParams(-1,-1))
        previewHolder.addView(targetOverlay, FrameLayout.LayoutParams(-1,-1))
        main.addView(previewHolder,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        status = TextView(this).apply { gravity = Gravity.CENTER_VERTICAL; minHeight = 45; textSize = 13f }
        main.addView(status)
        val scroll = ScrollView(this)
        val controls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(controls)
        main.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1.6f))
        fun row(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun button(text: String, parent: LinearLayout, action: () -> Unit): Button {
            val button = Button(this).apply { this.text = text; setOnClickListener { action() } }
            parent.addView(button, if (parent.orientation == LinearLayout.VERTICAL)
                LinearLayout.LayoutParams(-1, -2) else LinearLayout.LayoutParams(0, -2, 1f))
            return button
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
        interval.contentDescription = "Capture interval seconds"
        interval.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        val scheduleRow = row()
        dayOnly = CheckBox(this).apply { text = "Daily time range"; isChecked = false }
        scheduleRow.addView(dayOnly)
        timeStart = EditText(this).apply { setText("07:00"); isSingleLine = true }
        timeEnd = EditText(this).apply { setText("18:00"); isSingleLine = true }
        scheduleRow.addView(timeStart, LinearLayout.LayoutParams(0, -2, 1f))
        scheduleRow.addView(timeEnd, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(scheduleRow)
        val lensesRow = row()
        val manager = getSystemService(CAMERA_SERVICE) as CameraManager
        availableLenses = manager.cameraIdList.mapNotNull { id ->
            runCatching {
                val ch = manager.getCameraCharacteristics(id)
                val facing = when (ch.get(CameraCharacteristics.LENS_FACING)) {
                    CameraCharacteristics.LENS_FACING_FRONT -> "Front"
                    CameraCharacteristics.LENS_FACING_BACK -> "Back"
                    else -> "External"
                }
                val mm = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    ?.joinToString("/") { String.format(Locale.US, "%.1f",it) } ?: "?"
                id to "$facing #$id ($mm mm)"
            }.getOrNull()
        }
        lens = spinner(availableLenses.map { it.second }.ifEmpty { listOf("Unavailable") }, lensesRow)
        controls.addView(lensesRow)
        val focusRow = row()
        focusMode = spinner(listOf("EDIT Focus (green)", "EDIT Exposure (yellow)", "EDIT Both"), focusRow)
        controls.addView(focusRow)
        val locksRow = row()
        keepFocus = CheckBox(this).apply { text="Lock focus (AF)" ; isChecked = true }
        keepExposure = CheckBox(this).apply { text="Lock exposure (AE)"; isChecked = false }
        locksRow.addView(keepFocus)
        locksRow.addView(keepExposure)
        controls.addView(locksRow)
        val zoneRow = row()
        zoneRow.addView(TextView(this).apply { text = "Zone size 2%-50%"; gravity=Gravity.CENTER_VERTICAL })
        zoneRow.addView(SeekBar(this).apply {
            max = 48
            progress = 10
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    selectionRadius = (p + 2) / 100f
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }, LinearLayout.LayoutParams(0,-2,1f))
        controls.addView(zoneRow)
        controls.addView(TextView(this).apply {
            text = "Tap independent preview zones. AF lock is persistent when supported; AE lock freezes outdoor brightness and is generally NOT recommended."
            textSize = 12f
        })
        focusMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(p: AdapterView<*>?) {}
            override fun onItemSelected(p: AdapterView<*>?, view: android.view.View?, pos: Int, id: Long) {
                targetOverlay.target = listOf(MeteringOverlay.Target.FOCUS,
                    MeteringOverlay.Target.EXPOSURE,MeteringOverlay.Target.BOTH)[pos]
            }
        }
        keepFocus.setOnCheckedChangeListener { _, _ ->
            saveMeteringSelection()
            targetOverlay.focus?.let { applyMeteringSelection(MeteringOverlay.Target.FOCUS,it) }
        }
        keepExposure.setOnCheckedChangeListener { _, _ ->
            saveMeteringSelection()
            targetOverlay.exposure?.let { applyMeteringSelection(MeteringOverlay.Target.EXPOSURE,it) }
        }
        val manualFocusRow = row()
        val manualFocusD = labeledEdit("Focus D ", "0.5", manualFocusRow)
        button("Set MF", manualFocusRow) {
            val d = manualFocusD.text.toString().toFloatOrNull()
            if (d == null || d < 0f) refreshStatus("Enter focus distance in diopters (0=infinity)")
            else service?.manualFocus(d) { _,message -> runOnUiThread { refreshStatus(message) } }
                ?: refreshStatus("Camera not running")
        }
        button("Auto AF", manualFocusRow) {
            service?.resetAutofocus { _,message -> runOnUiThread { refreshStatus(message) } }
                ?: refreshStatus("Camera not running")
        }
        controls.addView(manualFocusRow)
        val manualExposureRow = row()
        val manualIso = labeledEdit("ISO ", "100", manualExposureRow)
        val manualShutter = labeledEdit("Shutter ms ", "10", manualExposureRow)
        button("Set AE manual",manualExposureRow) {
            val iso = manualIso.text.toString().toIntOrNull()
            val ms = manualShutter.text.toString().toFloatOrNull()
            if (iso == null || ms == null || ms <= 0 || !ms.isFinite())
                refreshStatus("Enter valid ISO and exposure milliseconds")
            else service?.manualExposure(iso,ms) { _,message -> runOnUiThread { refreshStatus(message) } }
                ?: refreshStatus("Camera not running")
        }
        button("Auto AE",manualExposureRow) {
            service?.resetAutoExposure { _,message -> runOnUiThread { refreshStatus(message) } }
                ?: refreshStatus("Camera not running")
        }
        controls.addView(manualExposureRow)
        lens.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(p: AdapterView<*>?) {}
            override fun onItemSelected(p: AdapterView<*>?, view: android.view.View?, pos: Int, id: Long) {
                restoreMeteringSelection()
            }
        }
        projects.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(p: AdapterView<*>?) {}
            override fun onItemSelected(p: AdapterView<*>?, view: android.view.View?, pos: Int, id: Long) {
                restoreMeteringSelection()
                updateProjectChip()
            }
        }
        val captureRow = row()
        button("▶ Start", captureRow) { startCapture() }
        button("■ Stop", captureRow) { stopCapture() }
        button("Refresh", captureRow) { refreshStatus() }
        controls.addView(captureRow)
        val effectRow = row()
        effect = spinner(listOf("None", "Motion Blur", "Light Trails"), effectRow)
        fps = spinner(listOf("30 fps", "24 fps", "25 fps", "60 fps"), effectRow)
        controls.addView(effectRow)
        val strengthRow = row()
        strengthRow.addView(TextView(this).apply {
            text = "Effect window"; gravity = Gravity.CENTER_VERTICAL
        })
        blurStrength = spinner(listOf("1 frame","3 frames","5 frames","7 frames","9 frames"), strengthRow)
        blurStrength.setSelection(2)
        controls.addView(strengthRow)
        val codecRow = row()
        resolution = spinner(listOf("1080p", "4K (test)", "8K (test)"), codecRow)
        quality = spinner(listOf("Auto bitrate", "Low", "Medium", "High"), codecRow)
        controls.addView(codecRow)
        val codecsRow = row()
        codecOptions = VideoExporter.availableCodecs()
        codec = spinner(if (codecOptions.isEmpty()) listOf("No encoders") else codecOptions.map { it.second }, codecsRow)
        controls.addView(codecsRow)
        val stampRow = row()
        stampEnabled = CheckBox(this).apply { text = "Timestamp video" }
        stampRow.addView(stampEnabled)
        stampPattern = EditText(this).apply {
            isSingleLine = true; setText("yyyy-MM-dd HH:mm:ss")
        }
        stampRow.addView(stampPattern, LinearLayout.LayoutParams(0,-2,1f))
        controls.addView(stampRow)
        val stampOptions = row()
        stampPosition = spinner(listOf("Bottom Left","Bottom Right","Top Left","Top Right"),stampOptions)
        stampColor = spinner(listOf("White","Yellow","Black"),stampOptions)
        stampSize = EditText(this).apply {
            setText("44"); isSingleLine = true
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        stampOptions.addView(stampSize,LinearLayout.LayoutParams(0,-2,0.5f))
        controls.addView(stampOptions)
        val exportPreferences = getSharedPreferences("video_export", MODE_PRIVATE)
        val dayRange = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        exportFromDay = EditText(this).apply {
            hint = "From YYYY-MM-DD (optional)"; isSingleLine = true
            setText(exportPreferences.getString("from", ""))
            contentDescription = "Export from day"
        }
        exportThroughDay = EditText(this).apply {
            hint = "Through YYYY-MM-DD (optional)"; isSingleLine = true
            setText(exportPreferences.getString("through", ""))
            contentDescription = "Export through day"
        }
        dayRange.addView(exportFromDay); dayRange.addView(exportThroughDay)
        dayRange.addView(TextView(this).apply {
            text = "Local days: ${java.time.ZoneId.systemDefault()}. Effects restart each day. Imported photos use file time when capture time is unavailable."
            textSize = 12f
        })
        exportPerDay = CheckBox(this).apply {
            text = "One MP4 per local day"; contentDescription = "Separate video per day"
            isChecked = exportPreferences.getBoolean("perDay", true)
            setOnCheckedChangeListener { _, checked -> dayRange.visibility = if (checked) android.view.View.VISIBLE else android.view.View.GONE }
        }
        dayRange.visibility = if (exportPerDay.isChecked) android.view.View.VISIBLE else android.view.View.GONE
        controls.addView(exportPerDay); controls.addView(dayRange)
        val exportRow = row()
        button("Photos ZIP", exportRow) {
            if (captureArmed()) {
                refreshStatus("Stop capture before export")
            } else exportZip.launch("${selectedProject()?.name ?: "timelapse"}.zip")
        }
        button("MP4 export", exportRow) { exportVideo() }
        val moreExport = row()
        button("Import folder", moreExport) { importFolder.launch(null) }
        button("Images folder", moreExport) { exportSequence.launch(null) }
        controls.addView(moreExport)
        controls.addView(exportRow)
        controls.addView(TextView(this).apply {
            text = "ALPHA — More PRO modes are SPECIFIED, not yet available. Do not rely on unattended 7-day operation before device validation."
            textSize = 12f
        })
        setContentView(main)
        restoreMeteringSelection()
        button("Cancel video export", controls) {
            cancelExportRequested = true; refreshStatus("Cancelling video export; originals remain intact")
        }
        button("Test photo · save one original", controls) { startCapture(true) }.contentDescription = "Take test photo"
        button("Projects, gallery & planner", controls) { workspace.launch(Intent(this, WorkspaceActivity::class.java)) }
        button("Save capture diagnostics (.txt)", controls) {
            exportDiagnostics.launch("Timelapse-diagnostics.txt")
        }
        refreshStatus()
    }


    /** Capture-first layout. Detailed controls are still available in a scrollable bottom sheet. */
    private fun redesignUi() {
        val density = resources.displayMetrics.density
        fun dp(value:Int) = (value*density + 0.5f).toInt()
        val navy = Color.rgb(11,18,31)
        val panel = Color.rgb(24,34,51)
        val cyan = Color.rgb(73,205,244)
        val grey = Color.rgb(170,190,210)
        fun round(color:Int,radius:Int=14):GradientDrawable =
            GradientDrawable().apply {setColor(color);cornerRadius=dp(radius).toFloat()}
        fun text(s:String,sp:Float,color:Int):TextView = TextView(this).apply {
            text=s;textSize=sp;setTextColor(color);gravity=Gravity.CENTER_VERTICAL
        }
        val root=(findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as LinearLayout)
        val cameraView=root.getChildAt(0) as FrameLayout
        val currentStatus=root.getChildAt(1)
        val settingsScroll=root.getChildAt(2) as ScrollView
        root.removeAllViews()
        root.setBackgroundColor(navy)
        // Android 15 enforces edge-to-edge: respect real system status/nav bars.
        ViewCompat.setOnApplyWindowInsetsListener(root){view,insets->
            val bars=insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0,bars.top,0,bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
        window.statusBarColor=navy
        window.navigationBarColor=navy

        // Reuse existing bound controls: layout changes cannot disconnect the capture logic.
        val settingsControls=settingsScroll.getChildAt(0) as LinearLayout
        val formatLine=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        formatLine.addView(text("Original photo resolution",13f,Color.DKGRAY))
        photoResolution=Spinner(this)
        formatLine.addView(photoResolution,LinearLayout.LayoutParams(-1,dp(48)))
        formatLine.addView(text(
            "MAX is the largest JPEG that Android exposes to third-party apps, not necessarily Samsung's private sensor mode.",
            12f,Color.DKGRAY))
        settingsControls.addView(formatLine,4)
        val children=(0 until settingsControls.childCount).map {settingsControls.getChildAt(it)}
        settingsControls.removeAllViews()
        settingsControls.setPadding(dp(18),0,dp(18),dp(36))

        // On a phone, manual controls must never occupy four cramped horizontal columns.
        fun expandManual(index:Int) {
            val container=children.getOrNull(index) as? LinearLayout ?: return
            val originals=(0 until container.childCount).map{container.getChildAt(it)}
            container.removeAllViews()
            container.orientation=LinearLayout.VERTICAL
            val actionRow=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
            for(v in originals) {
                if(v is Button) {
                    v.isAllCaps=false
                    v.textSize=13f
                    v.setTextColor(Color.rgb(12,58,91))
                    v.background=round(Color.rgb(218,241,249),10)
                    actionRow.addView(v,LinearLayout.LayoutParams(0,dp(45),1f).apply {
                        rightMargin=dp(10)
                    })
                } else {
                    container.addView(v,LinearLayout.LayoutParams(-1,dp(54)))
                }
            }
            container.addView(actionRow,LinearLayout.LayoutParams(-1,dp(52)))
        }
        expandManual(9)
        expandManual(10)

        // Keep ALL original controls and their callbacks, but expose only one
        // logical group per screen, like a real camera application.
        val settingsRoot=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(249,251,254))
        }
        val titleRow=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
            setPadding(dp(20),dp(6),dp(20),dp(2))
        }
        titleRow.addView(text("SETTINGS",21f,Color.rgb(24,47,73)).apply {
            setTypeface(null,android.graphics.Typeface.BOLD)
        },LinearLayout.LayoutParams(0,dp(58),1f))
        val close=text("✕",26f,Color.rgb(41,65,84)).apply {
            gravity=Gravity.CENTER
            setOnClickListener{settingsDialog.dismiss()}
        }
        titleRow.addView(close,LinearLayout.LayoutParams(dp(48),dp(48)))
        settingsRoot.addView(titleRow)
        val tabRow=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            setPadding(dp(16),0,dp(16),dp(9))
        }
        val tabNames=listOf("CAPTURE","CAMERA","EXPORT")
        val tabButtons=mutableListOf<TextView>()
        for((idx,name) in tabNames.withIndex()) {
            val tab=text(name,13f,Color.rgb(46,80,104)).apply {
                gravity=Gravity.CENTER
                setTypeface(null,android.graphics.Typeface.BOLD)
                setPadding(0,dp(5),0,dp(5))
            }
            tabButtons.add(tab)
            tabRow.addView(tab,LinearLayout.LayoutParams(0,dp(52),1f).apply {
                rightMargin=dp(4)
            })
        }
        settingsRoot.addView(tabRow)
        fun showSettingsTab(which:Int) {
            settingsControls.removeAllViews()
            tabButtons.forEachIndexed { i,view ->
                view.setTextColor(if(i==which)Color.WHITE else Color.rgb(43,72,96))
                view.background=round(if(i==which)Color.rgb(22,116,161) else
                    Color.rgb(228,236,246),11)
            }
            val indices=when(which) {
                0 -> listOf(0,1,2)
                1 -> (3..10).toList()
                else -> (12 until children.size).toList()
            }
            for(i in indices) {
                val view=children[i]
                if(i==3 || i==5 || i==12) {
                    settingsControls.addView(
                        text(when(i){3->"LENS & PHOTO QUALITY";5->"FOCUS & EXPOSURE";else->"VIDEO & IMAGE EXPORT"},
                            15f,Color.rgb(25,62,94)).apply {
                            setTypeface(null,android.graphics.Typeface.BOLD)
                            setPadding(0,dp(18),0,dp(7))
                        },LinearLayout.LayoutParams(-1,dp(63))
                    )
                }
                settingsControls.addView(view,LinearLayout.LayoutParams(-1,-2).apply {
                    bottomMargin=dp(10)
                })
            }
            settingsScroll.post{settingsScroll.scrollTo(0,0)}
        }
        tabButtons.forEachIndexed{i,v -> v.setOnClickListener{showSettingsTab(i)}}
        showSettingsTab(0)
        settingsScroll.setBackgroundColor(Color.rgb(249,251,254))
        settingsRoot.addView(settingsScroll,LinearLayout.LayoutParams(-1,0,1f))
        settingsDialog=android.app.Dialog(this)
        settingsDialog.setContentView(settingsRoot)
        settingsDialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(-1,(resources.displayMetrics.heightPixels*.86f).toInt())
            setGravity(Gravity.BOTTOM)
        }
        val header=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
            setPadding(dp(18),dp(6),dp(15),dp(6))
        }
        header.addView(text("TIMELAPSE",19f,Color.WHITE).apply {
            setTypeface(null,android.graphics.Typeface.BOLD);letterSpacing=.10f
        },LinearLayout.LayoutParams(0,dp(46),1f))
        header.addView(text("+ PROJECT",13f,cyan).apply {
            background=round(panel)
            gravity=Gravity.CENTER
            setPadding(dp(14),0,dp(14),0)
            setOnClickListener{newProject()}
        },LinearLayout.LayoutParams(-2,dp(41)))
        root.addView(header,LinearLayout.LayoutParams(-1,dp(59)))

        photoBadge=text("PHOTO · MAX",12f,Color.WHITE).apply {
            setTypeface(null,android.graphics.Typeface.BOLD)
        }
        val topBadge=LinearLayout(this).apply{
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
            background=round(Color.argb(216,11,18,31),12)
            setPadding(dp(13),0,dp(13),0)
        }
        topBadge.addView(photoBadge,LinearLayout.LayoutParams(0,dp(42),1f))
        topBadge.addView(text("⊞ GRID",12f,cyan).apply {
            gravity=Gravity.CENTER
            setOnClickListener {
                targetOverlay.showGrid=!targetOverlay.showGrid
                this.text=if(targetOverlay.showGrid)"⊞ GRID ON" else "⊞ GRID"
            }
        },LinearLayout.LayoutParams(dp(82),dp(42)))
        cameraView.addView(topBadge,FrameLayout.LayoutParams(-1,dp(50),Gravity.TOP).apply {
            setMargins(dp(8),dp(8),dp(8),0)
        })
        val touchBar=LinearLayout(this).apply{
            gravity=Gravity.CENTER_VERTICAL;orientation=LinearLayout.HORIZONTAL
            setPadding(dp(8),dp(6),dp(8),dp(6))
            background=round(Color.argb(220,11,18,31),13)
        }
        fun quick(name:String,action:()->Unit){
            touchBar.addView(text(name,13f,Color.WHITE).apply{
                gravity=Gravity.CENTER
                background=round(panel,9)
                setOnClickListener{action()}
            },LinearLayout.LayoutParams(0,dp(42),1f).apply{marginEnd=dp(5)})
        }
        quick("◎  FOCUS") {
            focusMode.setSelection(0)
            targetOverlay.target=MeteringOverlay.Target.FOCUS
            refreshStatus("Tap the subject for focus")
        }
        quick("◉  EXPOSURE") {
            focusMode.setSelection(1)
            targetOverlay.target=MeteringOverlay.Target.EXPOSURE
            refreshStatus("Tap a different spot for exposure")
        }
        quick("TEST PHOTO") { startCapture(true) }
        touchBar.getChildAt(touchBar.childCount - 1).contentDescription = "Take test photo"
        cameraView.addView(touchBar,FrameLayout.LayoutParams(-1,dp(57),Gravity.BOTTOM).apply {
            setMargins(dp(10),0,dp(10),dp(9))
        })
        root.addView(cameraView,LinearLayout.LayoutParams(-1,0,1f))

        val bottom=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL; setPadding(dp(14),dp(8),dp(8),dp(16))
            setBackgroundColor(panel)
        }
        val overview=LinearLayout(this).apply{
            orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
        }
        val name=text("PROJECT •",11f,grey)
        overview.addView(name,LinearLayout.LayoutParams(dp(85),dp(40)))
        projectChip=text("NO PROJECT • Tap to choose",14f,Color.WHITE)
        overview.addView(projectChip,LinearLayout.LayoutParams(0,dp(40),1f))
        projectChip.contentDescription = "Open projects library"
        projectChip.setOnClickListener{workspace.launch(Intent(this, WorkspaceActivity::class.java))}
        bottom.addView(overview)
        val statusHolder=currentStatus as TextView
        statusHolder.setTextColor(grey)
        statusHolder.textSize=12f
        statusHolder.setPadding(dp(4),0,dp(4),0)
        bottom.addView(statusHolder,LinearLayout.LayoutParams(-1,dp(47)))
        val buttonRow=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
        }
        fun actionButton(title:String,highlight:Boolean,onTap:()->Unit):Button {
            val b=Button(this).apply{
                isAllCaps=false
                text=title;textSize=13f
                setTextColor(if(highlight) navy else Color.WHITE)
                background=round(if(highlight)cyan else Color.rgb(46,61,84))
                setOnClickListener{onTap()}
            }
            buttonRow.addView(b,LinearLayout.LayoutParams(0,dp(53),1f).apply{marginEnd=dp(7)})
            return b
        }
        mainRecordButton=actionButton("●  START",true){startCapture()}
        mainRecordButton.contentDescription = "Start capture"
        actionButton("■  STOP",false){stopCapture()}.contentDescription = "Stop capture"
        actionButton("⚙  SETTINGS",false){settingsDialog.show()}.contentDescription = "Capture settings"
        bottom.addView(buttonRow)
        root.addView(bottom,LinearLayout.LayoutParams(-1,-2))

        // On selection, report all sensor sizes; user can choose truly highest.
        projects.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, index: Int, id: Long) {
                val active = CaptureService.activeService
                if (active?.isCapturing == true && projectNames.getOrNull(index) != active.selectedProject) {
                    projectNames.indexOf(active.selectedProject).takeIf { it >= 0 }?.let { projects.setSelection(it) }
                    refreshStatus("Stop recording before switching projects"); return
                }
                loadProjectPreset(); updateProjectChip()
            }
        }
        lens.onItemSelectedListener=object:AdapterView.OnItemSelectedListener{
            override fun onNothingSelected(p:AdapterView<*>?){}
            override fun onItemSelected(p:AdapterView<*>?,v:android.view.View?,index:Int,id:Long){
                restoreMeteringSelection()
                updatePhotoResolutions()
                availableLenses.getOrNull(index)?.first?.let{service?.changePreviewLens(it)}
            }
        }
        updatePhotoResolutions()
        updateProjectChip()
    }

    private fun updateProjectChip() {
        if(::projectChip.isInitialized)
            projectChip.text=selectedProject()?.let { projectInfo[it.name]?.title ?: it.name } ?: "PROJECTS · Gallery & planner ›"
    }

    private fun showProjectBrowser() {
        val names=projectNames
        val options=if(names.isEmpty())arrayOf("No projects yet") else names.toTypedArray()
        android.app.AlertDialog.Builder(this).setTitle("Timelapse projects")
            .setItems(options){_,index->
                if(names.isNotEmpty() && index in names.indices){
                    projects.setSelection(index)
                    updateProjectChip()
                    refreshStatus("Selected " + names[index])
                }
            }
            .setPositiveButton("+ New project"){_,_->newProject()}
            .setNegativeButton("Close",null)
            .show()
    }

    private fun updatePhotoResolutions() {
        if(!::photoResolution.isInitialized || !::lens.isInitialized)return
        val lensId=availableLenses.getOrNull(lens.selectedItemPosition)?.first
        val manager=getSystemService(CAMERA_SERVICE) as CameraManager
        supportedPhotoSizes=if(lensId==null)emptyList() else PhotoResolutionCatalog.query(manager,lensId)
        val labels=if(supportedPhotoSizes.isEmpty())listOf("Automatic (camera default)") else
            supportedPhotoSizes.mapIndexed {i,v->
                (if(i==0)"MAX • " else "") + v.caption
            }
        val adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,labels)
        photoResolution.adapter=adapter
        val max=supportedPhotoSizes.firstOrNull()
        photoBadge.text=if(max==null)"PHOTO · AUTO" else "PHOTO · MAX " + max.caption
    }

    private fun requestCameraPreview() {
        if (ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED || service?.isCapturing==true ||
            service?.isPreviewing==true) return
        runCatching {
            val intent=Intent(this,CaptureService::class.java).apply {
                action=CaptureService.ACTION_PREVIEW
                val id=availableLenses.getOrNull(lens.selectedItemPosition)?.first
                if(id!=null)putExtra(CaptureService.EXTRA_CAMERA_ID,id)
            }
            ContextCompat.startForegroundService(this,intent)
        }.onFailure { refreshStatus("Camera preview unavailable: " + it.message) }
    }

    private fun startCapture(testPhoto: Boolean = false) {
        if (exporting || captureArmed()) { refreshStatus("Stop the current capture/file operation before starting another"); return }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            pendingTestShot = testPhoto
            startAfterPermission = true
            cameraPermission.launch(Manifest.permission.CAMERA); return
        }
        if (selectedProject() == null) newProject()
        val p = selectedProject() ?: return
        val intervalMillis = runCatching { CapturePlanner.intervalMillis(interval.text.toString()) }.getOrElse {
            refreshStatus("Interval must be 0.5–3600s, with at most three decimals"); return
        }
        try {
            val projectInfo = projectLibrary().read(p.name)
            val manualFrame = testPhoto || projectInfo.mode == ProjectLibrary.Mode.PHOTOLAPSE
            val previous = projectInfo.preset
            val preset = ProjectLibrary.Preset(intervalMillis, dayOnly.isChecked, timeStart.text.toString(),
                timeEnd.text.toString(), previous.weekdays, previous.maxFrames)
            projectLibrary().savePreset(p.name, preset)
            val intent = Intent(this, CaptureService::class.java).apply {
                action = CaptureService.ACTION_START
                putExtra(CaptureService.EXTRA_PROJECT, p.name)
                putExtra(CaptureService.EXTRA_INTERVAL, intervalMillis)
                putExtra(CaptureService.EXTRA_MAX_FRAMES, if (manualFrame) 1L else preset.maxFrames)
                putExtra(CaptureService.EXTRA_WEEKDAYS, preset.weekdays.toIntArray())
                supportedPhotoSizes.getOrNull(photoResolution.selectedItemPosition)?.let {
                    putExtra(CaptureService.EXTRA_PHOTO_WIDTH,it.width)
                    putExtra(CaptureService.EXTRA_PHOTO_HEIGHT,it.height)
                }
                availableLenses.getOrNull(lens.selectedItemPosition)?.first?.let {
                    putExtra(CaptureService.EXTRA_CAMERA_ID, it)
                }
                putExtra(CaptureService.EXTRA_DAY_ONLY, if (manualFrame) false else dayOnly.isChecked)
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


    private fun meterPrefsKey(): String {
        val project = selectedProject()?.name ?: "default"
        val lensId = if (::lens.isInitialized)
            availableLenses.getOrNull(lens.selectedItemPosition)?.first ?: "back"
            else "back"
        return "$project:$lensId"
    }

    /** Store user intent; PENDING does not assert the sensor accepted a metering region. */
    private fun saveMeteringSelection() {
        if (!::targetOverlay.isInitialized) return
        val prefs = getSharedPreferences("metering", MODE_PRIVATE)
        val key = meterPrefsKey()
        prefs.edit().apply {
            targetOverlay.focus?.let {
                putFloat("$key:afX",it.x); putFloat("$key:afY",it.y)
                putFloat("$key:afSize",it.size)
            }
            targetOverlay.exposure?.let {
                putFloat("$key:aeX",it.x); putFloat("$key:aeY",it.y)
                putFloat("$key:aeSize",it.size)
            }
            putBoolean("$key:afLock",keepFocus.isChecked)
            putBoolean("$key:aeLock",keepExposure.isChecked)
        }.apply()
    }

    private fun restoreMeteringSelection() {
        if (!::targetOverlay.isInitialized || !::keepFocus.isInitialized) return
        val prefs = getSharedPreferences("metering", MODE_PRIVATE)
        val key = meterPrefsKey()
        fun marker(prefix:String): MeteringOverlay.Marker? =
            if (prefs.contains("$key:${prefix}X"))
                MeteringOverlay.Marker(
                    prefs.getFloat("$key:${prefix}X",0.5f).coerceIn(0f,1f),
                    prefs.getFloat("$key:${prefix}Y",0.5f).coerceIn(0f,1f),
                    prefs.getFloat("$key:${prefix}Size",0.12f).coerceIn(0.02f,0.5f),
                    "PENDING"
                ) else null
        targetOverlay.restore(marker("af"),marker("ae"))
        keepFocus.isChecked = prefs.getBoolean("$key:afLock",true)
        keepExposure.isChecked = prefs.getBoolean("$key:aeLock",false)
    }

    private fun applyMeteringSelection(target: MeteringOverlay.Target, marker: MeteringOverlay.Marker) {
        val meter = service ?: run {
            targetOverlay.setFeedback(target,"PENDING")
            refreshStatus("Target saved; start capture to apply it")
            return
        }
        if(preview.width <= 0 || preview.height <= 0) {
            targetOverlay.setFeedback(target,"PENDING")
            return
        }
        val point = preview.meteringPointFactory.createPoint(
            marker.x * preview.width, marker.y * preview.height, marker.size)
        val mode = when(target) {
            MeteringOverlay.Target.FOCUS -> 0
            MeteringOverlay.Target.EXPOSURE -> 1
            MeteringOverlay.Target.BOTH -> 2
        }
        targetOverlay.setFeedback(target,"REQUESTED")
        meter.meterAt(point, mode,
            keepFocus.isChecked && target != MeteringOverlay.Target.EXPOSURE,
            keepExposure.isChecked && target != MeteringOverlay.Target.FOCUS) { success, message ->
            runOnUiThread {
                targetOverlay.setFeedback(target,if(success) "APPLIED" else "FAILED")
                refreshStatus(message)
            }
        }
    }



    private fun exportVideo() {
        if (exporting || captureArmed()) {
            refreshStatus("Stop capture/export before MP4 export"); return
        }
        val project = selectedProject() ?: run { refreshStatus("Choose a project"); return }
        val separateDays = exportPerDay.isChecked
        val from: java.time.LocalDate?
        val through: java.time.LocalDate?
        try {
            from = if (separateDays && exportFromDay.text.isNotBlank()) java.time.LocalDate.parse(exportFromDay.text.toString().trim()) else null
            through = if (separateDays && exportThroughDay.text.isNotBlank()) java.time.LocalDate.parse(exportThroughDay.text.toString().trim()) else null
            require(from == null || through == null || !from.isAfter(through)) { "From day must precede through day" }
        } catch (e: Exception) { refreshStatus("Invalid export date range: ${e.message}"); return }
        val (w,h) = listOf(1920 to 1080,3840 to 2160,7680 to 4320)[resolution.selectedItemPosition]
        val rate = listOf(30,24,25,60)[fps.selectedItemPosition]
        val selectedEffect = VideoEffect.entries[effect.selectedItemPosition]
        val bitrate = VideoExporter.bitrateFor(w,h,rate,quality.selectedItemPosition)
        val overlay = if (!stampEnabled.isChecked) null else {
            val color = listOf(Color.WHITE,Color.YELLOW,Color.BLACK)[stampColor.selectedItemPosition]
            val position = listOf(StampPosition.BOTTOM_LEFT,StampPosition.BOTTOM_RIGHT,
                StampPosition.TOP_LEFT,StampPosition.TOP_RIGHT)[stampPosition.selectedItemPosition]
            TimestampOverlay(stampPattern.text.toString(), stampSize.text.toString().toIntOrNull()?.coerceIn(12,300) ?: 44,color,position)
        }
        val mime = codecOptions.getOrNull(codec.selectedItemPosition)?.first
            ?: run { refreshStatus("No compatible encoder"); return }
        if (!VideoExporter.canEncode(w,h,rate,mime)) {
            refreshStatus("No compatible encoder for ${w}x${h}/${rate}fps"); return
        }
        val settings = VideoBatchExporter.Settings(w,h,rate,selectedEffect,
            listOf(1,3,5,7,9)[blurStrength.selectedItemPosition],bitrate,mime,overlay,projectInfo[project.name]?.title)
        val zone = java.time.ZoneId.systemDefault()
        getSharedPreferences("video_export", MODE_PRIVATE).edit().putBoolean("perDay", separateDays)
            .putString("from", exportFromDay.text.toString()).putString("through", exportThroughDay.text.toString()).apply()
        exporting = true; cancelExportRequested = false; exportStatus = "Planning video export…"
        refreshStatus(exportStatus)
        val app = applicationContext
        exporter.execute {
            try {
                // Enumerate/stat originals off the UI thread, after capture/import ownership is held.
                val photos = project.listFiles().orEmpty().filter {
                    it.isFile && it.extension.lowercase(Locale.ROOT) in setOf("jpg", "jpeg", "png")
                }.sortedBy { it.name }
                require(photos.isNotEmpty()) { "No original photos to export" }
                val parts = if (separateDays) ExportDayPlanner.plan(photos, zone, from, through)
                    else listOf(ExportDay(null, photos))
                require(parts.isNotEmpty()) { "No photos in this date range" }
                val selectedCount = parts.sumOf { it.files.size }
                val storage = android.os.StatFs((app.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: app.filesDir).absolutePath)
                require(storage.availableBytes >= ExportBudget.requiredFreeBytes(selectedCount, rate, bitrate, storage.totalBytes)) {
                    "Not enough free space for selected videos plus safety reserve"
                }
                val result = VideoBatchExporter.export(app, project.name, parts, zone.id, settings,
                    cancelled = { cancelExportRequested }) { item ->
                    exportStatus = "${item.day} · MP4 ${item.part}/${item.parts} · ${item.frame}/${item.frames} frames" +
                        if (item.published) " · saved" else ""
                    if (item.frame % 20 == 0 || item.published) runOnUiThread { if (!isDestroyed) refreshStatus(exportStatus) }
                }
                runOnUiThread { if (!isDestroyed) {
                    val seconds = String.format(Locale.US, "%.1f", result.elapsedMs / 1000.0)
                    val message = "Saved ${result.outputs.size} MP4s (${result.frames} frames) in $seconds s: ${result.relativePath}"
                    refreshStatus(message); android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
                } }
            } catch (e: Exception) {
                runOnUiThread { if (!isDestroyed) {
                    val message = if (e is java.util.concurrent.CancellationException) "Export cancelled: ${e.message}" else "Export failed: ${e.message}"
                    refreshStatus(message); android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
                } }
            } finally { exporting = false; exportStatus = null }
        }
    }
    private fun refreshInventory() {
        if (isDestroyed || inventoryExecutor.isShutdown) return
        val id = projectNames.getOrNull(projects.selectedItemPosition) ?: return
        val now = android.os.SystemClock.elapsedRealtime()
        if (inventoryProject != id) { inventoryProject = id; inventoryAt = 0; inventoryCount = 0 }
        if (service?.isCapturing == true || now - inventoryAt < 5000 || !inventoryPending.compareAndSet(false, true)) return
        inventoryExecutor.execute {
            try {
                val count = runCatching { projectLibrary().inventory(id).count }.getOrElse { return@execute }
                val scannedAt = android.os.SystemClock.elapsedRealtime()
                runOnUiThread { if (!isDestroyed && inventoryProject == id) { inventoryCount = count; inventoryAt = scannedAt } }
            } finally { inventoryPending.set(false) }
        }
    }

    private fun refreshStatus(message: String? = null) {
        val s=service
        refreshInventory()
        val selectedId = projectNames.getOrNull(projects.selectedItemPosition)
        val saved = if (s != null && s.selectedProject == selectedId && (s.isCapturing || s.lastCommitElapsed > inventoryAt)) s.savedCount else inventoryCount
        if (::status.isInitialized) status.text = message ?: exportStatus ?:
            ((s?.status ?: getSharedPreferences("timelapse_status", MODE_PRIVATE)
                .getString("last_state", "CAMERA READY")) + " · " +
                saved + " photos · " +
                (s?.failures ?: 0) + " errors" +
                (s?.takeIf { it.isCapturing }?.let { " · ${it.attempts} attempts · ${it.skippedSlots} skipped · max gap ${it.maxSuccessfulFrameGapMs?.let { gap -> "$gap ms" } ?: "—"}" } ?: "") +
                (s?.actualPhotoSize?.takeIf { it!="Not captured yet" }?.let { " · " + it } ?: ""))
        if(::mainRecordButton.isInitialized)
            mainRecordButton.text = when {
                s?.isCapturing != true -> if (projectInfo[projectNames.getOrNull(projects.selectedItemPosition)]?.mode == ProjectLibrary.Mode.PHOTOLAPSE) "+  FRAME" else "●  START"
                s.status.startsWith("NEEDS_USER_ACTION") -> "!  CAMERA ERROR"
                s.status.startsWith("RECORDING") && s.hasSessionFrame -> "●  RECORDING"
                s.status.startsWith("STARTING") -> "◌  STARTING"
                else -> "Ⅱ  WAITING"
            }
    }
    /** Hardware/Bluetooth remotes that send volume/camera media keys when UI is visible. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode in listOf(KeyEvent.KEYCODE_VOLUME_UP,KeyEvent.KEYCODE_CAMERA,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)) {
            if (event.repeatCount == 0) {
                if (service?.status?.startsWith("RECORDING") == true) {
                    service?.requestImmediateFrame()
                    refreshStatus("Remote shot requested")
                } else startCapture()
            }
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (event.repeatCount == 0) stopCapture()
            return true
        }
        return super.onKeyDown(keyCode,event)
    }

    override fun onDestroy() {
        inventoryExecutor.shutdown()
        exporter.shutdown()
        super.onDestroy()
    }
}
