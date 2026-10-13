package com.waterall.domain.laws

import com.waterall.domain.calculator.RunoutEstimator
import com.waterall.domain.calculator.ScheduleCountdownCalculator
import com.waterall.domain.calculator.ScheduleFlowState
import com.waterall.domain.model.BarangayZone
import com.waterall.domain.model.GroundTruthReport
import com.waterall.domain.model.StorageInventory
import com.waterall.domain.model.SupplyWindow
import com.waterall.domain.model.TapFlowStatus
import com.waterall.domain.model.UserMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * Domain Invariant Law Test Suite
 *
 * Enforces non-negotiable invariants defined in LAWS.md:
 * - Law 1: Conservation of Water & Non-Negative Storage / Deficit
 * - Law 2: Rationing Window Chronology & Non-Overlapping Windows
 * - Law 3: Discrete Container Quantization
 * - Law 4: Ground-Truth Report Validity & Temporal Integrity
 */
class DomainLawsTest : LawsSpecificationStub {

    @Test
    override fun verifyLaw1ConservationOfWater() {
        // Test 1: Capacity clamping (stored cannot exceed total capacity)
        val inventory = StorageInventory(
            blueDrums200L = 3,       // 600L
            overheadTanks1000L = 1,  // 1000L
            gallons20L = 5,          // 100L -> total = 1700L
            fillPercentage = 100.0
        )
        assertEquals(1700.0, inventory.totalCapacityLiters, 0.001)
        assertEquals(1700.0, inventory.currentStoredVolumeLiters, 0.001)

        val halfFullInventory = inventory.copy(fillPercentage = 50.0)
        assertEquals(850.0, halfFullInventory.currentStoredVolumeLiters, 0.001)

        // Test 2: Deterministic Runout Time & Non-negative Deficit
        // Stored: 400L (2 drums full). Burn rate: 400L/day (16.666 L/hr). Next supply: 36 hours.
        val smallReserve = StorageInventory(blueDrums200L = 2, fillPercentage = 100.0)
        val result = RunoutEstimator.calculate(
            inventory = smallReserve,
            dailyBurnRateLiters = 400.0,
            hoursUntilNextSupply = 36.0
        )

        // Runout hours = (400L / (400 / 24)) = 24.0 hours
        assertEquals(24.0, result.runoutHours, 0.001)
        assertTrue(result.isDepletedBeforeNextSupply)
        // Deficit: Next supply is in 36 hrs, runs out at 24 hrs. Deficit duration = 12 hrs.
        // 12 hrs * (400 / 24) = 200.0 Liters shortfall.
        assertEquals(200.0, result.deficitLiters, 0.001)
        assertTrue("Deficit must never be negative", result.deficitLiters >= 0.0)

        // Test 3: Zero burn rate yields infinite runout and zero deficit
        val zeroBurnResult = RunoutEstimator.calculate(
            inventory = smallReserve,
            dailyBurnRateLiters = 0.0,
            hoursUntilNextSupply = 48.0
        )
        assertTrue(zeroBurnResult.runoutHours.isInfinite())
        assertEquals(0.0, zeroBurnResult.deficitLiters, 0.001)
        assertFalse(zeroBurnResult.isDepletedBeforeNextSupply)
    }

    @Test
    override fun verifyLaw2RationingWindowChronology() {
        // Law 2.1: Strict positive duration (end > start)
        val validWindow = SupplyWindow(
            dayOfWeek = "Mon",
            dateLabel = "Mon 09",
            startHour = 6,
            startMinute = 0,
            endHour = 10,
            endMinute = 0
        )
        assertEquals(240, validWindow.durationMinutes)
        assertEquals("6:00 AM – 10:00 AM", validWindow.formattedRange)

        // Invalid window with end < start must throw IllegalArgumentException
        var caughtViolation = false
        try {
            SupplyWindow(
                dayOfWeek = "Tue",
                dateLabel = "Tue 10",
                startHour = 14,
                startMinute = 0,
                endHour = 11,
                endMinute = 0
            )
        } catch (e: IllegalArgumentException) {
            caughtViolation = true
        }
        assertTrue("Law 2.1 violation must trigger exception on non-positive duration", caughtViolation)

        // Law 2.2: Temporal Non-Overlap within a schedule
        val nonOverlappingSchedule = listOf(
            SupplyWindow("Mon", "Mon 09", 6, 0, 10, 0),
            SupplyWindow("Wed", "Wed 11", 5, 0, 9, 0)
        )
        // Valid disjoint schedules pass validation without exception
        ScheduleCountdownCalculator.validateNonOverlapping(nonOverlappingSchedule)

        val overlappingSchedule = listOf(
            SupplyWindow("Mon", "Mon 09", 6, 0, 10, 0),
            SupplyWindow("Mon", "Mon 09", 8, 0, 12, 0)
        )
        var caughtOverlap = false
        try {
            ScheduleCountdownCalculator.validateNonOverlapping(overlappingSchedule)
        } catch (e: IllegalArgumentException) {
            caughtOverlap = true
        }
        assertTrue("Law 2.2 violation must trigger exception on overlapping windows", caughtOverlap)

        // Law 2.3: Monotonic Countdown & Deterministic State Transitions
        val schedules = listOf(
            SupplyWindow("Mon", "Mon 09", 6, 0, 10, 0),
            SupplyWindow("Wed", "Wed 11", 5, 0, 9, 0)
        )

        // Test 2.3a: During active flow, countdown ticks toward endTime
        val duringFlow = LocalDateTime.of(2026, 10, 12, 8, 0, 0) // Mon 8:00 AM
        val flowResult = ScheduleCountdownCalculator.calculate(schedules, duringFlow)
        assertEquals(ScheduleFlowState.FLOW_ACTIVE, flowResult.flowState)
        assertEquals(2 * 3600L, flowResult.remainingSeconds) // 2 hours until 10:00 AM closure
        assertEquals("02:00:00", flowResult.formattedCountdown)
        assertTrue("Countdown must be strictly non-negative", flowResult.remainingSeconds >= 0L)

        // Test 2.3b: During dry interval, countdown ticks toward next window startTime
        val duringDry = LocalDateTime.of(2026, 10, 12, 10, 0, 0) // Mon 10:00 AM (just closed)
        val dryResult = ScheduleCountdownCalculator.calculate(schedules, duringDry)
        assertEquals(ScheduleFlowState.RATIONING_ACTIVE, dryResult.flowState)
        // Ticks toward Wednesday 05:00 AM opening (43 hours away)
        assertEquals(43 * 3600L, dryResult.remainingSeconds)
        assertEquals("43:00:00", dryResult.formattedCountdown)
        assertTrue("Countdown must be strictly non-negative", dryResult.remainingSeconds >= 0L)

        // Test 2.3c: Monotonicity check (countdown strictly decreases as time advances)
        val duringFlowLater = LocalDateTime.of(2026, 10, 12, 8, 1, 0) // 1 minute later
        val flowLaterResult = ScheduleCountdownCalculator.calculate(schedules, duringFlowLater)
        assertEquals(flowResult.remainingSeconds - 60L, flowLaterResult.remainingSeconds)
    }


    @Test
    override fun verifyLaw3DiscreteVesselQuantization() {
        // Standard Baguio vessels: 200L drums, 1000L overhead tanks, 20L containers
        val inventory = StorageInventory(
            blueDrums200L = 2,       // 400L
            overheadTanks1000L = 0,
            gallons20L = 3,          // 60L
            fillPercentage = 100.0
        )
        assertEquals(460.0, inventory.totalCapacityLiters, 0.001)

        // Negative container counts must throw IllegalArgumentException
        var caughtNegative = false
        try {
            StorageInventory(blueDrums200L = -1)
        } catch (e: IllegalArgumentException) {
            caughtNegative = true
        }
        assertTrue("Law 3 violation must trigger exception on negative vessel count", caughtNegative)
    }

    @Test
    override fun verifyLaw4ReportValidityAndIntegrity() {
        val validReport = GroundTruthReport(
            id = "rep-001",
            zoneId = "brgy4",
            zoneName = "Zone 4 — Brgy. San Isidro",
            status = TapFlowStatus.RUNNING,
            notes = "Strong morning flow at Purok 2"
        )
        assertEquals(TapFlowStatus.RUNNING, validReport.status)

        // Future timestamp must trigger validation error
        var caughtFuture = false
        try {
            GroundTruthReport(
                id = "rep-002",
                zoneId = "brgy4",
                zoneName = "Zone 4",
                status = TapFlowStatus.DRY,
                timestamp = System.currentTimeMillis() + 100_000
            )
        } catch (e: IllegalArgumentException) {
            caughtFuture = true
        }
        assertTrue("Law 4 violation must trigger exception on future timestamps", caughtFuture)
    }
}
