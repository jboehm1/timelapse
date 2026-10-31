package eu.example.constructionlapse

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

fun main() {
    val zone = ZoneId.of("Europe/Berlin")
    val weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    val day = DayWindow(true, LocalTime.of(7, 0), LocalTime.of(18, 0), weekdays, zone)
    fun t(local: String) = java.time.LocalDateTime.parse(local).atZone(zone).toInstant()
    check(!day.contains(t("2026-10-30T06:59:59")))
    check(day.contains(t("2026-10-30T07:00:00")))
    check(day.contains(t("2026-10-30T17:59:59")))
    check(!day.contains(t("2026-10-30T18:00:00")))
    check(!day.contains(t("2026-10-31T08:00:00")))
    check(!day.contains(t("2026-11-01T08:00:00")))
    check(day.contains(t("2026-11-02T08:00:00")))
    val overnight = DayWindow(true, LocalTime.of(20, 0), LocalTime.of(3, 0), setOf(DayOfWeek.FRIDAY), zone)
    check(overnight.contains(t("2026-10-30T22:00:00")))
    check(overnight.contains(t("2026-10-31T02:59:59")))
    check(!overnight.contains(t("2026-10-31T03:00:00")))
    check(!overnight.contains(t("2026-10-31T22:00:00")))
    // Europe/Berlin DST fall-back: repeated 02:xx is within the window for each instant.
    val dst = DayWindow(true, LocalTime.of(1, 0), LocalTime.of(4, 0), DayOfWeek.values().toSet(), zone)
    check(dst.contains(Instant.parse("2026-10-25T00:30:00Z")))
    check(dst.contains(Instant.parse("2026-10-25T01:30:00Z")))
    val policy = SafetyPolicy()
    val ok = SafetySnapshot(t("2026-10-30T08:00:00"), 0, 2_000_000_000L, 20_000_000_000L, 90, false)
    check(policy.evaluate(ok, day) == CaptureDecision.CAPTURE)
    check(policy.evaluate(ok.copy(wallClock=t("2026-10-30T20:00:00")), day) == CaptureDecision.NIGHT_STANDBY)
    check(policy.evaluate(ok.copy(thermalStatus=3), day) == CaptureDecision.THERMAL_COOLDOWN)
    check(policy.evaluate(ok.copy(thermalStatus=6), day) == CaptureDecision.THERMAL_COOLDOWN)
    check(policy.evaluate(ok.copy(availableBytes=1000), day) == CaptureDecision.STORAGE_PAUSED)
    // Reserve is max(512 MiB, 5% of the volume): pause below the boundary,
    // resume exactly at it, and keep critical thermal protection highest priority.
    val storageReserve = maxOf(512L * 1024L * 1024L, (ok.volumeBytes * 0.05).toLong())
    check(policy.evaluate(ok.copy(availableBytes=storageReserve - 1L), day) == CaptureDecision.STORAGE_PAUSED)
    check(policy.evaluate(ok.copy(availableBytes=storageReserve), day) == CaptureDecision.CAPTURE)
    check(policy.evaluate(ok.copy(availableBytes=storageReserve - 1L, thermalStatus=3), day) == CaptureDecision.THERMAL_COOLDOWN)
    check(policy.evaluate(ok.copy(batteryPercent=10), day) == CaptureDecision.BATTERY_PAUSED)
    check(policy.evaluate(ok.copy(batteryPercent=10, isCharging=true), day) == CaptureDecision.CAPTURE)
    check(policy.evaluate(ok.copy(cameraRecoveryExhausted=true), day) == CaptureDecision.NEEDS_USER_ACTION)
    check(policy.effectiveIntervalMs(20_000, 2) == 40_000L)
    check(policy.effectiveIntervalMs(20_000, 1) == 20_000L)
    val retries = CameraRetryBudget()
    check(retries.failure(0) == 1000L)
    check(retries.failure(1) == 2000L)
    check(retries.failure(2) == 4000L)
    check(retries.failure(3) == 8000L)
    check(retries.failure(4) == null)
    check(retries.failure(30L * 60L * 1000L + 10) == 1000L)
    retries.clearAfterVerifiedSuccess()
    check(retries.failure(30L * 60L * 1000L + 11) == 1000L)
    println("PASS: capture policy checks (day/night, DST, safety, bounded retries)")
}
