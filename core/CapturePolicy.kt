package eu.example.constructionlapse

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.ArrayDeque

/** Pure platform-independent decisions. It does not acquire an Android camera. */
enum class CaptureDecision { CAPTURE, NIGHT_STANDBY, THERMAL_COOLDOWN, STORAGE_PAUSED, BATTERY_PAUSED, NEEDS_USER_ACTION }

data class DayWindow(
    val enabled: Boolean = false,
    val start: LocalTime = LocalTime.of(7, 0),
    val end: LocalTime = LocalTime.of(18, 0),
    val days: Set<DayOfWeek> = DayOfWeek.values().toSet(),
    val zone: ZoneId = ZoneId.of("Europe/Berlin")
) {
    init { require(start != end || !enabled) { "A schedule needs a nonzero window" } }

    /** Crossing-midnight windows are associated with the previous starting day. */
    fun contains(instant: Instant): Boolean {
        if (!enabled) return true
        val local = instant.atZone(zone)
        val time = local.toLocalTime()
        if (start < end) return local.dayOfWeek in days && time >= start && time < end
        return if (time >= start) local.dayOfWeek in days
            else time < end && local.minusDays(1).dayOfWeek in days
    }
}

data class SafetySnapshot(
    val wallClock: Instant,
    val thermalStatus: Int,
    val availableBytes: Long,
    val volumeBytes: Long,
    val batteryPercent: Int,
    val isCharging: Boolean,
    val cameraRecoveryExhausted: Boolean = false
)

data class SafetyPolicy(
    val reserveBytes: Long = 512L * 1024L * 1024L,
    val reserveFraction: Double = 0.05,
    val minUnpluggedBattery: Int = 20
) {
    init {
        require(reserveBytes >= 0 && reserveFraction in 0.0..1.0)
        require(minUnpluggedBattery in 0..100)
    }

    fun evaluate(snapshot: SafetySnapshot, schedule: DayWindow): CaptureDecision {
        if (snapshot.cameraRecoveryExhausted) return CaptureDecision.NEEDS_USER_ACTION
        // Check safety even at night; high thermal states must never silently clear.
        if (snapshot.thermalStatus >= 3) return CaptureDecision.THERMAL_COOLDOWN
        if (snapshot.volumeBytes <= 0 || snapshot.availableBytes < 0 ||
            snapshot.availableBytes < maxOf(reserveBytes, (snapshot.volumeBytes * reserveFraction).toLong())) {
            return CaptureDecision.STORAGE_PAUSED
        }
        if (!snapshot.isCharging && snapshot.batteryPercent in 0 until minUnpluggedBattery) {
            return CaptureDecision.BATTERY_PAUSED
        }
        if (!schedule.contains(snapshot.wallClock)) return CaptureDecision.NIGHT_STANDBY
        return CaptureDecision.CAPTURE
    }

    /** In moderate thermal throttling we may reduce capture frequency rather than overheat. */
    fun effectiveIntervalMs(baseIntervalMs: Long, thermalStatus: Int): Long {
        require(baseIntervalMs >= 1000L)
        return if (thermalStatus == 2) Math.multiplyExact(baseIntervalMs, 2L) else baseIntervalMs
    }
}

/** Time-based bounded retries, independent of UI/Android lifecycle. */
class CameraRetryBudget(private val maxFailures: Int = 5, private val windowMs: Long = 30L * 60L * 1000L) {
    private val failures = ArrayDeque<Long>()

    init { require(maxFailures >= 1 && windowMs > 0) }

    fun failure(nowElapsedMs: Long): Long? {
        require(nowElapsedMs >= 0L)
        while (failures.isNotEmpty() && nowElapsedMs - failures.peekFirst() >= windowMs) failures.removeFirst()
        failures.addLast(nowElapsedMs)
        if (failures.size >= maxFailures) return null // manual intervention
        val exponent = (failures.size - 1).coerceAtMost(8)
        return (1_000L shl exponent).coerceAtMost(300_000L)
    }

    fun clearAfterVerifiedSuccess() = failures.clear()
}
