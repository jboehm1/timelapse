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
    private lateinit var targetOverlay: MeteringOverlay
    private lateinit var keepFocus: CheckBox
    private lateinit var keepExposure: CheckBox
    private var selectionRadius = 0.12f
    private lateinit var lens: Spinner
    private var availableLenses: List<Pair<String,String>> = emptyList()
    private lateinit var effect: Spinner
    private lateinit var fps: Spinner
    private lateinit var quality: Spinner
    private lateinit var codec: Spinner
    private var codecOptions: List<Pair<String,String>> = emptyList()
    private lateinit var resolution: Spinner
    private lateinit var stampEnabled: CheckBox
    private lateinit var stampPattern: EditText
    private lateinit var stampPosition: Spinner
    private lateinit var stampColor: Spinner
    private lateinit var stampSize: EditText
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

    private val importFolder = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { tree ->
        if (tree == null) return@registerForActivityResult
        if (exporting || (service?.status?.startsWith("CAPTURING") == true)) {
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
                var nextId = (dir.listFiles()?.mapNotNull {
                    it.name.substringAfter("frame_", "").substringBefore("_").toLongOrNull()
                }?.maxOrNull() ?: 0L) + 1L
                for ((name,id) in items) {
                    val ext = if (name.endsWith(".png",true)) "png" else "jpg"
                    val target = File(dir, "frame_${(nextId++).toString().padStart(10,'0')}_import.$ext")
                    val partial = File(dir, "${target.name}.partial")
                    try {
                        val source = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                        contentResolver.openInputStream(source)?.use { input ->
                            partial.outputStream().use { output -> input.copyTo(output) }
                        } ?: throw IOException("Cannot read $name")
                        if (partial.length() < 50 || !partial.renameTo(target))
                            throw IOException("Failed to complete $name")
                        count++
                    } catch (e: Exception) {
                        partial.delete()
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
        if (service?.status?.startsWith("CAPTURING") == true) {
            refreshStatus("Stop capture before exporting"); return@registerForActivityResult
        }
        val frames = frameFiles()
        if (frames.isEmpty()) { refreshStatus("No frames to export"); return@registerForActivityResult }
        exporting = true
        exporter.execute {
            var count = 0
            try {
                val parent = DocumentsContract.buildDocumentUriUsingTree(
                    tree, DocumentsContract.getTreeDocumentId(tree))
                for (file in frames) {
                    val mime = if(file.name.endsWith(".png",true)) "image/png" else "image/jpeg"
                    val item = DocumentsContract.createDocument(
                        contentResolver,parent,mime,file.name)
                        ?: throw IOException("Cannot create ${file.name}")
                    contentResolver.openOutputStream(item)?.use { output ->
                        file.inputStream().use { input -> input.copyTo(output) }
                    } ?: throw IOException("Cannot write ${file.name}")
                    count++
                    if(count % 25 == 0) runOnUiThread { refreshStatus("Exported $count/${frames.size}") }
                }
                runOnUiThread { refreshStatus("Exported $count individual images") }
            } catch (e: Exception) {
                runOnUiThread { refreshStatus("Export interrupted after $count frames: ${e.message}") }
            } finally { exporting = false }
        }
    }

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
        it.isFile && (it.name.endsWith(".jpg", true) || it.name.endsWith(".jpeg", true) || it.name.endsWith(".png", true))
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
        val exportRow = row()
        button("Photos ZIP", exportRow) {
            if (service?.status?.startsWith("CAPTURING") == true) {
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
                availableLenses.getOrNull(lens.selectedItemPosition)?.first?.let {
                    putExtra(CaptureService.EXTRA_CAMERA_ID, it)
                }
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
        if (exporting || service?.status?.startsWith("CAPTURING") == true) {
            refreshStatus("Stop capture/export before MP4 export"); return
        }
        val photos = frameFiles()
        if (photos.size < 2) { refreshStatus("At least 2 frames needed"); return }
        val (w,h) = listOf(1920 to 1080,3840 to 2160,7680 to 4320)[resolution.selectedItemPosition]
        val rate = listOf(30,24,25,60)[fps.selectedItemPosition]
        val effectSelected = VideoEffect.entries[effect.selectedItemPosition]
        val bitrate = VideoExporter.bitrateFor(w,h,rate,quality.selectedItemPosition)
        val overlay = if (!stampEnabled.isChecked) null else {
            val color = listOf(Color.WHITE,Color.YELLOW,Color.BLACK)[stampColor.selectedItemPosition]
            val position = listOf(StampPosition.BOTTOM_LEFT,StampPosition.BOTTOM_RIGHT,
                StampPosition.TOP_LEFT,StampPosition.TOP_RIGHT)[stampPosition.selectedItemPosition]
            TimestampOverlay(stampPattern.text.toString(),
                stampSize.text.toString().toIntOrNull()?.coerceIn(12,300) ?: 44,color,position)
        }
        val mime = codecOptions.getOrNull(codec.selectedItemPosition)?.first
            ?: run { refreshStatus("No compatible encoder"); return }
        if (!VideoExporter.canEncode(w,h,rate,mime)) {
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
                    VideoExporter.encode(photos,pfd.fileDescriptor,w,h,rate,effectSelected,5,bitrate,mime,overlay) {
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
    /** Hardware/Bluetooth remotes that send volume/camera media keys when UI is visible. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode in listOf(KeyEvent.KEYCODE_VOLUME_UP,KeyEvent.KEYCODE_CAMERA,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)) {
            if (event.repeatCount == 0) {
                if (service?.status?.startsWith("CAPTURING") == true) {
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
        exporter.shutdown()
        super.onDestroy()
    }
}
