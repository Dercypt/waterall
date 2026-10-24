package com.waterall.domain.calculator

import com.waterall.domain.model.StorageInventory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class RunoutEstimatorTest {

    @Test
    fun testInterconnectionWithCountdownCalculationResult() {
        val inventory = StorageInventory(
            blueDrums200L = 2, // 400L
            fillPercentage = 100.0
        )
        // Simulate countdown result from Phase 1 schedule engine: 30 hours remaining
        val countdownResult = CountdownCalculationResult(
            flowState = ScheduleFlowState.RATIONING_ACTIVE,
            remainingSeconds = 30 * 3600L,
            targetDateTime = LocalDateTime.now().plusHours(30),
            activeWindow = null,
            nextWindow = null
        )

        // Household burns 200 L/day (8.333 L/hr)
        val result = RunoutEstimator.calculate(
            inventory = inventory,
            dailyBurnRateLiters = 200.0,
            countdownResult = countdownResult
        )

        assertEquals(30.0, result.hoursUntilNextSupply, 0.001)
        // 400L / (200 / 24) = 48.0 hours runout
        assertEquals(48.0, result.runoutHours, 0.001)
        assertFalse(result.isDepletedBeforeNextSupply)
        assertEquals(18.0, result.safetyBufferHours, 0.001)
        assertEquals(0.0, result.deficitLiters, 0.001)
    }

    @Test
    fun testInterconnectionDuringActiveFlowUsesHoursUntilNextOpening() {
        // Reserve: 200L (1 drum full). Daily burn: 240 L/day (10 L/hr -> 20.0 hours of reserve)
        val inventory = StorageInventory(
            blueDrums200L = 1,
            fillPercentage = 100.0
        )
        // Active window has 2.0 hours left until shutoff, but next supply window opening is in 45.0 hours
        val activeFlowCountdown = CountdownCalculationResult(
            flowState = ScheduleFlowState.FLOW_ACTIVE,
            remainingSeconds = 2 * 3600L,
            targetDateTime = LocalDateTime.now().plusHours(2),
            activeWindow = null,
            nextWindow = null,
            hoursUntilNextOpening = 45.0
        )

        val result = RunoutEstimator.calculate(
            inventory = inventory,
            dailyBurnRateLiters = 240.0,
            countdownResult = activeFlowCountdown
        )

        // Must evaluate against next supply opening (45.0 hrs), NOT window shutoff (2.0 hrs)
        assertEquals(45.0, result.hoursUntilNextSupply, 0.001)
        assertEquals(20.0, result.runoutHours, 0.001)
        assertTrue(result.isDepletedBeforeNextSupply)
        assertEquals(25.0, -result.safetyBufferHours, 0.001)
        // Shortfall: 25 hrs * 10 L/hr = 250 L deficit
        assertEquals(250.0, result.deficitLiters, 0.001)
    }

    @Test
    fun testRecommendedDailyTargetEliminatesDeficit() {
        // Reserve: 2 drums = 400L. Next supply in 40 hours.
        // Current consumption: 300 L/day (which runs out in 32 hrs -> 8 hrs shortfall).
        val inventory = StorageInventory(
            blueDrums200L = 2,
            fillPercentage = 100.0
        )
        val deficitResult = RunoutEstimator.calculate(
            inventory = inventory,
            dailyBurnRateLiters = 300.0,
            hoursUntilNextSupply = 40.0
        )

        assertTrue(deficitResult.isDepletedBeforeNextSupply)
        assertEquals(32.0, deficitResult.runoutHours, 0.001)
        assertEquals(100.0, deficitResult.deficitLiters, 0.001)

        // Recommended target: (400L / 40.0 hrs) * 24.0 = 240.0 L/day
        assertEquals(240.0, deficitResult.recommendedDailyTargetLiters, 0.001)

        // When re-evaluating at the recommended target consumption:
        val safeResult = RunoutEstimator.calculate(
            inventory = inventory,
            dailyBurnRateLiters = deficitResult.recommendedDailyTargetLiters,
            hoursUntilNextSupply = 40.0
        )

        assertFalse("Recommended target must eliminate deficit risk", safeResult.isDepletedBeforeNextSupply)
        assertEquals(0.0, safeResult.deficitLiters, 0.001)
        assertEquals(40.0, safeResult.runoutHours, 0.001)
        assertEquals(0.0, safeResult.safetyBufferHours, 0.001)
    }

    @Test
    fun testSurplusBufferRecommendedTarget() {
        // Reserve: 1 tank (1000L) + 2 drums (400L) = 1400L. Next supply in 28 hours.
        val inventory = StorageInventory(
            blueDrums200L = 2,
            overheadTanks1000L = 1,
            fillPercentage = 100.0
        )
        val result = RunoutEstimator.calculate(
            inventory = inventory,
            dailyBurnRateLiters = 250.0,
            hoursUntilNextSupply = 28.0
        )

        assertFalse(result.isDepletedBeforeNextSupply)
        // Target: (1400 / 28) * 24 = 1200 L/day
        assertEquals(1200.0, result.recommendedDailyTargetLiters, 0.001)
        assertTrue(result.safetyBufferHours > 0.0)
    }

    @Test
    fun testZeroStoredWaterTargetIsZero() {
        val emptyInventory = StorageInventory(
            blueDrums200L = 2,
            fillPercentage = 0.0
        )
        val result = RunoutEstimator.calculate(
            inventory = emptyInventory,
            dailyBurnRateLiters = 200.0,
            hoursUntilNextSupply = 24.0
        )

        assertEquals(0.0, result.currentStoredLiters, 0.001)
        assertEquals(0.0, result.recommendedDailyTargetLiters, 0.001)
        assertTrue(result.isDepletedBeforeNextSupply)
    }

    @Test
    fun testImmediateSupplyWindowTargetIsInfinite() {
        val inventory = StorageInventory(blueDrums200L = 2, fillPercentage = 100.0)
        val result = RunoutEstimator.calculate(
            inventory = inventory,
            dailyBurnRateLiters = 200.0,
            hoursUntilNextSupply = 0.0
        )

        assertTrue(result.recommendedDailyTargetLiters.isInfinite())
        assertFalse(result.isDepletedBeforeNextSupply)
        assertEquals(0.0, result.deficitLiters, 0.001)
    }

    @Test
    fun testEmptyReserveWithZeroSupplyHoursEvaluatesToDepletedNotSurplus() {
        // Empty reserve: 0L stored, household burns 150 L/day, supply window opens now (0.0 hrs)
        val emptyInventory = StorageInventory(blueDrums200L = 0, fillPercentage = 0.0)
        val result = RunoutEstimator.calculate(
            inventory = emptyInventory,
            dailyBurnRateLiters = 150.0,
            hoursUntilNextSupply = 0.0
        )

        assertEquals(0.0, result.currentStoredLiters, 0.001)
        assertEquals(0.0, result.runoutHours, 0.001)
        assertEquals(0.0, result.safetyBufferHours, 0.001)
        assertEquals(0.0, result.deficitLiters, 0.001)
        assertTrue("Empty reserve with positive burn rate must evaluate to depleted", result.isDepletedBeforeNextSupply)
        assertTrue(result.runoutSummaryText.startsWith("CRITICAL DEFICIT"))
        assertFalse("Must never categorize empty reserve as safe surplus", result.runoutSummaryText.startsWith("SAFE SURPLUS"))
    }

    @Test
    fun testEmptyReserveWithZeroConsumptionEvaluatesToIndefiniteSurplus() {
        val emptyInventory = StorageInventory(blueDrums200L = 0, fillPercentage = 0.0)
        val result = RunoutEstimator.calculate(
            inventory = emptyInventory,
            dailyBurnRateLiters = 0.0,
            hoursUntilNextSupply = 0.0
        )

        assertTrue(result.runoutHours.isInfinite())
        assertFalse(result.isDepletedBeforeNextSupply)
        assertEquals("Stored supply will last indefinitely (0 consumption).", result.runoutSummaryText)
    }
}
