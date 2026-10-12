package com.waterall.domain.calculator

import com.waterall.domain.model.StorageInventory
import kotlin.math.max

data class RunoutEstimateResult(
    val totalCapacityLiters: Double,
    val currentStoredLiters: Double,
    val dailyBurnRateLiters: Double,
    val runoutHours: Double,
    val hoursUntilNextSupply: Double,
    val deficitLiters: Double,
    val isDepletedBeforeNextSupply: Boolean,
    val safetyBufferHours: Double
) {
    val runoutSummaryText: String
        get() = when {
            runoutHours.isInfinite() -> "Stored supply will last indefinitely (0 consumption)."
            isDepletedBeforeNextSupply -> String.format(
                "CRITICAL DEFICIT: Reserve depletes in %.1f hrs (%.1f hrs before next supply). Estimated water shortfall: %.0f Liters.",
                runoutHours,
                -safetyBufferHours,
                deficitLiters
            )
            else -> String.format(
                "SAFE SURPLUS: Reserve lasts %.1f hrs (Buffer of +%.1f hrs after next supply window).",
                runoutHours,
                safetyBufferHours
            )
        }
}

object RunoutEstimator {

    /**
     * Calculates reserve runout duration and deficit against the next BWD supply window.
     * Enforces Law 1 (Conservation of Water & Non-negative Storage / Deficits).
     *
     * @param inventory Current container inventory and fill %
     * @param dailyBurnRateLiters Household or small business consumption per 24 hours
     * @param hoursUntilNextSupply Hours remaining until BWD valves open for this zone
     */
    fun calculate(
        inventory: StorageInventory,
        dailyBurnRateLiters: Double,
        hoursUntilNextSupply: Double
    ): RunoutEstimateResult {
        require(dailyBurnRateLiters >= 0.0) { "Burn rate cannot be negative." }
        require(hoursUntilNextSupply >= 0.0) { "Hours until next supply cannot be negative." }

        val capacity = inventory.totalCapacityLiters
        val stored = inventory.currentStoredVolumeLiters

        if (dailyBurnRateLiters == 0.0) {
            return RunoutEstimateResult(
                totalCapacityLiters = capacity,
                currentStoredLiters = stored,
                dailyBurnRateLiters = 0.0,
                runoutHours = Double.POSITIVE_INFINITY,
                hoursUntilNextSupply = hoursUntilNextSupply,
                deficitLiters = 0.0,
                isDepletedBeforeNextSupply = false,
                safetyBufferHours = Double.POSITIVE_INFINITY
            )
        }

        // Hourly consumption rate
        val hourlyBurn = dailyBurnRateLiters / 24.0

        // Time in hours until stored water hits 0
        val runoutHours = if (hourlyBurn > 0.0) stored / hourlyBurn else Double.POSITIVE_INFINITY

        val safetyBufferHours = runoutHours - hoursUntilNextSupply
        val isDepletedBefore = runoutHours < hoursUntilNextSupply

        val deficitLiters = if (isDepletedBefore) {
            max(0.0, (hoursUntilNextSupply - runoutHours) * hourlyBurn)
        } else {
            0.0
        }

        return RunoutEstimateResult(
            totalCapacityLiters = capacity,
            currentStoredLiters = stored,
            dailyBurnRateLiters = dailyBurnRateLiters,
            runoutHours = runoutHours,
            hoursUntilNextSupply = hoursUntilNextSupply,
            deficitLiters = deficitLiters,
            isDepletedBeforeNextSupply = isDepletedBefore,
            safetyBufferHours = safetyBufferHours
        )
    }
}
