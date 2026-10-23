package com.waterall.ui

import com.waterall.domain.calculator.RunoutEstimateResult
import com.waterall.domain.calculator.ScheduleFlowState
import java.util.Locale

/**
 * Immutable UI model representing the recommended daily consumption target card presentation.
 */
data class TargetRecommendationUiModel(
    val isDeficitRisk: Boolean,
    val targetValueText: String,
    val subtext: String
)

/**
 * Pure presentation formatter for the recommended daily consumption target card in the Reserves screen.
 *
 * Ensures that:
 * 1. An active supply message ("Supply window is active. Refill containers now.") is only shown
 *    when water is actively flowing ([ScheduleFlowState.FLOW_ACTIVE]).
 * 2. When rationing is active ([ScheduleFlowState.RATIONING_ACTIVE]) but hours until next supply is 0.0
 *    (e.g., empty schedule or 0 hours entered), neutral instructional copy is displayed
 *    ("Set hours to next supply to calculate a daily target.") with no misleading active flow claim.
 * 3. During normal rationing with positive hours, appropriate surplus or deficit targets are formatted.
 */
object TargetRecommendationFormatter {

    const val ACTIVE_FLOW_SUBTEXT = "Supply window is active. Refill containers now."
    const val EMPTY_SUPPLY_HOURS_SUBTEXT = "Set hours to next supply to calculate a daily target."
    const val UNRESTRICTED_TARGET_TEXT = "Unrestricted"
    const val EMPTY_TARGET_TEXT = "—"

    fun format(
        result: RunoutEstimateResult,
        flowState: ScheduleFlowState,
        hoursUntilNextSupply: Double
    ): TargetRecommendationUiModel {
        if (flowState == ScheduleFlowState.FLOW_ACTIVE) {
            return TargetRecommendationUiModel(
                isDeficitRisk = false,
                targetValueText = UNRESTRICTED_TARGET_TEXT,
                subtext = ACTIVE_FLOW_SUBTEXT
            )
        }

        if (hoursUntilNextSupply <= 0.0) {
            return TargetRecommendationUiModel(
                isDeficitRisk = false,
                targetValueText = EMPTY_TARGET_TEXT,
                subtext = EMPTY_SUPPLY_HOURS_SUBTEXT
            )
        }

        return if (result.isDepletedBeforeNextSupply) {
            TargetRecommendationUiModel(
                isDeficitRisk = true,
                targetValueText = String.format(Locale.US, "≤ %.0f L/day", result.recommendedDailyTargetLiters),
                subtext = String.format(
                    Locale.US,
                    "Reduce use from %.0f to %.0f L/day to avoid running out.",
                    result.dailyBurnRateLiters,
                    result.recommendedDailyTargetLiters
                )
            )
        } else {
            TargetRecommendationUiModel(
                isDeficitRisk = false,
                targetValueText = String.format(Locale.US, "≤ %.0f L/day", result.recommendedDailyTargetLiters),
                subtext = String.format(
                    Locale.US,
                    "Stay at or below %.0f L/day (currently %.0f) to keep your buffer.",
                    result.recommendedDailyTargetLiters,
                    result.dailyBurnRateLiters
                )
            )
        }
    }
}
