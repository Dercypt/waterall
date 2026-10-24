package com.waterall.ui

import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.waterall.R
import com.waterall.databinding.ActivityMainBinding
import com.waterall.domain.calculator.RunoutEstimator
import com.waterall.domain.calculator.ScheduleCountdownCalculator
import com.waterall.domain.calculator.ScheduleFlowState
import com.waterall.domain.model.StorageInventory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.util.Locale

/**
 * Screen delegate handling Drum Reserve & Supply Runout Estimator UI (SDG 6 Exclusive).
 *
 * Encapsulates container inventory management (Law 3 quantization), discrete fill presets,
 * daily consumption tracking, real-time runout buffer estimation, dynamic conservation target
 * recommendations, and preference persistence.
 */
class ReservesViewDelegate(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val viewModel: MainViewModel,
    private val userPreferences: UserPreferences
) : ViewDelegate {

    private var isAutoPopulatingScheduleHours = false

    override fun setup() {
        binding.etDrums200L.setText(viewModel.drums200L.value.toString())
        binding.etTanks1000L.setText(viewModel.tanks1000L.value.toString())
        binding.etGallons20L.setText(viewModel.gallons20L.value.toString())
        binding.etDailyConsumption.setText(formatConsumption(viewModel.dailyConsumption.value))

        // Quick-adjust stepper controls (+ / -) for standard Baguio containers (Law 3)
        binding.btnDecrementDrums200L.setOnClickListener {
            viewModel.setDrums200L(maxOf(0, viewModel.drums200L.value - 1))
        }
        binding.btnIncrementDrums200L.setOnClickListener {
            viewModel.setDrums200L(viewModel.drums200L.value + 1)
        }

        binding.btnDecrementTanks1000L.setOnClickListener {
            viewModel.setTanks1000L(maxOf(0, viewModel.tanks1000L.value - 1))
        }
        binding.btnIncrementTanks1000L.setOnClickListener {
            viewModel.setTanks1000L(viewModel.tanks1000L.value + 1)
        }

        binding.btnDecrementGallons20L.setOnClickListener {
            viewModel.setGallons20L(maxOf(0, viewModel.gallons20L.value - 1))
        }
        binding.btnIncrementGallons20L.setOnClickListener {
            viewModel.setGallons20L(viewModel.gallons20L.value + 1)
        }

        // Live text watchers for automatic real-time calculation as inputs change
        binding.etDrums200L.doAfterTextChanged { text ->
            val count = (text?.toString()?.toIntOrNull() ?: 0).coerceAtLeast(0)
            if (viewModel.drums200L.value != count) {
                viewModel.setDrums200L(count)
            }
        }
        binding.etTanks1000L.doAfterTextChanged { text ->
            val count = (text?.toString()?.toIntOrNull() ?: 0).coerceAtLeast(0)
            if (viewModel.tanks1000L.value != count) {
                viewModel.setTanks1000L(count)
            }
        }
        binding.etGallons20L.doAfterTextChanged { text ->
            val count = (text?.toString()?.toIntOrNull() ?: 0).coerceAtLeast(0)
            if (viewModel.gallons20L.value != count) {
                viewModel.setGallons20L(count)
            }
        }
        binding.etDailyConsumption.doAfterTextChanged { text ->
            val value = (text?.toString()?.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
            if (value != viewModel.dailyConsumption.value) {
                viewModel.setDailyConsumption(value)
                userPreferences.dailyConsumption = value
            }
        }
        binding.etHoursUntilNextSupply.doAfterTextChanged { text ->
            if (!isAutoPopulatingScheduleHours) {
                val hours = text?.toString()?.toDoubleOrNull()
                if (viewModel.customSupplyHours.value != hours) {
                    viewModel.setCustomSupplyHours(hours)
                    if (hours == null) {
                        binding.tvScheduleSyncStatus.text = "Synced with zone schedule"
                    }
                }
            }
        }

        // Sync with Schedule Countdown button
        binding.btnSyncScheduleHours.setOnClickListener {
            autoPopulateScheduleHours()
        }

        setupFillLevelPresets()

        val customHours = viewModel.customSupplyHours.value
        if (customHours != null) {
            isAutoPopulatingScheduleHours = true
            try {
                binding.etHoursUntilNextSupply.setText(String.format(Locale.US, "%.1f", customHours))
            } finally {
                isAutoPopulatingScheduleHours = false
            }
            binding.tvScheduleSyncStatus.text = "Manual override"
        } else if (binding.etHoursUntilNextSupply.text.isNullOrBlank()) {
            autoPopulateScheduleHours()
        }
    }

    override fun observeState(scope: CoroutineScope) {
        scope.launch {
            viewModel.selectedFillPercentage.collect { percent ->
                updateFillLevelUi(percent)
            }
        }
        scope.launch {
            viewModel.dailyConsumption.collect { consumption ->
                updateDailyConsumptionUi(consumption)
            }
        }
        scope.launch {
            viewModel.drums200L.collect { count ->
                updateContainerCountUi(binding.etDrums200L, count)
                persistContainerCounts()
            }
        }
        scope.launch {
            viewModel.tanks1000L.collect { count ->
                updateContainerCountUi(binding.etTanks1000L, count)
                persistContainerCounts()
            }
        }
        scope.launch {
            viewModel.gallons20L.collect { count ->
                updateContainerCountUi(binding.etGallons20L, count)
                persistContainerCounts()
            }
        }
        scope.launch {
            viewModel.customSupplyHours.collect { hours ->
                if (hours != null) {
                    val currentVal = binding.etHoursUntilNextSupply.text?.toString()?.toDoubleOrNull()
                    if (currentVal != hours) {
                        isAutoPopulatingScheduleHours = true
                        try {
                            binding.etHoursUntilNextSupply.setText(String.format(Locale.US, "%.1f", hours))
                        } finally {
                            isAutoPopulatingScheduleHours = false
                        }
                    }
                    binding.tvScheduleSyncStatus.text = "Manual override"
                }
            }
        }
        scope.launch {
            combine(
                combine(viewModel.selectedZone, viewModel.userMode) { zone, mode -> zone to mode },
                combine(viewModel.drums200L, viewModel.tanks1000L, viewModel.gallons20L) { d, t, g -> Triple(d, t, g) },
                combine(viewModel.selectedFillPercentage, viewModel.dailyConsumption, viewModel.customSupplyHours) { f, c, h -> Triple(f, c, h) }
            ) { _, _, _ ->
                Unit
            }.collect {
                recalculateRunoutBuffer()
            }
        }
    }

    override fun setVisible(visible: Boolean) {
        binding.viewReserves.visibility = if (visible) View.VISIBLE else View.GONE
    }

    override fun onTabSelected() {
        if (binding.etHoursUntilNextSupply.text.isNullOrBlank()) {
            autoPopulateScheduleHours()
        }
    }

    fun autoPopulateScheduleHours() {
        val countdown = ScheduleCountdownCalculator.calculate(
            zone = viewModel.selectedZone.value,
            userMode = viewModel.userMode.value,
            currentTime = LocalDateTime.now()
        )
        viewModel.setCustomSupplyHours(null)
        isAutoPopulatingScheduleHours = true
        try {
            binding.etHoursUntilNextSupply.setText(
                String.format(Locale.US, "%.1f", countdown.hoursUntilNextOpening)
            )
        } finally {
            isAutoPopulatingScheduleHours = false
        }
        binding.tvScheduleSyncStatus.text = String.format(
            Locale.US,
            "Synced · %.1f hrs · %s",
            countdown.hoursUntilNextOpening,
            countdown.flowState.badgeLabel
        )
    }

    fun recalculateRunoutBuffer() {
        val drumsCount = viewModel.drums200L.value
        val overheadCount = viewModel.tanks1000L.value
        val gallonsCount = viewModel.gallons20L.value
        val fillPercent = viewModel.selectedFillPercentage.value.coerceIn(0.0, 100.0)

        val dailyBurn = viewModel.dailyConsumption.value.coerceAtLeast(0.0)

        val countdown = ScheduleCountdownCalculator.calculate(
            zone = viewModel.selectedZone.value,
            userMode = viewModel.userMode.value,
            currentTime = LocalDateTime.now()
        )
        val nextSupplyHours = (viewModel.customSupplyHours.value ?: countdown.hoursUntilNextOpening).coerceAtLeast(0.0)

        val inventory = StorageInventory(
            blueDrums200L = drumsCount,
            overheadTanks1000L = overheadCount,
            gallons20L = gallonsCount,
            fillPercentage = fillPercent
        )

        val result = RunoutEstimator.calculate(
            inventory = inventory,
            dailyBurnRateLiters = dailyBurn,
            hoursUntilNextSupply = nextSupplyHours
        )

        binding.tvStoredTotalCapacity.text = String.format(Locale.US, "%.0f L", result.totalCapacityLiters)
        binding.tvStoredCurrentVolume.text = String.format(Locale.US, "%.0f L", result.currentStoredLiters)
        binding.tvRunoutHoursValue.text = if (result.runoutHours.isInfinite()) "∞" else String.format(Locale.US, "%.1f hrs", result.runoutHours)

        if (result.isDepletedBeforeNextSupply) {
            // Deficit Risk (Rose)
            binding.cardRunoutStatus.setBackgroundResource(R.drawable.bg_card_danger)
            binding.tvRunoutStatusBadge.text = "Deficit risk"
            binding.tvRunoutStatusBadge.setTextColor(activity.getColor(R.color.status_alert))
            binding.tvRunoutStatusTitle.text = String.format(Locale.US, "%.0f L short", result.deficitLiters)
            binding.tvRunoutStatusTitle.setTextColor(activity.getColor(R.color.status_alert))
            binding.tvRunoutStatusMessage.text = String.format(
                Locale.US,
                "Reserves run dry %.1f hrs before the next supply window, leaving a %.0f L shortfall.",
                -result.safetyBufferHours,
                result.deficitLiters
            )
        } else {
            // Surplus Buffer (Emerald)
            binding.cardRunoutStatus.setBackgroundResource(R.drawable.bg_card_safe)
            binding.tvRunoutStatusBadge.text = "Surplus"
            binding.tvRunoutStatusBadge.setTextColor(activity.getColor(R.color.status_ok))
            val bufferLabel = if (result.safetyBufferHours.isInfinite()) "Indefinite" else String.format(Locale.US, "+%.1f hrs", result.safetyBufferHours)
            binding.tvRunoutStatusTitle.text = bufferLabel
            binding.tvRunoutStatusTitle.setTextColor(activity.getColor(R.color.status_ok))
            binding.tvRunoutStatusMessage.text = if (result.safetyBufferHours.isInfinite()) {
                "No consumption set. Stored water lasts indefinitely."
            } else {
                String.format(
                    Locale.US,
                    "Stored water outlasts the next supply window by %.1f hrs.",
                    result.safetyBufferHours
                )
            }
        }

        val effectiveFlowState = if (viewModel.customSupplyHours.value != null) {
            ScheduleFlowState.RATIONING_ACTIVE
        } else {
            countdown.flowState
        }

        val targetUi = TargetRecommendationFormatter.format(
            result = result,
            flowState = effectiveFlowState,
            hoursUntilNextSupply = nextSupplyHours
        )

        if (targetUi.isDeficitRisk) {
            binding.cardRecommendedTarget.setBackgroundResource(R.drawable.bg_target_box_danger)
            binding.tvTargetLabel.setTextColor(activity.getColor(R.color.status_alert))
            binding.tvRecommendedTargetValue.text = targetUi.targetValueText
            binding.tvRecommendedTargetValue.setTextColor(activity.getColor(R.color.status_alert))
            binding.tvRecommendedTargetSubtext.text = targetUi.subtext
        } else {
            binding.cardRecommendedTarget.setBackgroundResource(R.drawable.bg_target_box_safe)
            binding.tvTargetLabel.setTextColor(activity.getColor(R.color.ink_muted))
            binding.tvRecommendedTargetValue.text = targetUi.targetValueText
            val valueColor = if (nextSupplyHours <= 0.0 && effectiveFlowState == ScheduleFlowState.RATIONING_ACTIVE) {
                activity.getColor(R.color.ink_muted)
            } else {
                activity.getColor(R.color.status_ok)
            }
            binding.tvRecommendedTargetValue.setTextColor(valueColor)
            binding.tvRecommendedTargetSubtext.text = targetUi.subtext
        }
    }

    private fun setupFillLevelPresets() {
        val fillButtons = listOf(
            Triple(binding.btnFillEmpty, 0.0, "Empty / dry"),
            Triple(binding.btnFillQuarter, 25.0, "Quarter full (quarter-line mark)"),
            Triple(binding.btnFillHalf, 50.0, "Half full (middle ridge line)"),
            Triple(binding.btnFillThreeQuarter, 75.0, "Three quarters full (upper ring)"),
            Triple(binding.btnFillFull, 100.0, "Full to the brim / top rim")
        )

        fillButtons.forEach { (btn, percent, _) ->
            btn.setOnClickListener {
                viewModel.setSelectedFillPercentage(percent)
                userPreferences.fillPercentage = percent
            }
        }

        binding.btnFillLevelInfo.setOnClickListener {
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.fill_level_info_title)
                .setMessage(R.string.fill_level_info_message)
                .setPositiveButton(R.string.dialog_ok, null)
                .show()
        }

        updateFillLevelUi(viewModel.selectedFillPercentage.value)
    }

    private fun updateFillLevelUi(percent: Double) {
        val fillButtons = listOf(
            Triple(binding.btnFillEmpty, 0.0, "Empty / dry"),
            Triple(binding.btnFillQuarter, 25.0, "Quarter full (quarter-line mark)"),
            Triple(binding.btnFillHalf, 50.0, "Half full (middle ridge line)"),
            Triple(binding.btnFillThreeQuarter, 75.0, "Three quarters full (upper ring)"),
            Triple(binding.btnFillFull, 100.0, "Full to the brim / top rim")
        )
        val activeBg = R.drawable.bg_pill_active
        val inactiveBg = R.drawable.bg_pill_inactive
        val activeColor = activity.getColor(R.color.white)
        val inactiveColor = activity.getColor(R.color.ink_soft)

        var selectedHint = "Empty / dry"
        fillButtons.forEach { (btn, p, hint) ->
            if (p == percent) {
                btn.setBackgroundResource(activeBg)
                btn.setTextColor(activeColor)
                selectedHint = hint
            } else {
                btn.setBackgroundResource(inactiveBg)
                btn.setTextColor(inactiveColor)
            }
        }

        val statusTag = when (percent) {
            0.0 -> "0% · EMPTY"
            25.0 -> "25% · 1/4 FULL"
            50.0 -> "50% · 1/2 FULL"
            75.0 -> "75% · 3/4 FULL"
            else -> "100% · FULL"
        }
        binding.tvFillPercentageDisplay.text = statusTag
        binding.tvFillLevelHint.text = selectedHint
    }

    private fun formatConsumption(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toLong().toString()
        } else {
            value.toString()
        }
    }

    private fun updateDailyConsumptionUi(consumption: Double) {
        if (InputSyncHelper.shouldUpdateConsumption(binding.etDailyConsumption.text, consumption)) {
            binding.etDailyConsumption.setText(formatConsumption(consumption))
        }
    }

    private fun updateContainerCountUi(editText: EditText, count: Int) {
        if (InputSyncHelper.shouldUpdateContainerCount(editText.text, count)) {
            editText.setText(count.toString())
        }
    }

    fun persistContainerCounts() {
        if (!viewModel.isInitialized) return
        userPreferences.saveContainerCounts(
            drums = viewModel.drums200L.value,
            tanks = viewModel.tanks1000L.value,
            gallons = viewModel.gallons20L.value
        )
    }

    fun persistPreferences() {
        if (!viewModel.isInitialized) return
        persistContainerCounts()
        userPreferences.fillPercentage = viewModel.selectedFillPercentage.value
        userPreferences.dailyConsumption = viewModel.dailyConsumption.value
    }
}
