package com.waterall.domain.laws

import com.waterall.domain.calculator.CountdownCalculationResult
import com.waterall.domain.calculator.RunoutEstimator
import com.waterall.domain.calculator.ScheduleCountdownCalculator
import com.waterall.domain.calculator.ScheduleFlowState
import com.waterall.domain.calculator.ZoneConsensusCalculator
import com.waterall.domain.model.BarangayZone
import com.waterall.domain.model.GroundTruthReport
import com.waterall.domain.model.StorageInventory
import com.waterall.domain.model.SupplyWindow
import com.waterall.domain.model.TapFlowStatus
import com.waterall.domain.model.UserMode
import com.waterall.domain.model.VesselType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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

    // =========================================================================
    // LAW 1: Conservation of Water & Non-Negative Storage / Deficit
    // =========================================================================

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
        // Recommended daily target to eliminate deficit before next supply: (400 / 36) * 24 = 266.667 L/day
        assertEquals(266.667, result.recommendedDailyTargetLiters, 0.001)

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
    fun verifyLaw1FillPercentageBoundariesAndExceptions() {
        val inventory = StorageInventory(blueDrums200L = 2)

        // Exact 0% fill percentage gives 0.0 current volume
        val emptyInv = inventory.copy(fillPercentage = 0.0)
        assertEquals(0.0, emptyInv.currentStoredVolumeLiters, 0.001)

        // Exact 100% fill percentage gives total capacity
        val fullInv = inventory.copy(fillPercentage = 100.0)
        assertEquals(400.0, fullInv.currentStoredVolumeLiters, 0.001)

        // Negative fill percentage must violate Law 1
        try {
            inventory.copy(fillPercentage = -0.1)
            fail("Negative fill percentage must trigger Law 1 violation")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Law 1 violation") == true)
        }

        // Fill percentage over 100% must violate Law 1
        try {
            inventory.copy(fillPercentage = 100.1)
            fail("Fill percentage > 100% must trigger Law 1 violation")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Law 1 violation") == true)
        }
    }

    @Test
    fun verifyLaw1RunoutEstimatorEdgeCasesAndNonNegativeDeficit() {
        val inventory = StorageInventory(blueDrums200L = 2, fillPercentage = 100.0) // 400L

        // Surplus reserve: runout 48 hrs > next supply 24 hrs -> deficit is strictly 0.0
        val surplus = RunoutEstimator.calculate(
            inventory = inventory,
            dailyBurnRateLiters = 200.0, // 8.33 L/hr -> 48 hrs
            hoursUntilNextSupply = 24.0
        )
        assertFalse(surplus.isDepletedBeforeNextSupply)
        assertEquals(0.0, surplus.deficitLiters, 0.001)
        assertEquals(24.0, surplus.safetyBufferHours, 0.001)
        assertTrue("Deficit must never be negative", surplus.deficitLiters >= 0.0)

        // Zero stored water: runout is 0.0 hrs, deficit is entire requirement
        val zeroStored = StorageInventory(blueDrums200L = 2, fillPercentage = 0.0)
        val zeroStoredResult = RunoutEstimator.calculate(
            inventory = zeroStored,
            dailyBurnRateLiters = 240.0, // 10 L/hr
            hoursUntilNextSupply = 10.0
        )
        assertEquals(0.0, zeroStoredResult.currentStoredLiters, 0.001)
        assertEquals(0.0, zeroStoredResult.runoutHours, 0.001)
        assertTrue(zeroStoredResult.isDepletedBeforeNextSupply)
        assertEquals(100.0, zeroStoredResult.deficitLiters, 0.001)
        assertEquals(0.0, zeroStoredResult.recommendedDailyTargetLiters, 0.001)

        // Supply window currently active (0 hours until next supply): target is infinite, deficit is 0
        val immediateSupply = RunoutEstimator.calculate(
            inventory = inventory,
            dailyBurnRateLiters = 200.0,
            hoursUntilNextSupply = 0.0
        )
        assertTrue(immediateSupply.recommendedDailyTargetLiters.isInfinite())
        assertEquals(0.0, immediateSupply.deficitLiters, 0.001)
        assertFalse(immediateSupply.isDepletedBeforeNextSupply)

        // Negative burn rate or negative hours until supply must throw IllegalArgumentException
        try {
            RunoutEstimator.calculate(inventory, dailyBurnRateLiters = -10.0, hoursUntilNextSupply = 12.0)
            fail("Negative daily burn rate must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Burn rate cannot be negative") == true)
        }

        try {
            RunoutEstimator.calculate(inventory, dailyBurnRateLiters = 100.0, hoursUntilNextSupply = -5.0)
            fail("Negative hours until next supply must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Hours until next supply cannot be negative") == true)
        }
    }

    // =========================================================================
    // LAW 2: Rationing Window Chronology & Disjoint Intervals
    // =========================================================================

    @Test
    override fun verifyLaw2RationingWindowChronology() {
        // Law 2.1: Strict positive duration (end > start)
        val validWindow = SupplyWindow(
            dayOfWeek = "Mon",
            dateLabel = "Mon",
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
                dateLabel = "Tue",
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
        assertEquals(45.0, flowResult.hoursUntilNextOpening, 0.001) // 45 hours until Wednesday 05:00 AM opening
        assertEquals("02:00:00", flowResult.formattedCountdown)
        assertTrue("Countdown must be strictly non-negative", flowResult.remainingSeconds >= 0L)

        // Test 2.3b: During dry interval, countdown ticks toward next window startTime
        val duringDry = LocalDateTime.of(2026, 10, 12, 10, 0, 0) // Mon 10:00 AM (just closed)
        val dryResult = ScheduleCountdownCalculator.calculate(schedules, duringDry)
        assertEquals(ScheduleFlowState.RATIONING_ACTIVE, dryResult.flowState)
        // Ticks toward Wednesday 05:00 AM opening (43 hours away)
        assertEquals(43 * 3600L, dryResult.remainingSeconds)
        assertEquals(43.0, dryResult.hoursUntilNextOpening, 0.001)
        assertEquals("43:00:00", dryResult.formattedCountdown)
        assertTrue("Countdown must be strictly non-negative", dryResult.remainingSeconds >= 0L)

        // Test 2.3c: Monotonicity check (countdown strictly decreases as time advances)
        val duringFlowLater = LocalDateTime.of(2026, 10, 12, 8, 1, 0) // 1 minute later
        val flowLaterResult = ScheduleCountdownCalculator.calculate(schedules, duringFlowLater)
        assertEquals(flowResult.remainingSeconds - 60L, flowLaterResult.remainingSeconds)
    }

    @Test
    fun verifyLaw2ZeroDurationAndClockValidation() {
        // Zero duration window (end == start) must throw Law 2 violation
        try {
            SupplyWindow("Wed", "Wed 11", 6, 30, 6, 30)
            fail("Zero duration window must throw Law 2 violation")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Law 2 violation") == true)
        }

        // Invalid hour (> 23)
        try {
            SupplyWindow("Thu", "Thu 12", 24, 0, 25, 0)
            fail("Invalid hour > 23 must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Invalid start time") == true)
        }

        // Invalid minute (> 59)
        try {
            SupplyWindow("Fri", "Fri 13", 6, 60, 10, 0)
            fail("Invalid minute > 59 must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Invalid start time") == true)
        }

        // Invalid day string
        val invalidDayWindow = SupplyWindow("Funday", "Funday 01", 6, 0, 10, 0)
        try {
            invalidDayWindow.dayOfWeekEnum
            fail("Invalid day string must throw Law 2 violation")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Law 2 violation") == true)
        }
    }

    @Test
    fun verifyLaw2OverlappingVariationsAndBoundaryAdjacency() {
        // Abutting windows on the same day (e.g. 06:00-10:00 and 10:00-14:00) are valid (disjoint)
        val abuttingWindows = listOf(
            SupplyWindow("Mon", "Mon 09", 6, 0, 10, 0),
            SupplyWindow("Mon", "Mon 09", 10, 0, 14, 0)
        )
        ScheduleCountdownCalculator.validateNonOverlapping(abuttingWindows)

        // Enclosed window (one window completely inside another)
        val enclosingWindows = listOf(
            SupplyWindow("Tue", "Tue 10", 6, 0, 18, 0),
            SupplyWindow("Tue", "Tue 10", 8, 0, 12, 0)
        )
        try {
            ScheduleCountdownCalculator.validateNonOverlapping(enclosingWindows)
            fail("Enclosed window must trigger Law 2 overlap violation")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Law 2 violation") == true)
        }

        // Duplicate identical window
        val duplicateWindows = listOf(
            SupplyWindow("Fri", "Fri 13", 8, 0, 12, 0),
            SupplyWindow("Fri", "Fri 13", 8, 0, 12, 0)
        )
        try {
            ScheduleCountdownCalculator.validateNonOverlapping(duplicateWindows)
            fail("Duplicate windows must trigger Law 2 overlap violation")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Law 2 violation") == true)
        }
    }

    @Test
    fun verifyLaw2CountdownBoundaryTransitionsAndEmptySchedule() {
        val schedules = listOf(
            SupplyWindow("Mon", "Mon 09", 6, 0, 10, 0),
            SupplyWindow("Fri", "Fri 13", 6, 0, 11, 0)
        )

        // Exact window start: 06:00:00 -> FLOW_ACTIVE, full duration remaining
        val exactStart = LocalDateTime.of(2026, 10, 12, 6, 0, 0)
        val startResult = ScheduleCountdownCalculator.calculate(schedules, exactStart)
        assertEquals(ScheduleFlowState.FLOW_ACTIVE, startResult.flowState)
        assertEquals(4 * 3600L, startResult.remainingSeconds)

        // 1 second before window closure: 09:59:59 -> FLOW_ACTIVE, 1 second remaining
        val oneSecBefore = LocalDateTime.of(2026, 10, 12, 9, 59, 59)
        val oneSecResult = ScheduleCountdownCalculator.calculate(schedules, oneSecBefore)
        assertEquals(ScheduleFlowState.FLOW_ACTIVE, oneSecResult.flowState)
        assertEquals(1L, oneSecResult.remainingSeconds)

        // Exact window closure: 10:00:00 -> RATIONING_ACTIVE, deterministic transition
        val exactClosure = LocalDateTime.of(2026, 10, 12, 10, 0, 0)
        val closureResult = ScheduleCountdownCalculator.calculate(schedules, exactClosure)
        assertEquals(ScheduleFlowState.RATIONING_ACTIVE, closureResult.flowState)
        // Next window is Friday 06:00 AM: Monday 10:00 to Friday 06:00 = 92 hours = 331200 seconds
        assertEquals(92 * 3600L, closureResult.remainingSeconds)

        // Weekend wrap-around: Friday 12:00 PM (after Friday 06:00-11:00) to Monday 06:00 AM = 66 hours
        val fridayAfternoon = LocalDateTime.of(2026, 10, 16, 12, 0, 0)
        val weekendResult = ScheduleCountdownCalculator.calculate(schedules, fridayAfternoon)
        assertEquals(ScheduleFlowState.RATIONING_ACTIVE, weekendResult.flowState)
        assertEquals(66 * 3600L, weekendResult.remainingSeconds)
        assertEquals("66:00:00", weekendResult.formattedCountdown)

        // Empty schedule handles safely
        val emptyResult = ScheduleCountdownCalculator.calculate(emptyList(), exactStart)
        assertEquals(ScheduleFlowState.RATIONING_ACTIVE, emptyResult.flowState)
        assertEquals(0L, emptyResult.remainingSeconds)
        assertEquals("00:00:00", emptyResult.formattedCountdown)
        assertNull(emptyResult.targetDateTime)
    }

    // =========================================================================
    // LAW 3: Discrete Vessel Quantization
    // =========================================================================

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
    fun verifyLaw3VesselUnitCapacitiesAndZeroInventory() {
        // Standard Baguio vessel capacities per Law 3.1
        assertEquals(200.0, VesselType.BLUE_DRUM_200L.unitCapacityLiters, 0.001)
        assertEquals(1000.0, VesselType.OVERHEAD_TANK_1000L.unitCapacityLiters, 0.001)
        assertEquals(20.0, VesselType.GALLON_CONTAINER_20L.unitCapacityLiters, 0.001)

        // Zero containers yields zero capacity and stored volume
        val zeroInventory = StorageInventory(blueDrums200L = 0, overheadTanks1000L = 0, gallons20L = 0)
        assertEquals(0.0, zeroInventory.totalCapacityLiters, 0.001)
        assertEquals(0.0, zeroInventory.currentStoredVolumeLiters, 0.001)

        // Mixed combination dot product: 5 drums (1000L) + 2 tanks (2000L) + 10 gallons (200L) = 3200L
        val mixedInventory = StorageInventory(blueDrums200L = 5, overheadTanks1000L = 2, gallons20L = 10)
        assertEquals(3200.0, mixedInventory.totalCapacityLiters, 0.001)

        // Negative overhead tanks must trigger Law 3 violation
        try {
            StorageInventory(overheadTanks1000L = -1)
            fail("Negative overhead tanks must throw Law 3 violation")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Law 3 violation") == true)
        }

        // Negative gallons must trigger Law 3 violation
        try {
            StorageInventory(gallons20L = -2)
            fail("Negative gallons must throw Law 3 violation")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Law 3 violation") == true)
        }
    }

    // =========================================================================
    // LAW 4: Ground-Truth Report Validity & Temporal Integrity
    // =========================================================================

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

        // Future timestamp must trigger validation error on report construction
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

        // Law 4 Relative Timestamp Formatting: Enforce non-future timestamps
        val now = 1_700_000_000_000L
        var caughtRelativeFuture = false
        try {
            GroundTruthReport.formatRelativeTimestamp(now + 60_000L, now)
        } catch (e: IllegalArgumentException) {
            caughtRelativeFuture = true
            assertTrue(e.message?.contains("Law 4 violation") == true)
        }
        assertTrue("Relative formatting must reject future timestamps under Law 4", caughtRelativeFuture)

        // Relative Formatting checks: exact formats ("Reported 12m ago", "Reported just now", etc.)
        assertEquals("Reported just now", GroundTruthReport.formatRelativeTimestamp(now, now))
        assertEquals("Reported just now", GroundTruthReport.formatRelativeTimestamp(now + 1000L, now))
        assertEquals("Reported just now", GroundTruthReport.formatRelativeTimestamp(now - 30_000L, now))
        assertEquals("Reported 12m ago", GroundTruthReport.formatRelativeTimestamp(now - 12 * 60 * 1000L, now))
        assertEquals("Reported 45m ago", GroundTruthReport.formatRelativeTimestamp(now - 45 * 60 * 1000L, now))
        assertEquals("Reported 2h ago", GroundTruthReport.formatRelativeTimestamp(now - 2 * 3600 * 1000L, now))
        assertEquals("Reported 1d ago", GroundTruthReport.formatRelativeTimestamp(now - 24 * 3600 * 1000L, now))

        // Law 4 Zone Consensus Calculation: Aggregates verified reports into consensus
        val zone4Reports = listOf(
            GroundTruthReport("r1", "brgy4", "Zone 4", TapFlowStatus.RUNNING, timestamp = now - 45 * 60 * 1000L),
            GroundTruthReport("r2", "brgy4", "Zone 4", TapFlowStatus.RUNNING, timestamp = now - 30 * 60 * 1000L),
            GroundTruthReport("r3", "brgy4", "Zone 4", TapFlowStatus.RUNNING, timestamp = now - 20 * 60 * 1000L),
            GroundTruthReport("r4", "brgy4", "Zone 4", TapFlowStatus.LOW_PRESSURE, timestamp = now - 12 * 60 * 1000L),
            // Out-of-zone report: must not contaminate Zone 4 (Zone Integrity)
            GroundTruthReport("r5", "brgy1", "Zone 1", TapFlowStatus.DRY, timestamp = now - 10 * 60 * 1000L)
        )

        val consensus = ZoneConsensusCalculator.calculate(
            zoneId = "brgy4",
            zoneName = "Zone 4 — Brgy. San Isidro",
            reports = zone4Reports,
            currentTimeMillis = now
        )

        assertEquals(4, consensus.totalReports)
        assertEquals(TapFlowStatus.RUNNING, consensus.dominantStatus)
        assertEquals(75, consensus.statusPercentages[TapFlowStatus.RUNNING])
        assertEquals(25, consensus.statusPercentages[TapFlowStatus.LOW_PRESSURE])
        assertEquals("Reported 12m ago", consensus.formattedRelativeTime)
        assertEquals("Zone 4: 4 verified reports today — 75% Running, 25% Low Pressure", consensus.summaryText)

        // Invariant: Consensus accepts reports within clock skew tolerance (<= 1,000ms)
        val skewedReports = listOf(
            GroundTruthReport("r-skewed", "brgy4", "Zone 4", TapFlowStatus.RUNNING, timestamp = now + 800L)
        )
        val skewedConsensus = ZoneConsensusCalculator.calculate(
            zoneId = "brgy4",
            zoneName = "Zone 4",
            reports = skewedReports,
            currentTimeMillis = now
        )
        assertEquals(1, skewedConsensus.totalReports)
        assertEquals("Reported just now", skewedConsensus.formattedRelativeTime)

        // Invariant: Consensus must reject reports exceeding the 1,000ms clock skew buffer
        val invalidFutureReports = listOf(
            GroundTruthReport("r-future", "brgy4", "Zone 4", TapFlowStatus.RUNNING, timestamp = now)
        )
        var caughtConsensusFuture = false
        try {
            ZoneConsensusCalculator.calculate(
                zoneId = "brgy4",
                zoneName = "Zone 4",
                reports = invalidFutureReports,
                currentTimeMillis = now - 5000L
            )
        } catch (e: IllegalArgumentException) {
            caughtConsensusFuture = true
            assertTrue(e.message?.contains("Law 4 violation") == true)
        }
        assertTrue("Consensus calculator must reject future timestamps under Law 4", caughtConsensusFuture)
    }

    @Test
    fun verifyLaw4ClosedEnumStatesAndBlankZoneIntegrity() {
        // Law 4 Invariant 1: Exactly 3 verified physical tap states in closed enum
        val statuses = TapFlowStatus.values()
        assertEquals(3, statuses.size)
        assertTrue(statuses.contains(TapFlowStatus.RUNNING))
        assertTrue(statuses.contains(TapFlowStatus.LOW_PRESSURE))
        assertTrue(statuses.contains(TapFlowStatus.DRY))

        // Law 4 Invariant 3: Blank Zone ID must trigger Law 4 violation
        try {
            GroundTruthReport(
                id = "rep-blank-zone",
                zoneId = "   ",
                zoneName = "Zone Blank",
                status = TapFlowStatus.RUNNING
            )
            fail("Blank zone ID must throw Law 4 violation")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Law 4 violation") == true)
        }
    }

    @Test
    fun verifyLaw4RelativeTimestampThresholdBoundaries() {
        val now = 1_700_000_000_000L

        assertEquals("Reported just now", GroundTruthReport.formatRelativeTimestamp(now, now))
        assertEquals("Reported just now", GroundTruthReport.formatRelativeTimestamp(now - 59_000L, now))
        assertEquals("Reported 1m ago", GroundTruthReport.formatRelativeTimestamp(now - 60_000L, now))
        assertEquals("Reported 59m ago", GroundTruthReport.formatRelativeTimestamp(now - 59 * 60 * 1000L, now))
        assertEquals("Reported 1h ago", GroundTruthReport.formatRelativeTimestamp(now - 60 * 60 * 1000L, now))
        assertEquals("Reported 23h ago", GroundTruthReport.formatRelativeTimestamp(now - 23 * 3600 * 1000L, now))
        assertEquals("Reported 1d ago", GroundTruthReport.formatRelativeTimestamp(now - 24 * 3600 * 1000L, now))
        assertEquals("Reported 5d ago", GroundTruthReport.formatRelativeTimestamp(now - 5 * 24 * 3600 * 1000L, now))
    }

    @Test
    fun verifyLaw4ConsensusDistributionSumTo100AndEmptyState() {
        val now = 1_700_000_000_000L

        // Empty state
        val emptyConsensus = ZoneConsensusCalculator.calculate(
            zoneId = "brgy1",
            zoneName = "Zone 1 — Brgy. Cabinet Hill",
            reports = emptyList(),
            currentTimeMillis = now
        )
        assertEquals(0, emptyConsensus.totalReports)
        assertNull(emptyConsensus.dominantStatus)
        assertNull(emptyConsensus.formattedRelativeTime)
        assertEquals("Zone 1: No verified reports today", emptyConsensus.summaryText)

        // All 3 states present: 1 Running, 1 Low Pressure, 1 Dry -> discrete percentages sum to exactly 100%
        val threeWayReports = listOf(
            GroundTruthReport("r1", "brgy1", "Zone 1", TapFlowStatus.RUNNING, timestamp = now - 30 * 60 * 1000L),
            GroundTruthReport("r2", "brgy1", "Zone 1", TapFlowStatus.LOW_PRESSURE, timestamp = now - 20 * 60 * 1000L),
            GroundTruthReport("r3", "brgy1", "Zone 1", TapFlowStatus.DRY, timestamp = now - 10 * 60 * 1000L)
        )
        val consensus = ZoneConsensusCalculator.calculate("brgy1", "Zone 1", threeWayReports, now)
        assertEquals(3, consensus.totalReports)
        val sumPct = consensus.statusPercentages.values.sum()
        assertEquals(100, sumPct)
        assertEquals("Reported 10m ago", consensus.formattedRelativeTime)
    }
}
