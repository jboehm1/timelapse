package eu.example.constructionlapse

import android.app.AlarmManager
import android.graphics.BitmapFactory
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraFilter
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CameraCharacteristics
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.MeteringPoint
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import android.util.Log
import android.util.Size
import androidx.camera.core.Preview.SurfaceProvider
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import java.io.File
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.ZoneId
import java.util.concurrent.Executors

/**
 * Proof-of-concept lifecycle-owned camera service, not a multi-day certified engine.
 * Start from visible Activity (Android 14+ CAMERA while-in-use permission).
 * All capture files belong to private app storage and are never automatically deleted.
 */
@androidx.annotation.OptIn(markerClass = [ExperimentalCamera2Interop::class])
class CaptureService : LifecycleService() {
    companion object {
        const val ACTION_START = "eu.example.constructionlapse.START"
        const val ACTION_PREVIEW = "eu.example.constructionlapse.PREVIEW"
        const val ACTION_RELEASE_PREVIEW = "eu.example.constructionlapse.RELEASE_PREVIEW"

        const val ACTION_STOP = "eu.example.constructionlapse.STOP"
        const val ACTION_SCHEDULE_ALARM = "eu.example.constructionlapse.SCHEDULE_ALARM"
        @Volatile var activeService: CaptureService? = null
            private set
        const val EXTRA_PROJECT = "project"
        const val EXTRA_INTERVAL = "interval"
        const val EXTRA_MAX_FRAMES = "max_frames"
        const val EXTRA_WEEKDAYS = "weekdays"
        const val EXTRA_CAMERA_ID = "camera_id"
        const val EXTRA_PHOTO_WIDTH = "photo_width"
        const val EXTRA_PHOTO_HEIGHT = "photo_height"

        const val EXTRA_DAY_ONLY = "day_only"
        const val EXTRA_FROM = "day_start"
        const val EXTRA_TO = "day_end"
        private const val CHANNEL_ID = "capture"
        private const val NOTIFICATION_ID = 45
        private const val STATUS_KEY = "last_state"
    }

    inner class LocalBinder : Binder() {
        fun service(): CaptureService = this@CaptureService
    }
    private val binder = LocalBinder()
    private val handler = Handler(Looper.getMainLooper())
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val retries = CameraRetryBudget()
    private val policy = SafetyPolicy()
    private val retryDelayMs = 5_000L
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var cameraBound = false
    private var pendingPreviewRebind = false
    private var previewProvider: SurfaceProvider? = null
    private var running = false
    private var previewOnly = false
    val isCapturing: Boolean get() = running && !previewOnly
    val isPreviewing: Boolean get() = running && previewOnly
    val hasSessionFrame: Boolean get() = sessionFrames > 0
    private var sessionFrames = 0L
    private var maxSessionFrames = 0L
    private var sessionGeneration = 0L
    private var bindGeneration = 0L
    private var firstFrameWatchdog: Runnable? = null
    private var lastFailure = ""
    var actualPhotoSize: String = "Not captured yet"
        private set
    private var photoTargetWidth = 0
    private var photoTargetHeight = 0

    private var inFlight = false
    private var lastAttemptElapsed = 0L
    private var captureGeneration = 0L
    private var latestThermalStatus = 0
    private var watchdog: Runnable? = null
    private var deadlineElapsed = 0L
    private var intervalMs = 20_000L
    private var captureCount = 0L
    private var nextId = 1L
    private var project: File? = null
    private var frameStore: FrameStore? = null
    private var requestedCameraId: String? = null
    private var schedule = DayWindow()
    private var wakeLock: PowerManager.WakeLock? = null
    private var wakeLockAcquiredAt = 0L
    private var errorCount = 0
    private var recoveryBlocked = false
    private var pendingRetryDelay: Long? = null
    private var pendingCameraRebind = false
    var status: String = "IDLE"
        private set
    val selectedProject: String get() = project?.name ?: "(none)"
    val savedCount: Long get() = captureCount
    val failures: Int get() = errorCount

    override fun onCreate() {
        super.onCreate()
        activeService = this
        CaptureDiagnostics.initialize(this)
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(NotificationChannel(CHANNEL_ID, "Timelapse capture",
                NotificationManager.IMPORTANCE_LOW))
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        trace("SERVICE_INTENT", intent?.action ?: "null")
        when (intent?.action) {
            ACTION_STOP -> stopCaptureAndService()
            ACTION_RELEASE_PREVIEW -> if(previewOnly) stopCaptureAndService()
            ACTION_PREVIEW -> {
                if (!running) {
                    try {
                        startForeground(NOTIFICATION_ID, notification("Camera preview"),
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
                        requestedCameraId = intent.getStringExtra(EXTRA_CAMERA_ID)
                        photoTargetWidth = 0; photoTargetHeight = 0
                        running = true
                        previewOnly = true
                        bindCamera()
                    } catch(e:Exception) {
                        stopCaptureAndService("NEEDS_USER_ACTION preview unavailable: ${e.message}")
                    }
                }
            }
            ACTION_START -> {
                // A running preview can upgrade into capture without closing the activity.
                if (running && !previewOnly) return START_NOT_STICKY
                val session = ++sessionGeneration
                sessionFrames = 0L
                lastFailure = ""
                captureGeneration++
                inFlight = false
                bindGeneration++
                if (previewOnly) {
                    try { cameraProvider?.unbindAll() } catch (_:Exception) {}
                    cameraBound = false
                    previewOnly = false
                }
                // No silent startup from BOOT_COMPLETED; callers must start from visible activity.
                try {
                    startForeground(NOTIFICATION_ID, notification("Starting"),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
                    val p = intent.getStringExtra(EXTRA_PROJECT) ?: error("Missing project")
                    val root = File(getExternalFilesDir("Pictures") ?: filesDir, "projects")
                    val dir = File(root, p)
                    require(dir.canonicalFile.parentFile == root.canonicalFile && dir.isDirectory)
                    project = dir
                    requestedCameraId = intent.getStringExtra(EXTRA_CAMERA_ID)
                    photoTargetWidth = intent.getIntExtra(EXTRA_PHOTO_WIDTH, 0)
                    photoTargetHeight = intent.getIntExtra(EXTRA_PHOTO_HEIGHT, 0)
                    actualPhotoSize = "Awaiting first JPEG"
                    intervalMs = intent.getLongExtra(EXTRA_INTERVAL, 20_000L).coerceIn(500L, 3_600_000L)
                    maxSessionFrames = intent.getLongExtra(EXTRA_MAX_FRAMES, 0).coerceIn(0, 10_000_000)
                    val days = (intent.getIntArrayExtra(EXTRA_WEEKDAYS)?.toSet() ?: (1..7).toSet())
                    require(days.isNotEmpty() && days.all { it in 1..7 }) { "Select at least one valid weekday" }
                    val dayOnly = intent.getBooleanExtra(EXTRA_DAY_ONLY, false)
                    val from = LocalTime.parse(intent.getStringExtra(EXTRA_FROM) ?: "07:00")
                    val until = LocalTime.parse(intent.getStringExtra(EXTRA_TO) ?: "18:00")
                    schedule = DayWindow(enabled = dayOnly, start = from, end = until,
                        zone = ZoneId.systemDefault(), days = days.map { java.time.DayOfWeek.of(it) }.toSet())
                    val store = FrameStore(dir)
                    frameStore = store
                    running = true
                    pendingRetryDelay = null
                    recoveryBlocked = false
                    errorCount = 0
                    state("INDEXING originals / recovering interrupted frames")
                    // Scan and SHA256 repair may be slow for multi-day 10k+ photos.
                    // Never block the Android main thread while rebuilding the project index.
                    cameraExecutor.execute {
                        val result = runCatching { store.recover() }
                        handler.post {
                            if (!running || session != sessionGeneration) return@post
                            if (result.isFailure) {
                                stopCaptureAndService("NEEDS_USER_ACTION storage recovery: ${result.exceptionOrNull()?.message}")
                                return@post
                            }
                            val recovered = result.getOrThrow()
                            nextId = recovered.nextId
                            captureCount = recovered.originals.toLong()
                            if (recovered.promoted > 0 || recovered.quarantined > 0) {
                                state("Recovered ${recovered.promoted} frames; ${recovered.quarantined} incomplete")
                            }
                            deadlineElapsed = SystemClock.elapsedRealtime()
                            bindCamera()
                            scheduleNext(0L)
                        }
                    }
                } catch (e: Exception) {
                    trace("START_FAILED", e.toString())
                    stopCaptureAndService("NEEDS_USER_ACTION: ${e.message}")
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun notification(message: String): Notification {
        val pending = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("Construction timelapse")
            .setContentText(message)
            .setContentIntent(pending)
            .addAction(android.R.drawable.ic_media_pause, "Stop", PendingIntent.getService(this, 7,
                Intent(this, CaptureService::class.java).apply { action = ACTION_STOP },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setOngoing(true)
            .build()
    }

    private fun trace(event: String, detail: String = "") {
        val line = "elapsed=${SystemClock.elapsedRealtime()} wall=${System.currentTimeMillis()} " +
            "session=$sessionGeneration project=${project?.name} camera=$requestedCameraId event=$event $detail"
        Log.i("TimelapseCapture", line)
        CaptureDiagnostics.record(line)
    }

    private fun state(value: String) {
        if (status == value) return
        status = value
        trace("STATE", value)
        getSharedPreferences("timelapse_status", MODE_PRIVATE).edit()
            .putString(STATUS_KEY, value).apply()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, notification(value))
    }

    private fun bindCamera() {
        val binding = ++bindGeneration
        state(if (previewOnly) "STARTING preview" else "STARTING binding camera; no new JPEG saved")
        trace("BIND_REQUEST", "requested=${photoTargetWidth}x${photoTargetHeight}")
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            if (!running || binding != bindGeneration) return@addListener
            try {
                val provider = future.get()
                cameraProvider = provider
                pendingCameraRebind = false
                imageCapture = ImageCapture.Builder().apply {
                    setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    setJpegQuality(95)
                    if (photoTargetWidth > 0 && photoTargetHeight > 0) {
                        val desired = Size(photoTargetWidth, photoTargetHeight)
                        setResolutionSelector(ResolutionSelector.Builder()
                            .setAllowedResolutionMode(ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE)
                            .setResolutionStrategy(ResolutionStrategy(desired,
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                            .build())
                    }
                }.build()
                if (connectUseCases()) {
                    state(if (previewOnly) "PREVIEW — choose AF / AE zones" else
                        "STARTING camera bound; awaiting first JPEG")
                }
            } catch (e: Exception) {
                cameraFailure("CAMERA_INIT", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /** Visible UI may attach/detach preview without owning camera lifecycle. */
    /** Lens changes apply only to PREVIEW; active captures keep a stable framing. */
    fun changePreviewLens(id: String) {
        if (!isPreviewing || inFlight || requestedCameraId == id) return
        requestedCameraId = id
        connectUseCases()
    }

    fun setPreviewSurface(surface: SurfaceProvider?) {
        if (previewProvider === surface) return
        previewProvider = surface
        if (running && cameraProvider != null) {
            if (inFlight) pendingPreviewRebind = true else connectUseCases()
        }
    }

    private fun connectUseCases(): Boolean {
        val p = cameraProvider ?: return false
        val image = imageCapture ?: return false
        try {
            p.unbindAll()
            val selector = requestedCameraId?.let { id ->
                CameraSelector.Builder().addCameraFilter(CameraFilter { infos ->
                    infos.filter { info -> Camera2CameraInfo.from(info).cameraId == id }
                }).build()
            } ?: CameraSelector.DEFAULT_BACK_CAMERA
            val preview = previewProvider?.let { surface ->
                Preview.Builder().build().also { it.setSurfaceProvider(surface) }
            }
            camera = if (preview == null)
                p.bindToLifecycle(this, selector, image)
            else
                p.bindToLifecycle(this, selector, image, preview)
            cameraBound = true
            trace("CAMERA_BOUND", "actual=${Camera2CameraInfo.from(camera!!.cameraInfo).cameraId}")
            return true
        } catch (e: Exception) {
            camera = null
            cameraBound = false
            cameraFailure("CAMERA_BIND", e)
            return false
        }
    }

    /**
     * Independent AF and AE regions. CameraX 1.6+ is used for focus/region placement,
     * Camera2 interop for AE lock, with support checking and explicit feedback.
     * Hardware AF success does not prove photographic sharpness.
     */
    @OptIn(ExperimentalCamera2Interop::class)
    fun meterAt(
        point: MeteringPoint, mode: Int, focusLock: Boolean, exposureLock: Boolean,
        result: (Boolean, String) -> Unit
    ) {
        val c = camera ?: run { result(false, "Camera not initialized"); return }
        val usesAf = mode != 1
        val usesAe = mode != 0
        if (usesAe && exposureLock) {
            val supported = runCatching {
                Camera2CameraInfo.from(c.cameraInfo).getCameraCharacteristic(
                    CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE
                ) == true
            }.getOrDefault(false)
            if(!supported) {
                result(false, "AE locking is not supported by this lens")
                return
            }
        }
        val builder = when(mode) {
            0 -> FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
            1 -> FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AE)
            else -> FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                .addPoint(point, FocusMeteringAction.FLAG_AE)
        }
        if (usesAf && focusLock) builder.disableAutoCancel()
        // For AE-only requests, no AF point is submitted: an existing AF lock is not rescanned.
        val action = builder.build()
        try {
            val metering = c.cameraControl.startFocusAndMetering(action)
            metering.addListener({
                try {
                    val focusSucceeded = metering.get().isFocusSuccessful
                    if(usesAf && !focusSucceeded) {
                        result(false, "AF did not report focus lock; subject may be soft")
                        return@addListener
                    }
                    if (usesAe) {
                        val options = CaptureRequestOptions.Builder().setCaptureRequestOption(
                            CaptureRequest.CONTROL_AE_LOCK, exposureLock
                        ).build()
                        val applied = Camera2CameraControl.from(c.cameraControl)
                            .addCaptureRequestOptions(options)
                        applied.addListener({
                            try {
                                applied.get()
                                result(true, if(exposureLock)
                                    "AF/AE region applied; AE lock requested (verify exposure visually)"
                                    else "AE region applied; auto exposure remains adaptive")
                            } catch(e: Exception) {
                                result(false,"AE state change failed: ${e.message}")
                            }
                        }, ContextCompat.getMainExecutor(this))
                    } else {
                        result(true, if (focusLock) "AF scan reported success; fixed until reset"
                            else "AF scan reported success; returns to continuous")
                    }
                } catch(e: Exception) {
                    result(false,"Metering request failed: ${e.message}")
                }
            }, ContextCompat.getMainExecutor(this))
        } catch(e: Exception) { result(false, e.message ?: "Camera metering failed") }
    }

    /** Actual Camera2 controls, never simulated image processing. */
    @OptIn(ExperimentalCamera2Interop::class)
    fun manualFocus(diopters: Float, result: (Boolean, String) -> Unit) {
        val current = camera ?: run { result(false,"Camera is not active");return }
        try {
            val maxD = Camera2CameraInfo.from(current.cameraInfo)
                .getCameraCharacteristic(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)
                ?: 0f
            if (maxD <= 0f || diopters !in 0f..maxD) {
                result(false,"Manual focus unsupported or out of range: 0–${maxD} diopters")
                return
            }
            val options = CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_OFF)
                .setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE,diopters)
                .build()
            val future = Camera2CameraControl.from(current.cameraControl).addCaptureRequestOptions(options)
            future.addListener({
                try {
                    future.get()
                    result(true,"Requested manual focus ${diopters} D (image sharpness not verified)")
                } catch(e:Exception) { result(false,"Manual focus failed: ${e.message}") }
            },ContextCompat.getMainExecutor(this))
        } catch(e:Exception) {result(false,"Manual focus unavailable: ${e.message}")}
    }

    @OptIn(ExperimentalCamera2Interop::class)
    fun resetAutofocus(result: (Boolean,String) -> Unit) {
        val current = camera ?: run {result(false,"Camera is not active");return}
        try {
            val options = CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE).build()
            val future = Camera2CameraControl.from(current.cameraControl)
                .addCaptureRequestOptions(options)
            future.addListener({
                try {
                    future.get()
                    current.cameraControl.cancelFocusAndMetering()
                    result(true,"Continuous AF requested")
                } catch(e:Exception){result(false,"Restore AF failed: ${e.message}")}
            },ContextCompat.getMainExecutor(this))
        } catch(e:Exception){result(false,"Cannot reset AF: ${e.message}")}
    }

    @OptIn(ExperimentalCamera2Interop::class)
    fun manualExposure(iso:Int, shutterMs:Float, result:(Boolean,String)->Unit) {
        val current = camera ?: run {result(false,"Camera is not active");return}
        try {
            val info = Camera2CameraInfo.from(current.cameraInfo)
            val caps = info.getCameraCharacteristic(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.toSet().orEmpty()
            if (CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR !in caps) {
                result(false,"Manual sensor exposure is not available on this lens");return
            }
            val isoRange = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
            val shutterRange = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
            val nanos = (shutterMs.toDouble()*1_000_000.0).toLong()
            if (isoRange == null || shutterRange == null ||
                !isoRange.contains(iso) || !shutterRange.contains(nanos)) {
                result(false,"ISO/shutter outside supported lens range");return
            }
            val options = CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_OFF)
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK,false)
                .setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY,iso)
                .setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME,nanos)
                .build()
            val future = Camera2CameraControl.from(current.cameraControl)
                .addCaptureRequestOptions(options)
            future.addListener({
                try {future.get();result(true,"Requested ISO $iso / ${shutterMs} ms")}
                catch(e:Exception){result(false,"Manual exposure failed: ${e.message}")}
            },ContextCompat.getMainExecutor(this))
        } catch(e:Exception){result(false,"Manual exposure unavailable: ${e.message}")}
    }

    @OptIn(ExperimentalCamera2Interop::class)
    fun resetAutoExposure(result:(Boolean,String)->Unit) {
        val current = camera ?: run {result(false,"Camera is not active");return}
        try {
            val options = CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON)
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK,false).build()
            val future = Camera2CameraControl.from(current.cameraControl)
                .addCaptureRequestOptions(options)
            future.addListener({
                try {future.get();result(true,"Adaptive auto exposure requested")}
                catch(e:Exception){result(false,"AE restore failed: ${e.message}")}
            },ContextCompat.getMainExecutor(this))
        } catch(e:Exception){result(false,"Cannot reset AE: ${e.message}")}
    }

    private val nextTick = Runnable { captureWhenAllowed() }
    private fun scheduleNext(delayMs: Long) {
        handler.removeCallbacks(nextTick)
        if (running) handler.postDelayed(nextTick, delayMs.coerceAtLeast(0))
    }

    private fun captureWhenAllowed() {
        if (!isCapturing) return
        val dir = project ?: return
        val power = getSystemService(POWER_SERVICE) as PowerManager
        val battery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val percent = (battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1).let { level ->
            val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            if (level < 0 || scale <= 0) 100 else (100 * level / scale).coerceIn(0, 100)
        }
        val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val stat = StatFs(dir.absolutePath)
        val thermal = power.currentThermalStatus
        latestThermalStatus = thermal
        val decision = policy.evaluate(
            SafetySnapshot(Instant.now(), thermal, stat.availableBytes,
                stat.totalBytes, percent, plugged != 0, recoveryBlocked),
            schedule
        )
        when (decision) {
            CaptureDecision.CAPTURE -> {
                state(if (hasSessionFrame) "RECORDING ${captureCount} saved, ${errorCount} errors"
                    else "STARTING no new JPEG saved")
                armFirstFrameDeadline()
                ensureWakeLock(power)
                if (!cameraBound && imageCapture != null) {
                    if (connectUseCases()) scheduleNext(2000)
                    return
                }
                if (inFlight) { scheduleNext(2_000); return }
                val now = SystemClock.elapsedRealtime()
                if (now < deadlineElapsed) { scheduleNext(deadlineElapsed - now); return }
                takePhoto()
            }
            CaptureDecision.NIGHT_STANDBY -> {
                disarmFirstFrameDeadline()
                state("NIGHT_STANDBY display may be OFF")
                releaseWakeLock()
                if (!inFlight && cameraBound) {
                    cameraProvider?.unbindAll()
                    camera = null
                    cameraBound = false
                }
                scheduleMorningWakeAlarm()
                // Android Doze may delay Handler ticks; AlarmManager also requests a wake.
                scheduleNext(60_000)
            }
            else -> {
                disarmFirstFrameDeadline()
                releaseWakeLock()
                if (decision == CaptureDecision.THERMAL_COOLDOWN && !inFlight && cameraBound) {
                    cameraProvider?.unbindAll()
                    cameraBound = false
                    camera = null
                }
                state(if (decision == CaptureDecision.NEEDS_USER_ACTION)
                    "NEEDS_USER_ACTION $lastFailure" else decision.name)
                scheduleNext(if (decision == CaptureDecision.THERMAL_COOLDOWN) 120_000 else 60_000)
            }
        }
    }

    private fun ensureWakeLock(power: PowerManager) {
        val now = SystemClock.elapsedRealtime()
        if (wakeLock?.isHeld == true && now - wakeLockAcquiredAt < 30L * 60L * 1000L) return
        releaseWakeLock()
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Timelapse:ActiveCapture")
        wakeLock?.setReferenceCounted(false)
        // Finite renewable lease: explicitly release when paused or stopped.
        wakeLock?.acquire(60L * 60L * 1000L)
        wakeLockAcquiredAt = now
    }

    private fun releaseWakeLock() {
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) {}
        wakeLock = null
    }

    fun requestImmediateFrame() {
        if (isCapturing && !inFlight) {
            deadlineElapsed = SystemClock.elapsedRealtime()
            scheduleNext(0)
        }
    }

    private fun armFirstFrameDeadline() {
        if (hasSessionFrame || firstFrameWatchdog != null) return
        val session = sessionGeneration
        val watch = Runnable {
            if (session == sessionGeneration && isCapturing && !hasSessionFrame) {
                trace("FIRST_JPEG_TIMEOUT", lastFailure)
                recoveryBlocked = true
                cameraBound = false
                cameraProvider?.unbindAll()
                captureGeneration++
                inFlight = false
                disarmWatchdog()
                releaseWakeLock()
                handler.removeCallbacks(nextTick)
                state("NEEDS_USER_ACTION no JPEG saved after 30s. $lastFailure Stop and retry; inspect capture log.")
            }
        }
        firstFrameWatchdog = watch
        handler.postDelayed(watch, 30_000L)
    }

    private fun disarmFirstFrameDeadline() {
        firstFrameWatchdog?.let { handler.removeCallbacks(it) }
        firstFrameWatchdog = null
    }

    private fun armWatchdog(generation: Long, pending: File) {
        watchdog?.let { handler.removeCallbacks(it) }
        val timeout = maxOf(15_000L, intervalMs * 2).coerceAtMost(120_000L)
        val watch = Runnable {
            if (inFlight && generation == captureGeneration) {
                inFlight = false
                // Preserve staging bytes: late CameraX callback may finish writing.
                // FrameStore will promote complete files or quarantine partial files
                // on next service start. Never rename a still-being-written file.
                cameraFailure("CAPTURE_TIMEOUT ${pending.name}")
                cameraBound = false
                try { cameraProvider?.unbindAll() } catch (_: Exception) {}
                advanceDeadline()
            }
        }
        watchdog = watch
        handler.postDelayed(watch, timeout)
    }

    private fun disarmWatchdog() {
        watchdog?.let { handler.removeCallbacks(it) }
        watchdog = null
    }

    private fun takePhoto() {
        val cameraCapture = imageCapture ?: run {
            if (!pendingCameraRebind) {
                pendingCameraRebind = true
                bindCamera()
            }
            cameraFailure("IMAGE_CAPTURE_UNAVAILABLE"); return
        }
        val dir = project ?: return
        inFlight = true
        lastAttemptElapsed = SystemClock.elapsedRealtime()
        val generation = ++captureGeneration
        val store = frameStore ?: run { inFlight = false; cameraFailure("FRAME_STORE_UNAVAILABLE"); return }
        val id = nextId++
        val base = "frame_${id.toString().padStart(10, '0')}_${System.currentTimeMillis()}"
        val pending = File(dir, "${base}.pending")
        val output = ImageCapture.OutputFileOptions.Builder(pending).build()
        trace("JPEG_REQUEST", "file=${pending.name}")
        armWatchdog(generation, pending)
        try {
            cameraCapture.takePicture(output, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                    // Decode only image bounds (no full bitmap allocated).
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(pending.absolutePath, bounds)
                                        val publication = runCatching {
                        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "JPEG does not decode" }
                        store.commit(pending, "${base}.jpg", System.currentTimeMillis())
                    }
                    publication.onSuccess {
                        trace("JPEG_COMMITTED", "file=${it.name} bytes=${it.length()} dimensions=${bounds.outWidth}x${bounds.outHeight}")
                    }.onFailure { trace("JPEG_COMMIT_FAILED", it.toString()) }
                    // Never delete a staging file after a failure: it might be
                    // valid but unjournaled and is reconciled on restart.
                    handler.post {
                        if (generation != captureGeneration || !inFlight) return@post
                        disarmWatchdog()
                        inFlight = false
                        if (publication.isSuccess) {
                            captureCount++
                            actualPhotoSize = "${bounds.outWidth} × ${bounds.outHeight}"
                            sessionFrames++
                            disarmFirstFrameDeadline()
                            state("RECORDING ${captureCount} saved · $actualPhotoSize")
                            retries.clearAfterVerifiedSuccess()
                            if (maxSessionFrames > 0 && sessionFrames >= maxSessionFrames) {
                                stopCaptureAndService("COMPLETED $sessionFrames new photos saved · $actualPhotoSize")
                                return@post
                            }
                            // Persist last successful project selection across process death.
                            getSharedPreferences("lapse", MODE_PRIVATE).edit()
                                .putString("lastProject", dir.name).apply()
                        } else cameraFailure("JPEG_COMMIT", publication.exceptionOrNull())
                        if (pendingPreviewRebind) {
                            pendingPreviewRebind = false
                            connectUseCases()
                        }
                        advanceDeadline()
                    }
                }
                override fun onError(exception: ImageCaptureException) {
                    // Leave staged data untouched for recovery and crash analysis.
                    handler.post {
                        if (generation != captureGeneration || !inFlight) return@post
                        disarmWatchdog()
                        inFlight = false
                        cameraFailure("JPEG_CALLBACK_${exception.imageCaptureError}", exception)
                        advanceDeadline()
                    }
                }
            })
        } catch (e: Exception) {
            disarmWatchdog()
            inFlight = false
            // Leave even partial captures for FrameStore recovery.
            cameraFailure("JPEG_REQUEST", e)
            advanceDeadline()
        }
    }

    private fun cameraFailure(code: String, error: Throwable? = null) {
        lastFailure = "$code: ${error?.message ?: "camera did not complete"}"
        trace("FAILURE", lastFailure)
        if (error != null) Log.e("TimelapseCapture", code, error)
        errorCount++
        val next = retries.failure(SystemClock.elapsedRealtime())
        if (next == null) {
            recoveryBlocked = true
            handler.removeCallbacks(nextTick)
            disarmFirstFrameDeadline()
            releaseWakeLock()
            state("NEEDS_USER_ACTION $lastFailure")
        } else {
            state("RECOVERING $lastFailure; retry in ${next / 1000}s")
            pendingRetryDelay = next
            scheduleNext(next)
        }
    }

    private fun advanceDeadline() {
        if (!running) return
        val retry = pendingRetryDelay
        if (retry != null) {
            pendingRetryDelay = null
            scheduleNext(retry)
            return
        }
        val now = SystemClock.elapsedRealtime()
        val safeInterval = policy.effectiveIntervalMs(intervalMs, latestThermalStatus)
        deadlineElapsed = TimelapseLogic.nextDeadlineMs(deadlineElapsed, now, safeInterval)
        scheduleNext(deadlineElapsed - now)
    }

    /** Schedule a best-effort wake, not a guarantee of exact 07:00 under Doze. */
    private fun scheduleMorningWakeAlarm() {
        if (!schedule.enabled) return
        val zone = schedule.zone
        val now = ZonedDateTime.now(zone)
        var morning = now.toLocalDate().atTime(schedule.start).atZone(zone)
        if (!morning.isAfter(now)) morning = morning.plusDays(1)
        val manager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, morning.toInstant().toEpochMilli(), wakeIntent())
    }

    private fun wakeIntent(): PendingIntent = PendingIntent.getBroadcast(this, 4,
        Intent(this, ScheduleReceiver::class.java).apply { action = ACTION_SCHEDULE_ALARM },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** Alarm receiver calls only into this still-active service; never cold-starts a camera FGS. */
    fun onScheduledWake() {
        if (running) scheduleNext(0)
    }

    private fun stopCaptureAndService(finalState: String = "STOPPED") {
        sessionGeneration++
        bindGeneration++
        captureGeneration++
        inFlight = false
        disarmFirstFrameDeadline()
        running = false
        previewOnly = false
        pendingRetryDelay = null
        handler.removeCallbacks(nextTick)
        disarmWatchdog()
        releaseWakeLock()
        try { cameraProvider?.unbindAll() } catch (_: Exception) {}
        cameraBound = false
        (getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(wakeIntent())
        imageCapture = null
        camera = null
        state(finalState)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        sessionGeneration++
        bindGeneration++
        captureGeneration++
        disarmFirstFrameDeadline()
        try { cameraProvider?.unbindAll() } catch (_: Exception) {}
        running = false
        handler.removeCallbacks(nextTick)
        releaseWakeLock()
        disarmWatchdog()
        cameraExecutor.shutdown()
        if (activeService === this) activeService = null
        super.onDestroy()
    }
}
