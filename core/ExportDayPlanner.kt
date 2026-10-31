package eu.example.constructionlapse

import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class ExportDay(val day: LocalDate?, val files: List<File>)

object ExportDayPlanner {
    private val captured = Regex("frame_[0-9]+_([0-9]+)\\.[^.]+", RegexOption.IGNORE_CASE)
    fun captureMillis(file: File): Long = captured.matchEntire(file.name)?.groupValues?.get(1)
        ?.toLongOrNull() ?: file.lastModified()

    /** Calendar boundaries use the selected zone, including DST. Preserve source sequence within each day. */
    fun plan(files: List<File>, zone: ZoneId, from: LocalDate? = null, through: LocalDate? = null): List<ExportDay> {
        require(from == null || through == null || !from.isAfter(through)) { "From day must precede through day" }
        return files.groupBy { Instant.ofEpochMilli(captureMillis(it)).atZone(zone).toLocalDate() }
            .toSortedMap().filterKeys { (from == null || !it.isBefore(from)) && (through == null || !it.isAfter(through)) }
            .map { (day, originals) -> ExportDay(day, originals) }
    }
}
