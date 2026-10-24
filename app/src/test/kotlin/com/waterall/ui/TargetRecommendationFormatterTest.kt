package com.waterall.ui

import com.waterall.domain.calculator.RunoutEstimator
import com.waterall.domain.calculator.ScheduleFlowState
import com.waterall.domain.model.StorageInventory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying [TargetRecommendationFormatter] enforces accurate presentation logic
 * and prevents falsely displaying "Supply window is active" when supply hours is 0.0 or schedule is empty.
 */
class TargetRecommendationFormatterTest {

    @Test
    fun testEmptyScheduleHoursUnderRationingShowsNeutralCopyNotActiveSupplyMessage() {
        val inventory = StorageInventory(blueDrums200L = 2, fillPercentage = 100.0) // 400L stored
        val result = RunoutEstimator.calculate(
            inventory = inventory,
            dailyBurnRateLiters = 200.0,
            hoursUntilNextSupply = 0.0
        )

        // Empty schedule or 0 hours entered with RATIONING_ACTIVE
        val uiModel = TargetRecommendationFormatter.format(
            result = result,
            flowState = ScheduleFlowState.RATIONING_ACTIVE,
            hoursUntilNextSupply = 0.0
        )

        assertEquals(TargetRecommendationFormatter.EMPTY_TARGET_TEXT, uiModel.targetValueText)
        assertEquals(TargetRecommendationFormatter.EMPTY_SUPPLY_HOURS_SUBTEXT, uiModel.subtext)
        assertFalse(uiModel.isDeficitRisk)
        assertNotEquals(TargetRecommendationFormatter.ACTIVE_FLOW_SUBTEXT, uiModel.subtext)
    }

    @Test
    fun testActiveFlowWindowShowsUnrestrictedAndActiveSupplyMessage() {
        val inventory = StorageInventory(blueDrums200L = 1, fillPercentage = 50.0) // 100L stored
        val result = RunoutEstimator.calculate(
            inventory = inventory,
            dailyBurnRateLiters = 240.0,
            hoursUntilNextSupply = 45.0
        )

        val uiModel = TargetRecommendationFormatter.format(
            result = result,
            flowState = ScheduleFlowState.FLOW_ACTIVE,
            hoursUntilNextSupply = 45.0
        )

        assertEquals(TargetRecommendationFormatter.UNRESTRICTED_TARGET_TEXT, uiModel.targetValueText)
        assertEquals(TargetRecommendationFormatter.ACTIVE_FLOW_SUBTEXT, uiModel.subtext)
        assertFalse(uiModel.isDeficitRisk)
    }

    @Test
    fun testNormalRationingSurplusShowsBufferRetentionTarget() {
        val inventory = StorageInventory(blueDrums200L = 2, fillPercentage = 100.0) // 400L stored
        val result = RunoutEstimator.calculate(
            inventory = inventory,
            dailyBurnRateLiters = 200.0,
            hoursUntilNextSupply = 24.0
        )

        val uiModel = TargetRecommendationFormatter.format(
            result = result,
            flowState = ScheduleFlowState.RATIONING_ACTIVE,
            hoursUntilNextSupply = 24.0
        )

        assertEquals("≤ 400 L/day", uiModel.targetValueText)
        assertEquals("Stay at or below 400 L/day (currently 200) to keep your buffer.", uiModel.subtext)
        assertFalse(uiModel.isDeficitRisk)
    }

    @Test
    fun testNormalRationingDeficitShowsReductionTarget() {
        val inventory = StorageInventory(blueDrums200L = 1, fillPercentage = 100.0) // 200L stored
        val result = RunoutEstimator.calculate(
            inventory = inventory,
            dailyBurnRateLiters = 200.0,
            hoursUntilNextSupply = 48.0
        )

        val uiModel = TargetRecommendationFormatter.format(
            result = result,
            flowState = ScheduleFlowState.RATIONING_ACTIVE,
            hoursUntilNextSupply = 48.0
        )

        assertEquals("≤ 100 L/day", uiModel.targetValueText)
        assertEquals("Reduce use from 200 to 100 L/day to avoid running out.", uiModel.subtext)
        assertTrue(uiModel.isDeficitRisk)
    }

    @Test
    fun testZeroOrNegativeSupplyHoursUnderRationingShowsNeutralInstructions() {
        val inventory = StorageInventory(blueDrums200L = 1, fillPercentage = 50.0) // 100L stored
        val result = RunoutEstimator.calculate(
            inventory = inventory,
            dailyBurnRateLiters = 100.0,
            hoursUntilNextSupply = 0.0
        )

        val negativeHoursModel = TargetRecommendationFormatter.format(
            result = result,
            flowState = ScheduleFlowState.RATIONING_ACTIVE,
            hoursUntilNextSupply = -1.0
        )

        assertEquals(TargetRecommendationFormatter.EMPTY_TARGET_TEXT, negativeHoursModel.targetValueText)
        assertEquals(TargetRecommendationFormatter.EMPTY_SUPPLY_HOURS_SUBTEXT, negativeHoursModel.subtext)
        assertFalse(negativeHoursModel.isDeficitRisk)
    }

    @Test
    fun testZeroConsumptionWithZeroSupplyHoursUnderRationingShowsNeutralInstructions() {
        // Law 1: 0 burn rate lasts indefinitely
        val inventory = StorageInventory(blueDrums200L = 2, fillPercentage = 100.0) // 400L stored
        val result = RunoutEstimator.calculate(
            inventory = inventory,
            dailyBurnRateLiters = 0.0,
            hoursUntilNextSupply = 0.0
        )

        val uiModel = TargetRecommendationFormatter.format(
            result = result,
            flowState = ScheduleFlowState.RATIONING_ACTIVE,
            hoursUntilNextSupply = 0.0
        )

        assertEquals(TargetRecommendationFormatter.EMPTY_TARGET_TEXT, uiModel.targetValueText)
        assertEquals(TargetRecommendationFormatter.EMPTY_SUPPLY_HOURS_SUBTEXT, uiModel.subtext)
        assertFalse(uiModel.isDeficitRisk)
        assertNotEquals(TargetRecommendationFormatter.ACTIVE_FLOW_SUBTEXT, uiModel.subtext)
    }

    @Test
    fun testActiveFlowStateWithEmptyContainersDirectsResidentToRefill() {
        val emptyInventory = StorageInventory(blueDrums200L = 0, fillPercentage = 0.0)
        val result = RunoutEstimator.calculate(
            inventory = emptyInventory,
            dailyBurnRateLiters = 150.0,
            hoursUntilNextSupply = 36.0
        )

        val uiModel = TargetRecommendationFormatter.format(
            result = result,
            flowState = ScheduleFlowState.FLOW_ACTIVE,
            hoursUntilNextSupply = 36.0
        )

        assertEquals(TargetRecommendationFormatter.UNRESTRICTED_TARGET_TEXT, uiModel.targetValueText)
        assertEquals(TargetRecommendationFormatter.ACTIVE_FLOW_SUBTEXT, uiModel.subtext)
        assertFalse(uiModel.isDeficitRisk)
    }

    @Test
    fun testManualOverrideDuringActiveFlowUsesRationingActiveTarget() {
        // When active flow is ongoing, countdown.flowState is FLOW_ACTIVE.
        // However, when a resident sets an explicit manual override in etHoursUntilNextSupply (e.g. 24.0 hours),
        // recalculateRunoutBuffer passes effectiveFlowState = ScheduleFlowState.RATIONING_ACTIVE.
        val inventory = StorageInventory(blueDrums200L = 2, fillPercentage = 100.0) // 400L stored
        val result = RunoutEstimator.calculate(
            inventory = inventory,
            dailyBurnRateLiters = 200.0,
            hoursUntilNextSupply = 24.0
        )

        val countdownFlowState = ScheduleFlowState.FLOW_ACTIVE
        val customSupplyHours: Double? = 24.0

        val effectiveFlowState = if (customSupplyHours != null) {
            ScheduleFlowState.RATIONING_ACTIVE
        } else {
            countdownFlowState
        }

        val uiModel = TargetRecommendationFormatter.format(
            result = result,
            flowState = effectiveFlowState,
            hoursUntilNextSupply = customSupplyHours ?: 0.0
        )

        // Manual override must calculate rationing daily target instead of "Unrestricted"
        assertEquals("≤ 400 L/day", uiModel.targetValueText)
        assertEquals("Stay at or below 400 L/day (currently 200) to keep your buffer.", uiModel.subtext)
        assertFalse(uiModel.isDeficitRisk)
        assertNotEquals(TargetRecommendationFormatter.UNRESTRICTED_TARGET_TEXT, uiModel.targetValueText)
        assertNotEquals(TargetRecommendationFormatter.ACTIVE_FLOW_SUBTEXT, uiModel.subtext)
    }

    @Test
    fun testWithoutManualOverrideDuringActiveFlowShowsUnrestricted() {
        val inventory = StorageInventory(blueDrums200L = 2, fillPercentage = 100.0) // 400L stored
        val result = RunoutEstimator.calculate(
            inventory = inventory,
            dailyBurnRateLiters = 200.0,
            hoursUntilNextSupply = 24.0
        )

        val countdownFlowState = ScheduleFlowState.FLOW_ACTIVE
        val customSupplyHours: Double? = null

        val effectiveFlowState = if (customSupplyHours != null) {
            ScheduleFlowState.RATIONING_ACTIVE
        } else {
            countdownFlowState
        }

        val uiModel = TargetRecommendationFormatter.format(
            result = result,
            flowState = effectiveFlowState,
            hoursUntilNextSupply = 24.0
        )

        assertEquals(TargetRecommendationFormatter.UNRESTRICTED_TARGET_TEXT, uiModel.targetValueText)
        assertEquals(TargetRecommendationFormatter.ACTIVE_FLOW_SUBTEXT, uiModel.subtext)
        assertFalse(uiModel.isDeficitRisk)
    }

    @Test
    fun testManualOverrideWithZeroHoursDuringActiveFlowShowsNeutralPromptNotUnrestricted() {
        val inventory = StorageInventory(blueDrums200L = 2, fillPercentage = 100.0) // 400L stored
        val result = RunoutEstimator.calculate(
            inventory = inventory,
            dailyBurnRateLiters = 200.0,
            hoursUntilNextSupply = 0.0
        )

        val countdownFlowState = ScheduleFlowState.FLOW_ACTIVE
        val customSupplyHours: Double? = 0.0

        val effectiveFlowState = if (customSupplyHours != null) {
            ScheduleFlowState.RATIONING_ACTIVE
        } else {
            countdownFlowState
        }

        val uiModel = TargetRecommendationFormatter.format(
            result = result,
            flowState = effectiveFlowState,
            hoursUntilNextSupply = customSupplyHours ?: 0.0
        )

        assertEquals(TargetRecommendationFormatter.EMPTY_TARGET_TEXT, uiModel.targetValueText)
        assertEquals(TargetRecommendationFormatter.EMPTY_SUPPLY_HOURS_SUBTEXT, uiModel.subtext)
        assertFalse(uiModel.isDeficitRisk)
        assertNotEquals(TargetRecommendationFormatter.UNRESTRICTED_TARGET_TEXT, uiModel.targetValueText)
        assertNotEquals(TargetRecommendationFormatter.ACTIVE_FLOW_SUBTEXT, uiModel.subtext)
    }

    @Test
    fun testManualOverrideCausingDeficitDuringActiveFlowShowsDeficitTarget() {
        val inventory = StorageInventory(blueDrums200L = 1, fillPercentage = 100.0) // 200L stored
        val result = RunoutEstimator.calculate(
            inventory = inventory,
            dailyBurnRateLiters = 200.0,
            hoursUntilNextSupply = 48.0
        )

        val countdownFlowState = ScheduleFlowState.FLOW_ACTIVE
        val customSupplyHours: Double? = 48.0

        val effectiveFlowState = if (customSupplyHours != null) {
            ScheduleFlowState.RATIONING_ACTIVE
        } else {
            countdownFlowState
        }

        val uiModel = TargetRecommendationFormatter.format(
            result = result,
            flowState = effectiveFlowState,
            hoursUntilNextSupply = customSupplyHours ?: 0.0
        )

        assertTrue(uiModel.isDeficitRisk)
        assertEquals("≤ 100 L/day", uiModel.targetValueText)
        assertEquals("Reduce use from 200 to 100 L/day to avoid running out.", uiModel.subtext)
        assertNotEquals(TargetRecommendationFormatter.UNRESTRICTED_TARGET_TEXT, uiModel.targetValueText)
        assertNotEquals(TargetRecommendationFormatter.ACTIVE_FLOW_SUBTEXT, uiModel.subtext)
    }

    @Test
    fun testBlankSupplyHoursInputDefaultsToZeroAndTriggersEmptyState() {
        // Household with 1 drum full (200L), consumption 150 L/day
        // When etHoursUntilNextSupply is blank, nextSupplyHours evaluates to 0.0
        val isSupplyHoursBlank = true
        val customSupplyHours: Double? = 0.0
        val countdownHoursUntilNextOpening = 18.0
        val nextSupplyHours = if (isSupplyHoursBlank) {
            0.0
        } else {
            (customSupplyHours ?: countdownHoursUntilNextOpening).coerceAtLeast(0.0)
        }
        assertEquals(0.0, nextSupplyHours, 0.001)

        val inventory = StorageInventory(blueDrums200L = 1, fillPercentage = 100.0)
        val result = RunoutEstimator.calculate(
            inventory = inventory,
            dailyBurnRateLiters = 150.0,
            hoursUntilNextSupply = nextSupplyHours
        )

        val countdownFlowState = ScheduleFlowState.RATIONING_ACTIVE
        val effectiveFlowState = if (customSupplyHours != null || isSupplyHoursBlank) {
            ScheduleFlowState.RATIONING_ACTIVE
        } else {
            countdownFlowState
        }

        val uiModel = TargetRecommendationFormatter.format(
            result = result,
            flowState = effectiveFlowState,
            hoursUntilNextSupply = nextSupplyHours
        )

        assertEquals(TargetRecommendationFormatter.EMPTY_TARGET_TEXT, uiModel.targetValueText)
        assertEquals(TargetRecommendationFormatter.EMPTY_SUPPLY_HOURS_SUBTEXT, uiModel.subtext)
        assertFalse(uiModel.isDeficitRisk)
    }

    @Test
    fun testBlankSupplyHoursInputDuringActiveFlowTriggersEmptyStateNotUnrestricted() {
        val isSupplyHoursBlank = true
        val countdownFlowState = ScheduleFlowState.FLOW_ACTIVE
        val countdownHoursUntilNextOpening = 24.0

        val nextSupplyHours = if (isSupplyHoursBlank) {
            0.0
        } else {
            countdownHoursUntilNextOpening.coerceAtLeast(0.0)
        }
        val effectiveFlowState = if (isSupplyHoursBlank) {
            ScheduleFlowState.RATIONING_ACTIVE
        } else {
            countdownFlowState
        }

        val inventory = StorageInventory(blueDrums200L = 2, fillPercentage = 100.0)
        val result = RunoutEstimator.calculate(
            inventory = inventory,
            dailyBurnRateLiters = 200.0,
            hoursUntilNextSupply = nextSupplyHours
        )

        val uiModel = TargetRecommendationFormatter.format(
            result = result,
            flowState = effectiveFlowState,
            hoursUntilNextSupply = nextSupplyHours
        )

        assertEquals(TargetRecommendationFormatter.EMPTY_TARGET_TEXT, uiModel.targetValueText)
        assertEquals(TargetRecommendationFormatter.EMPTY_SUPPLY_HOURS_SUBTEXT, uiModel.subtext)
        assertFalse(uiModel.isDeficitRisk)
        assertNotEquals(TargetRecommendationFormatter.UNRESTRICTED_TARGET_TEXT, uiModel.targetValueText)
        assertNotEquals(TargetRecommendationFormatter.ACTIVE_FLOW_SUBTEXT, uiModel.subtext)
    }
}
