package com.waterall.domain.calculator

import com.waterall.domain.model.BarangayZone
import com.waterall.domain.model.SupplyWindow
import com.waterall.domain.model.UserMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ScheduleCountdownCalculatorTest {

    private val sampleDomesticSchedules = listOf(
        SupplyWindow("Mon", "Mon 09", 6, 0, 10, 0),
        SupplyWindow("Wed", "Wed 11", 5, 0, 9, 0),
        SupplyWindow("Fri", "Fri 13", 6, 0, 11, 0)
    )

    private val sampleCommercialSchedules = listOf(
        SupplyWindow("Tue", "Tue 10", 8, 0, 14, 0),
        SupplyWindow("Thu", "Thu 12", 8, 0, 14, 0)
    )

    private val sampleZone = BarangayZone(
        id = "zone-test",
        name = "Zone 4 — Brgy. San Isidro",
        domesticSchedules = sampleDomesticSchedules,
        commercialSchedules = sampleCommercialSchedules
    )

    @Test
    fun testFlowActiveDuringSupplyWindow() {
        // Monday at 07:30 AM falls squarely inside Mon 06:00 - 10:00
        val mondayMorning = LocalDateTime.of(2026, 10, 12, 7, 30, 0) // Oct 12, 2026 is Monday
        val result = ScheduleCountdownCalculator.calculate(sampleZone, UserMode.DOMESTIC, mondayMorning)

        assertEquals(ScheduleFlowState.FLOW_ACTIVE, result.flowState)
        assertNotNull(result.activeWindow)
        assertEquals("Mon", result.activeWindow?.dayOfWeek)

        // Remaining time to 10:00 AM = 2 hours and 30 minutes = 9000 seconds
        assertEquals(9000L, result.remainingSeconds)
        assertEquals(2.5, result.remainingHours, 0.001)
        assertEquals("02:30:00", result.formattedCountdown)
        assertEquals(LocalDateTime.of(2026, 10, 12, 10, 0, 0), result.targetDateTime)
        // Next upcoming window is Wednesday 05:00
        assertEquals("Wed", result.nextWindow?.dayOfWeek)
    }

    @Test
    fun testRationingActiveBeforeWindowOpens() {
        // Monday at 05:15 AM is before Monday 06:00 window
        val mondayEarly = LocalDateTime.of(2026, 10, 12, 5, 15, 0)
        val result = ScheduleCountdownCalculator.calculate(sampleZone, UserMode.DOMESTIC, mondayEarly)

        assertEquals(ScheduleFlowState.RATIONING_ACTIVE, result.flowState)
        assertNull(result.activeWindow)
        assertNotNull(result.nextWindow)
        assertEquals("Mon", result.nextWindow?.dayOfWeek)

        // 45 minutes until opening = 2700 seconds
        assertEquals(2700L, result.remainingSeconds)
        assertEquals("00:45:00", result.formattedCountdown)
        assertEquals(LocalDateTime.of(2026, 10, 12, 6, 0, 0), result.targetDateTime)
    }

    @Test
    fun testRationingActiveAfterWindowCloses() {
        // Monday at 10:30 AM is after Monday window has closed
        val mondayMidday = LocalDateTime.of(2026, 10, 12, 10, 30, 0)
        val result = ScheduleCountdownCalculator.calculate(sampleZone, UserMode.DOMESTIC, mondayMidday)

        assertEquals(ScheduleFlowState.RATIONING_ACTIVE, result.flowState)
        assertNull(result.activeWindow)
        // Next window is Wednesday 05:00 AM (in 42.5 hours)
        assertEquals("Wed", result.nextWindow?.dayOfWeek)
        assertEquals(LocalDateTime.of(2026, 10, 14, 5, 0, 0), result.targetDateTime)

        // Remaining seconds: from Mon 10:30 to Wed 05:00 = 42.5 hrs = 153000s
        assertEquals(153000L, result.remainingSeconds)
        assertEquals(42.5, result.remainingHours, 0.001)
        assertEquals("42:30:00", result.formattedCountdown)
    }

    @Test
    fun testRationingActiveWeekendWrapAround() {
        // Friday at 12:00 PM (after Friday 06:00-11:00 window)
        // Next window wraps to next Monday at 06:00 AM
        val fridayAfternoon = LocalDateTime.of(2026, 10, 16, 12, 0, 0) // Oct 16, 2026 is Friday
        val result = ScheduleCountdownCalculator.calculate(sampleZone, UserMode.DOMESTIC, fridayAfternoon)

        assertEquals(ScheduleFlowState.RATIONING_ACTIVE, result.flowState)
        assertEquals("Mon", result.nextWindow?.dayOfWeek)
        // Friday 12:00 to Monday 06:00 = 66 hours
        assertEquals(LocalDateTime.of(2026, 10, 19, 6, 0, 0), result.targetDateTime)
        assertEquals(66 * 3600L, result.remainingSeconds)
        assertEquals("66:00:00", result.formattedCountdown)
    }

    @Test
    fun testBoundaryTransitionAtExactStartAndEnd() {
        // At exact start time 06:00:00, flow becomes ACTIVE
        val exactStart = LocalDateTime.of(2026, 10, 12, 6, 0, 0)
        val startResult = ScheduleCountdownCalculator.calculate(sampleZone, UserMode.DOMESTIC, exactStart)
        assertEquals(ScheduleFlowState.FLOW_ACTIVE, startResult.flowState)
        assertEquals(4 * 3600L, startResult.remainingSeconds) // 4 hours remaining

        // At 1 second before end time 09:59:59, flow is still ACTIVE
        val beforeEnd = LocalDateTime.of(2026, 10, 12, 9, 59, 59)
        val beforeEndResult = ScheduleCountdownCalculator.calculate(sampleZone, UserMode.DOMESTIC, beforeEnd)
        assertEquals(ScheduleFlowState.FLOW_ACTIVE, beforeEndResult.flowState)
        assertEquals(1L, beforeEndResult.remainingSeconds)

        // At exact end time 10:00:00, flow closes and transitions deterministically to RATIONING_ACTIVE
        val exactEnd = LocalDateTime.of(2026, 10, 12, 10, 0, 0)
        val endResult = ScheduleCountdownCalculator.calculate(sampleZone, UserMode.DOMESTIC, exactEnd)
        assertEquals(ScheduleFlowState.RATIONING_ACTIVE, endResult.flowState)
        assertEquals(LocalDateTime.of(2026, 10, 14, 5, 0, 0), endResult.targetDateTime)
    }

    @Test
    fun testLaw2TemporalNonOverlapEnforcement() {
        val overlappingSchedules = listOf(
            SupplyWindow("Mon", "Mon 09", 6, 0, 10, 0),
            SupplyWindow("Mon", "Mon 09", 9, 30, 12, 0) // Overlaps 9:30 - 10:00
        )

        var caughtOverlap = false
        try {
            ScheduleCountdownCalculator.calculate(overlappingSchedules, LocalDateTime.now())
        } catch (e: IllegalArgumentException) {
            caughtOverlap = true
            assertTrue(e.message?.contains("Law 2 violation") == true)
        }
        assertTrue("Law 2 violation must trigger exception on overlapping supply windows", caughtOverlap)
    }

    @Test
    fun testEmptyScheduleHandling() {
        val result = ScheduleCountdownCalculator.calculate(emptyList(), LocalDateTime.now())
        assertEquals(ScheduleFlowState.RATIONING_ACTIVE, result.flowState)
        assertEquals(0L, result.remainingSeconds)
        assertEquals("00:00:00", result.formattedCountdown)
        assertNull(result.targetDateTime)
    }
}
