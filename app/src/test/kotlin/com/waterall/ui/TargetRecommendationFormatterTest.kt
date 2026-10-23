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
}
