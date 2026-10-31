package eu.example.constructionlapse

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** Compose workspace. Camera remains in its independently tested service/Activity. */
class WorkspaceActivity : ComponentActivity() {
    private val library by lazy { ProjectLibrary(File(getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: filesDir, "projects")) }
    private var revision by mutableIntStateOf(0)
    override fun onResume() { super.onResume(); revision++ }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CaptureDiagnostics.initialize(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFF6DE0C5), onPrimary = Color(0xFF002E26),
                secondary = Color(0xFFB1C9EB), background = Color(0xFF0D141C), surface = Color(0xFF131E2A),
                surfaceVariant = Color(0xFF203040), onSurface = Color(0xFFE4EDF7))) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { Workspace() }
            }
        }
    }
    private fun openCamera(id: String) {
        val active = CaptureService.activeService
        if (active?.isCapturing == true && active.selectedProject != id) {
            android.widget.Toast.makeText(this, "Stop recording before switching projects", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        getSharedPreferences("lapse", MODE_PRIVATE).edit().putString("lastProject", id).apply()
        // Return a selection to the existing camera Activity; never duplicate its capture engine.
        setResult(RESULT_OK, Intent().putExtra("project", id))
        finish()
    }

    data class Row(val project: ProjectLibrary.Project, val inventory: ProjectLibrary.Inventory)
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable private fun Workspace() {
        var rows by remember { mutableStateOf<List<Row>>(emptyList()) }
        var loading by remember { mutableStateOf(true) }
        var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
        var planner by rememberSaveable { mutableStateOf(false) }
        var filter by rememberSaveable { mutableStateOf("All") }
        var query by rememberSaveable { mutableStateOf("") }
        var create by remember { mutableStateOf(false) }
        var createBusy by remember { mutableStateOf(false) }
        var createError by remember { mutableStateOf<String?>(null) }
        var rename by remember { mutableStateOf<ProjectLibrary.Project?>(null) }
        var trash by remember { mutableStateOf<ProjectLibrary.Project?>(null) }
        var error by remember { mutableStateOf<String?>(null) }
        var createName by remember { mutableStateOf("") }
        var createMode by remember { mutableStateOf(ProjectLibrary.Mode.TIMELAPSE) }
        val scope = rememberCoroutineScope()
        fun operation(action: () -> Unit) { scope.launch {
            runCatching { withContext(Dispatchers.IO) { action() } }.onFailure { error = it.message; android.util.Log.e("TimelapseWorkspace", "Project operation failed", it) }
            revision++
        } }
        LaunchedEffect(revision) {
            loading = true
            runCatching { withContext(Dispatchers.IO) { library.list(true).map { Row(it, library.inventory(it.id)) } } }
                .onSuccess { rows = it }.onFailure { error = it.message }
            loading = false
        }
        val selected = rows.firstOrNull { it.project.id == selectedId }
        BackHandler(selectedId != null || planner) {
            if (planner) planner = false else selectedId = null
        }
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = { TopAppBar(title = {
                Column {
                    Text(if (planner) "Capture planner" else selected?.project?.title ?: "Your projects", fontWeight = FontWeight.Bold)
                    Text(if (planner) "Space, cadence & playback" else "TIMELAPSE STUDIO", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                }
            }, navigationIcon = { IconButton(onClick = {
                if (planner) planner = false else if (selectedId != null) selectedId = null else finish()
            }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }, actions = {
                IconButton(onClick = { revision++ }) { Icon(Icons.Default.Refresh, "Refresh projects") }
                if (!planner) IconButton(onClick = { planner = true }) { Icon(Icons.Default.Settings, "Open capture planner") }
            }) },
            floatingActionButton = { if (selected == null && !planner) FloatingActionButton(onClick = { create = true }) {
                Icon(Icons.Default.Add, "Create project")
            } }
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                if (error != null) Card(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(error!!, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                    TextButton(onClick = { error = null }) { Text("Dismiss") }
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                when {
                    planner -> PlannerPanel(selected?.project, selected?.inventory, onSave = { preset, completed ->
                        selected?.let { row -> scope.launch {
                            runCatching { withContext(Dispatchers.IO) { library.savePreset(row.project.id, preset) } }
                                .onSuccess { completed("Preset saved. Applies next time this project starts.") }
                                .onFailure { completed("Save failed: ${it.message}") }
                            revision++
                        } }
                    })
                    selected != null -> ProjectPanel(selected, onCamera = { openCamera(selected.project.id) },
                        onRename = { rename = selected.project }, onDuplicate = { operation { library.duplicatePreset(selected.project.id) } },
                        onTrash = { trash = selected.project }, onRestore = { operation { library.archive(selected.project.id, false) } },
                        onPlanner = { planner = true })
                    else -> {
                        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(16.dp),
                            label = { Text("Search projects") }, singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, null) })
                        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("All", "Timelapse", "Photolapse", "Trash").forEach { value ->
                                FilterChip(filter == value, { filter = value }, label = { Text(value, style = MaterialTheme.typography.labelMedium) })
                            }
                        }
                        val visible = rows.filter { row ->
                            row.project.archived == (filter == "Trash") && row.project.title.contains(query, true) &&
                                (filter !in listOf("Timelapse", "Photolapse") || row.project.mode.name.equals(filter, true))
                        }
                        if (visible.isEmpty() && !loading) Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.List, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(16.dp))
                            Text(if (filter == "Trash") "Nothing in trash" else "Make time visible", style = MaterialTheme.typography.headlineSmall)
                            Text(if (filter == "Trash") "Originals stay on device until restored." else "Create a project. Keep every original. Export when ready.",
                                Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium)
                            if (filter != "Trash") FilledTonalButton(onClick = { create = true }, Modifier.padding(top = 24.dp)) { Text("Create your first project") }
                        }
                        LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(visible, key = { it.project.id }) { row ->
                                Card(onClick = { selectedId = row.project.id }, shape = RoundedCornerShape(20.dp)) {
                                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Thumbnail(row.inventory.last, Modifier.size(88.dp))
                                        Column(Modifier.weight(1f).padding(start = 16.dp)) {
                                            Text(row.project.title, fontWeight = FontWeight.SemiBold, maxLines = 2)
                                            Text("${row.inventory.count} photos · ${bytes(row.inventory.bytes)}", style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(if (row.project.archived) "In trash · originals retained" else if (row.project.mode == ProjectLibrary.Mode.PHOTOLAPSE) "Photolapse · manual frames" else "Timelapse · ${row.project.preset.intervalMs / 1000.0}s interval",
                                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        }
                                        Icon(Icons.Default.KeyboardArrowRight, null)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (create) AlertDialog(onDismissRequest = { if (!createBusy) create = false }, title = { Text("A new perspective") }, text = {
            Column {
                OutlinedTextField(createName, { createName = it }, label = { Text("Project name") }, singleLine = true,
                    modifier = Modifier.semantics { contentDescription = "New project name" })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProjectLibrary.Mode.entries.forEach { mode ->
                        FilterChip(createMode == mode, { createMode = mode }, label = { Text(if (mode == ProjectLibrary.Mode.TIMELAPSE) "Timelapse" else "Photolapse") },
                            modifier = Modifier.semantics { contentDescription = "Mode ${mode.name}" })
                    }
                }
                Text("Photolapse keeps manually added frames in the same original-preserving project format.", style = MaterialTheme.typography.bodySmall)
                createError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }, confirmButton = { TextButton(enabled = createName.isNotBlank() && !createBusy, onClick = {
            val title = createName; val mode = createMode
            createBusy = true; createError = null
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { library.create(title, mode) } }
                    .onSuccess { project ->
                        selectedId = project.id
                        create = false; createName = ""
                        revision++
                    }
                    .onFailure {
                        createError = it.message ?: "Cannot create project"
                        android.util.Log.e("TimelapseWorkspace", "Project creation failed", it)
                    }
                createBusy = false
            }
        }) { Text(if (createBusy) "Creating…" else "Create") } }, dismissButton = { TextButton(enabled = !createBusy, onClick = { create = false }) { Text("Cancel") } })
        rename?.let { project ->
            var title by remember(project.id) { mutableStateOf(project.title) }
            AlertDialog(onDismissRequest = { rename = null }, title = { Text("Rename project") }, text = {
                OutlinedTextField(title, { title = it }, label = { Text("Project name") }, singleLine = true)
            }, confirmButton = { TextButton(onClick = { operation { library.rename(project.id, title) }; rename = null }) { Text("Save name") } },
                dismissButton = { TextButton(onClick = { rename = null }) { Text("Cancel") } })
        }
        trash?.let { project -> AlertDialog(onDismissRequest = { trash = null }, title = { Text("Move to trash?") }, text = {
            Text("${project.title} will be hidden from active projects. All originals stay on device and you can restore the project. This does not free disk space.")
        }, confirmButton = { TextButton(onClick = {
            operation { library.archive(project.id, true, CaptureService.activeService?.takeIf { it.isCapturing }?.selectedProject) }
            trash = null; selectedId = null
        }) { Text("Move to trash") } }, dismissButton = { TextButton(onClick = { trash = null }) { Text("Keep project") } }) }
    }

    @Composable private fun Thumbnail(file: File?, modifier: Modifier = Modifier) {
        if (file == null) Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Create, null, tint = MaterialTheme.colorScheme.primary)
        } else AsyncImage(file, "Saved original thumbnail", modifier, contentScale = ContentScale.Crop)
    }

    @Composable private fun ProjectPanel(row: Row, onCamera: () -> Unit, onRename: () -> Unit, onDuplicate: () -> Unit,
                                         onTrash: () -> Unit, onRestore: () -> Unit, onPlanner: () -> Unit) {
        var frames by remember(row.project.id, row.inventory.count) { mutableStateOf<List<File>>(emptyList()) }
        var preview by remember { mutableStateOf<File?>(null) }
        var visible by remember(row.project.id) { mutableIntStateOf(96) }
        LaunchedEffect(row.project.id, row.inventory.count) { frames = withContext(Dispatchers.IO) { library.frames(row.project.id).asReversed() } }
        Column(Modifier.fillMaxSize()) {
            Card(Modifier.padding(16.dp).fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.project.metadataWarning?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Text("${row.inventory.count} original photos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("${bytes(row.inventory.bytes)} stored · originals preserved", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (row.project.archived) Button(onClick = onRestore, Modifier.fillMaxWidth()) { Text("Restore project") }
                    else Button(onClick = onCamera, Modifier.fillMaxWidth()) { Icon(Icons.Default.Create, null); Spacer(Modifier.width(8.dp)); Text("Open camera & export") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onRename, Modifier.semantics { contentDescription = "Rename project" }) { Text("Rename") }
                        TextButton(onClick = onDuplicate, Modifier.semantics { contentDescription = "Copy project preset" }) { Text("Copy preset") }
                        TextButton(onClick = onPlanner, Modifier.semantics { contentDescription = "Open project planner" }) { Text("Plan") }
                    }
                    if (!row.project.archived) TextButton(onClick = onTrash) { Icon(Icons.Default.Delete, null); Text("Move to trash") }
                }
            }
            if (frames.isEmpty()) Text("No saved images yet. Open the camera and take a test photo.", Modifier.padding(24.dp))
            else LazyVerticalGrid(GridCells.Adaptive(104.dp), Modifier.weight(1f), contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(frames.take(visible), key = { it.name }) { file ->
                    Box(Modifier.aspectRatio(1f).clickable { preview = file }) { Thumbnail(file, Modifier.fillMaxSize()) }
                }
                if (visible < frames.size) item { TextButton(onClick = { visible += 96 }) { Text("Load more") } }
            }
        }
        preview?.let { file -> AlertDialog(onDismissRequest = { preview = null }, title = { Text("Saved original") }, text = {
            Column { AsyncImage(file, "Saved original image", Modifier.fillMaxWidth().height(320.dp), contentScale = ContentScale.Fit)
                Text("${file.name}\n${bytes(file.length())}", style = MaterialTheme.typography.bodySmall) }
        }, confirmButton = { TextButton(onClick = { preview = null }) { Text("Close preview") } }) }
    }

    @Composable private fun PlannerPanel(project: ProjectLibrary.Project?, inventory: ProjectLibrary.Inventory?, onSave: (ProjectLibrary.Preset, (String) -> Unit) -> Unit) {
        val initial = project?.preset ?: ProjectLibrary.Preset()
        var interval by rememberSaveable(project?.id) { mutableStateOf((initial.intervalMs / 1000.0).toString()) }
        var hours by rememberSaveable { mutableStateOf("8") }
        var days by rememberSaveable { mutableStateOf("7") }
        var fps by rememberSaveable { mutableStateOf("30") }
        var mb by rememberSaveable { mutableStateOf("8") }
        var unlimited by rememberSaveable { mutableStateOf(initial.maxFrames == 0L) }
        var frameLimit by rememberSaveable(project?.id) { mutableStateOf(if (initial.maxFrames == 0L) "1000" else initial.maxFrames.toString()) }
        var dayOnly by rememberSaveable(project?.id) { mutableStateOf(initial.dayOnly) }
        var from by rememberSaveable(project?.id) { mutableStateOf(initial.from) }
        var until by rememberSaveable(project?.id) { mutableStateOf(initial.until) }
        var weekdays by remember { mutableStateOf(initial.weekdays) }
        var note by remember { mutableStateOf<String?>(null) }
        val stat = remember { StatFs((getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: filesDir).absolutePath) }
        val actualHours = runCatching {
            if (project == null) hours.toDouble() else if (!dayOnly) 24.0 else {
                val seconds = java.time.Duration.between(java.time.LocalTime.parse(from), java.time.LocalTime.parse(until)).seconds
                require(seconds != 0L) { "Daily window must have nonzero duration" }
                (if (seconds < 0) seconds + 86400 else seconds) / 3600.0
            }
        }
        val estimate = runCatching { CapturePlanner.estimate(CapturePlanner.intervalMillis(interval), actualHours.getOrThrow(), days.toInt(), fps.toDouble(),
            (mb.toDouble() * 1_000_000).toLong(), inventory?.recentSizes.orEmpty(), stat.availableBytes, stat.totalBytes, maxFrames = if (unlimited) 0 else frameLimit.toLong().also { require(it > 0) }) }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(project?.title ?: "Explore before shooting", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Nominal cadence. A slow camera skips overdue slots; it never queues a catch-up burst.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField("Interval (seconds)", interval, { interval = it }, Modifier.weight(1f))
                NumberField("Active hours/day", if (project == null) hours else actualHours.getOrNull()?.toString() ?: "", { hours = it }, Modifier.weight(1f), enabled = project == null)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField("Active days", days, { days = it }, Modifier.weight(1f))
                NumberField("Playback FPS", fps, { fps = it }, Modifier.weight(1f))
            }
            if (project != null) Text(if (dayOnly) "Active hours follow your daily window." else "Continuous capture runs 24h/day. Enable a daily window below for work hours.", style = MaterialTheme.typography.bodySmall)
            if (project?.mode == ProjectLibrary.Mode.PHOTOLAPSE) Text("Photolapse records one frame per action. Cadence figures are planning examples, not an automatic schedule.", style = MaterialTheme.typography.bodySmall)
            if (inventory?.recentSizes.isNullOrEmpty()) NumberField("Estimated MB/photo", mb, { mb = it }, Modifier.fillMaxWidth())
            estimate.onSuccess { result ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(if (unlimited) "UNLIMITED SESSION · projections" else "LIMITED SESSION · projections", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Metric("Photos per active day", "${result.framesPerDay}")
                        Metric(if (unlimited) "$days active-day projection" else "$frameLimit-frame session", "${result.projectedFrames} photos · ${bytes(result.photoBytes)}")
                        Metric("Video at $fps FPS", duration(result.videoSeconds))
                        Metric("Video at 10 Mbps", "≈ ${bytes(result.videoBytes)}")
                        HorizontalDivider()
                        Metric("Safe storage runway", String.format(Locale.US, "≈ %.1f active days", result.safeRemainingDays))
                        Text((if (result.measured) "Median of ${inventory?.recentSizes?.size} recent originals" else "Manual size estimate; refine after first capture") +
                            ". Reserves 512 MiB or 5% of volume plus 20% JPEG-size allowance. Video/effect size varies.", style = MaterialTheme.typography.bodySmall)
                        if (result.photoBytes > result.usableBytes) Text("Projected photos exceed available storage.", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                }
            }.onFailure { Text(it.message ?: "Enter valid values", color = MaterialTheme.colorScheme.error) }
            if (project != null) {
                Row(verticalAlignment = Alignment.CenterVertically) { Switch(unlimited, { unlimited = it }, Modifier.semantics { contentDescription = "Unlimited recording" }); Text("  Unlimited recording") }
                if (!unlimited) NumberField("Stop after NEW frames", frameLimit, { frameLimit = it }, Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) { Switch(dayOnly, { dayOnly = it }, Modifier.semantics { contentDescription = "Daily time window" }); Text("  Daily time window") }
                if (dayOnly) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(from, { from = it }, Modifier.weight(1f), label = { Text("From HH:mm") }, singleLine = true)
                        OutlinedTextField(until, { until = it }, Modifier.weight(1f), label = { Text("Until HH:mm") }, singleLine = true)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { index, label ->
                            FilterChip(index + 1 in weekdays, { weekdays = if (index + 1 in weekdays) weekdays - (index + 1) else weekdays + (index + 1) },
                                label = { Text(label) }, modifier = Modifier.weight(1f))
                        }
                    }
                }
                Button(onClick = {
                    runCatching { ProjectLibrary.Preset(CapturePlanner.intervalMillis(interval), dayOnly, from, until, weekdays,
                        if (unlimited) 0 else frameLimit.toLong().also { require(it > 0) }) }
                        .onSuccess { preset -> note = "Saving…"; onSave(preset) { message -> note = message } }
                        .onFailure { note = it.message }
                }, Modifier.fillMaxWidth()) { Text("Save capture preset") }
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            Text("Diagnostic build · S21 screen-OFF and long sessions remain unverified.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
        }
    }
    @Composable private fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier, enabled: Boolean = true) {
        OutlinedTextField(value, onChange, modifier.semantics { contentDescription = label }, label = { Text(label) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), enabled = enabled)
    }
    @Composable private fun Metric(label: String, value: String) {
        Column { Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
    }
    private fun bytes(value: Long) = when { value >= 1_000_000_000 -> String.format(Locale.US, "%.2f GB", value / 1e9)
        value >= 1_000_000 -> String.format(Locale.US, "%.1f MB", value / 1e6)
        else -> String.format(Locale.US, "%.0f kB", value / 1e3) }
    private fun duration(seconds: Double): String { val total = seconds.toLong(); return "${total / 60}m ${total % 60}s" }
}
