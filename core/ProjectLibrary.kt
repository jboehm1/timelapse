package eu.example.constructionlapse

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException
import java.time.LocalTime
import java.util.Properties
import java.util.UUID

/** Filesystem project catalogue; originals keep stable paths, including in reversible trash. */
class ProjectLibrary(private val root: File) {
    enum class Mode { TIMELAPSE, PHOTOLAPSE }
    data class Preset(val intervalMs: Long = 20_000, val dayOnly: Boolean = false,
                      val from: String = "07:00", val until: String = "18:00",
                      val weekdays: Set<Int> = (1..7).toSet(), val maxFrames: Long = 0) {
        init {
            require(intervalMs in 500..3_600_000 && maxFrames in 0..10_000_000)
            require(weekdays.isNotEmpty() && weekdays.all { it in 1..7 })
            require(!dayOnly || LocalTime.parse(from) != LocalTime.parse(until))
            LocalTime.parse(from); LocalTime.parse(until)
        }
    }
    data class Project(val id: String, val title: String, val mode: Mode, val archived: Boolean,
                       val createdAt: Long, val preset: Preset, val directory: File, val metadataWarning: String? = null)
    data class Inventory(val count: Int, val bytes: Long, val last: File?, val recentSizes: List<Long>)
    private val idPattern = Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,95}")
    init { require(root.isDirectory || root.mkdirs()) }

    fun directory(id: String): File {
        require(idPattern.matches(id)) { "Invalid project identifier" }
        val file = File(root, id)
        require(file.canonicalFile.parentFile == root.canonicalFile) { "Project outside library" }
        return file
    }

    @Synchronized fun list(includeArchived: Boolean = false): List<Project> = root.listFiles().orEmpty()
        .filter { it.isDirectory && idPattern.matches(it.name) }
        .mapNotNull { runCatching { read(it.name) }.getOrNull() }
        .filter { includeArchived || !it.archived }.sortedByDescending { it.createdAt }

    @Synchronized fun read(id: String): Project {
        val dir = directory(id)
        require(dir.isDirectory) { "Project no longer available" }
        val props = Properties()
        val metadata = File(dir, ".project.properties")
        var warning: String? = null
        if (metadata.exists()) runCatching { metadata.inputStream().use { props.load(it) } }.onFailure {
            props.clear(); warning = "Project settings unreadable; defaults loaded. Originals preserved."
        }
        val preset = runCatching { Preset(props.getProperty("intervalMs", "20000").toLong(),
            props.getProperty("dayOnly", "false").toBoolean(), props.getProperty("from", "07:00"),
            props.getProperty("until", "18:00"), props.getProperty("weekdays", "1,2,3,4,5,6,7")
                .split(',').map { it.toInt() }.toSet(), props.getProperty("maxFrames", "0").toLong()) }.getOrElse {
            warning = "Project settings invalid; defaults loaded. Originals preserved."; Preset()
        }
        val mode = runCatching { Mode.valueOf(props.getProperty("mode", "TIMELAPSE")) }.getOrElse {
            warning = "Unknown project mode; originals preserved."; Mode.TIMELAPSE
        }
        return Project(id, props.getProperty("title", id), mode,
            props.getProperty("archived", "false").toBoolean(), props.getProperty("createdAt")?.toLongOrNull()
                ?: dir.lastModified(), preset, dir, warning)
    }

    @Synchronized fun create(title: String, mode: Mode = Mode.TIMELAPSE, preset: Preset = Preset()): Project {
        validateTitle(title)
        val id = "project_" + UUID.randomUUID().toString().replace("-", "")
        val dir = directory(id)
        check(dir.mkdir()) { "Cannot create project" }
        return write(Project(id, title.trim(), mode, false, System.currentTimeMillis(), preset, dir))
    }
    @Synchronized fun rename(id: String, title: String): Project {
        validateTitle(title)
        return write(read(id).copy(title = title.trim()))
    }
    @Synchronized fun savePreset(id: String, preset: Preset) = write(read(id).copy(preset = preset))
    @Synchronized fun archive(id: String, archived: Boolean, activeProject: String? = null): Project {
        require(!archived || id != activeProject) { "Stop this recording before moving it to trash" }
        return write(read(id).copy(archived = archived))
    }
    @Synchronized fun duplicatePreset(id: String): Project = read(id).let { create(it.title + " · copy", it.mode, it.preset) }

    fun frames(id: String): List<File> = directory(id).listFiles().orEmpty().filter {
        it.isFile && it.extension.lowercase() in setOf("jpg", "jpeg", "png")
    }.sortedBy { it.name }
    fun inventory(id: String): Inventory {
        val frames = frames(id)
        return Inventory(frames.size, frames.sumOf { it.length() }, frames.lastOrNull(), frames.takeLast(20).map { it.length() })
    }
    private fun validateTitle(title: String) {
        require(title.trim().length in 1..120 && title.none { it.isISOControl() }) { "Name must contain 1–120 printable characters" }
    }
    private fun write(project: Project): Project {
        val props = Properties().apply {
            setProperty("title", project.title); setProperty("mode", project.mode.name)
            setProperty("createdAt", project.createdAt.toString()); setProperty("archived", project.archived.toString())
            setProperty("intervalMs", project.preset.intervalMs.toString()); setProperty("dayOnly", project.preset.dayOnly.toString())
            setProperty("from", project.preset.from); setProperty("until", project.preset.until)
            setProperty("weekdays", project.preset.weekdays.sorted().joinToString(","))
            setProperty("maxFrames", project.preset.maxFrames.toString())
        }
        val temp = File(project.directory, ".project.properties.tmpmeta")
        val target = File(project.directory, ".project.properties")
        FileOutputStream(temp).use { props.store(it, "Timelapse project metadata v1"); it.fd.sync() }
        try { Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
        catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        return project
    }
}
