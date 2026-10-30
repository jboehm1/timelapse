package eu.example.constructionlapse

import org.junit.Assert.*
import org.junit.Test

class ThermalTelemetryTest {
    @Test fun severePausesImmediatelyAndOnlySustainedLowTemperatureResumes() {
        val gate = ThermalRecoveryGate(180_000)
        assertFalse(gate.paused(0, 0))
        assertTrue(gate.paused(100, 3))
        assertTrue(gate.paused(200, 1))
        assertTrue(gate.paused(180_199, 0))
        assertFalse(gate.paused(180_200, 0))
    }
    @Test fun moderateOrNewHeatRestartsRecoveryWindow() {
        val gate = ThermalRecoveryGate(1000)
        assertTrue(gate.paused(0, 6))
        assertTrue(gate.paused(100, 0))
        assertTrue(gate.paused(1000, 2))
        assertTrue(gate.paused(1100, 0))
        assertTrue(gate.paused(1500, 3))
        assertTrue(gate.paused(1600, 1))
        assertFalse(gate.paused(2600, 0))
    }
    @Test fun counterMeasuresSkippedActiveSlotsWithoutCatchUp() {
        assertEquals(0L, TimelapseLogic.skippedDeadlines(1000, 1200, 500))
        assertEquals(1L, TimelapseLogic.skippedDeadlines(1000, 1500, 500))
        assertEquals(4L, TimelapseLogic.skippedDeadlines(1000, 3400, 500))
        assertEquals(3500L, TimelapseLogic.nextDeadlineMs(1000, 3400, 500))
        assertEquals(0L, TimelapseLogic.skippedDeadlines(5000, 4000, 500))
    }
    @Test fun morningAlarmSkipsExcludedWeekend() {
        val window = DayWindow(days = setOf(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.TUESDAY,
            java.time.DayOfWeek.WEDNESDAY, java.time.DayOfWeek.THURSDAY, java.time.DayOfWeek.FRIDAY), enabled = true)
        assertEquals(java.time.Instant.parse("2026-11-02T06:00:00Z"), window.nextOpeningAfter(java.time.Instant.parse("2026-10-30T18:00:00Z")))
    }
    @Test fun springGapStartsAtFirstActuallyAllowedInstant() {
        val window = DayWindow(enabled = true, start = java.time.LocalTime.of(2,30), end = java.time.LocalTime.of(4,0),
            days = setOf(java.time.DayOfWeek.SUNDAY))
        assertEquals(java.time.Instant.parse("2027-03-28T01:00:00Z"), window.nextOpeningAfter(java.time.Instant.parse("2027-03-28T00:00:00Z")))
    }
    @Test fun autumnOverlapCanUseSecondOpening() {
        val window = DayWindow(enabled = true, start = java.time.LocalTime.of(2,30), end = java.time.LocalTime.of(3,30),
            days = setOf(java.time.DayOfWeek.SUNDAY))
        assertEquals(java.time.Instant.parse("2026-10-25T01:30:00Z"), window.nextOpeningAfter(java.time.Instant.parse("2026-10-25T01:15:00Z")))
    }
    @Test fun entirelyMissingSpringWindowIsSkipped() {
        val window = DayWindow(enabled = true, start = java.time.LocalTime.of(2,30), end = java.time.LocalTime.of(3,0),
            days = setOf(java.time.DayOfWeek.SUNDAY))
        assertEquals(java.time.Instant.parse("2027-04-04T00:30:00Z"), window.nextOpeningAfter(java.time.Instant.parse("2027-03-28T00:00:00Z")))
    }

}
