package com.waterall.ui

import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.waterall.R
import com.waterall.databinding.ActivityMainBinding
import com.waterall.databinding.ItemScheduleCardBinding
import com.waterall.domain.calculator.CountdownCalculationResult
import com.waterall.domain.calculator.RunoutEstimator
import com.waterall.domain.calculator.ScheduleCountdownCalculator
import com.waterall.domain.calculator.ScheduleFlowState
import com.waterall.domain.calculator.ZoneConsensus
import com.waterall.domain.calculator.ZoneConsensusCalculator
import com.waterall.domain.model.BarangayZone
import com.waterall.domain.model.GroundTruthReport
import com.waterall.domain.model.StorageInventory
import com.waterall.domain.model.SupplyWindow
import com.waterall.domain.model.TapFlowStatus
import com.waterall.domain.model.UserMode
import com.waterall.domain.repository.ReportRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.util.Locale


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var userPreferences: UserPreferences
    private var uiCoroutineJob: Job? = null
    private var isAutoPopulatingScheduleHours = false

    // Mock Baguio Water District Rotation Data
    internal val zones: List<BarangayZone> = MainViewModel.DEFAULT_ZONES

    private var countdownTimer: CountDownTimer? = null
    private lateinit var viewModel: MainViewModel
    private val reportRepository: ReportRepository
        get() = viewModel.reportRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this, MainViewModel.Factory)[MainViewModel::class.java]

        userPreferences = UserPreferences(this)
        // Hydrate from UserPreferences whenever ViewModel is freshly created.
        // We check !viewModel.isInitialized instead of savedInstanceState == null so that
        // when Android recreates the Activity after process death (where savedInstanceState != null
        // but ViewModelProvider returns a fresh zero-initialized ViewModel), user preferences are
        // properly restored rather than skipped and subsequently overwritten with zero defaults.
        if (!viewModel.isInitialized) {
            viewModel.hydrateFromPreferences(userPreferences, zones)
        }

        setupBottomNavigation()
        setupZoneSelector()
        setupModeToggle()
        setupReservesCalculator()
        setupReportFeature()

        binding.btnQuickReport.setOnClickListener {
            switchTab(Tab.REPORT)
        }

        binding.btnLoadSampleReports.setOnClickListener {
            val now = System.currentTimeMillis()
            viewModel.loadSampleReports(now)
            val activeReports = reportRepository.getAllReports().filter { activeReport ->
                (now - activeReport.timestamp) <= ZoneConsensusCalculator.REPORT_EXPIRATION_WINDOW_MS
            }
            userPreferences.saveReports(activeReports)
            Toast.makeText(
                this,
                "Sample community reports loaded for live demo",
                Toast.LENGTH_SHORT
            ).show()
            updateConsensusCard()
        }
    }


    private fun setupBottomNavigation() {
        binding.navCalendar.setOnClickListener { switchTab(Tab.CALENDAR) }
        binding.navReserves.setOnClickListener { switchTab(Tab.RESERVES) }
        binding.navReport.setOnClickListener { switchTab(Tab.REPORT) }
        binding.btnBack.setOnClickListener { switchTab(Tab.CALENDAR) }
    }

    private fun switchTab(tab: Tab) {
        viewModel.setCurrentTab(tab)
    }

    private fun applyTab(tab: Tab) {
        binding.viewCalendar.visibility = if (tab == Tab.CALENDAR) View.VISIBLE else View.GONE
        binding.viewReserves.visibility = if (tab == Tab.RESERVES) View.VISIBLE else View.GONE
        binding.viewReport.visibility = if (tab == Tab.REPORT) View.VISIBLE else View.GONE

        binding.btnBack.visibility = if (tab == Tab.CALENDAR) View.GONE else View.VISIBLE

        binding.tvScreenTitle.text = when (tab) {
            Tab.CALENDAR -> getString(R.string.title_hydro_track)
            Tab.RESERVES -> "Reserves"
            Tab.REPORT -> "Report"
        }

        if (tab == Tab.CALENDAR) {
            updateConsensusCard()
        }

        if (tab == Tab.RESERVES) {
            if (binding.etHoursUntilNextSupply.text.isNullOrBlank()) {
                autoPopulateScheduleHours()
            }
        }

        if (tab == Tab.REPORT) {
            binding.tvReportZoneName.text = viewModel.selectedZone.value.name
        }

        // Highlight selected nav tab
        val activeColor = getColor(R.color.ink)
        val inactiveColor = getColor(R.color.ink_faint)

        binding.tvNavCalendar.setTextColor(if (tab == Tab.CALENDAR) activeColor else inactiveColor)
        binding.tvNavReserves.setTextColor(if (tab == Tab.RESERVES) activeColor else inactiveColor)
        binding.tvNavReport.setTextColor(if (tab == Tab.REPORT) activeColor else inactiveColor)
    }

    // --- FEATURE 1: Barangay Water Rotation Calendar & Countdown ---
    private fun setupZoneSelector() {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            zones.map { it.name }
        )
        binding.spinnerZones.adapter = adapter
        val currentZone = viewModel.selectedZone.value
        val zoneIndex = zones.indexOfFirst { it.id == currentZone.id }.let { if (it >= 0) it else 0 }
        binding.spinnerZones.setSelection(zoneIndex, false)

        binding.spinnerZones.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val newZone = zones[position]
                if (newZone.id != viewModel.selectedZone.value.id) {
                    viewModel.setSelectedZone(newZone)
                    userPreferences.selectedZoneId = newZone.id
                    binding.tvConsensusAcknowledgment.visibility = View.GONE
                    autoPopulateScheduleHours()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupModeToggle() {
        binding.btnModeDomestic.setOnClickListener {
            if (viewModel.userMode.value != UserMode.DOMESTIC) {
                viewModel.setUserMode(UserMode.DOMESTIC)
                userPreferences.userMode = UserMode.DOMESTIC
                autoPopulateScheduleHours()
            }
        }
        binding.btnModeCommercial.setOnClickListener {
            if (viewModel.userMode.value != UserMode.COMMERCIAL) {
                viewModel.setUserMode(UserMode.COMMERCIAL)
                userPreferences.userMode = UserMode.COMMERCIAL
                autoPopulateScheduleHours()
            }
        }
    }

    private fun updateModeToggleUi(mode: UserMode) {
        if (mode == UserMode.DOMESTIC) {
            binding.btnModeDomestic.setBackgroundResource(R.drawable.bg_pill_active)
            binding.btnModeDomestic.setTextColor(getColor(R.color.white))
            binding.btnModeCommercial.setBackgroundResource(R.drawable.bg_pill_inactive)
            binding.btnModeCommercial.setTextColor(getColor(R.color.ink_soft))
        } else {
            binding.btnModeCommercial.setBackgroundResource(R.drawable.bg_pill_active)
            binding.btnModeCommercial.setTextColor(getColor(R.color.white))
            binding.btnModeDomestic.setBackgroundResource(R.drawable.bg_pill_inactive)
            binding.btnModeDomestic.setTextColor(getColor(R.color.ink_soft))
        }
    }

    private fun updateScheduleCards() {
        val schedules = viewModel.selectedZone.value.getSchedules(viewModel.userMode.value)
        binding.layoutScheduleContainer.removeAllViews()

        if (schedules.isEmpty()) {
            binding.tvNoScheduleMessage.visibility = View.VISIBLE
            return
        }
        binding.tvNoScheduleMessage.visibility = View.GONE

        schedules.forEachIndexed { index, schedule ->
            if (index > 0) {
                val divider = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        (1 * resources.displayMetrics.density).toInt()
                    ).apply {
                        marginStart = (48 * resources.displayMetrics.density).toInt()
                    }
                    setBackgroundColor(getColor(R.color.hairline))
                }
                binding.layoutScheduleContainer.addView(divider)
            }

            val cardBinding = ItemScheduleCardBinding.inflate(layoutInflater, binding.layoutScheduleContainer, false)
            cardBinding.tvScheduleDayBadge.text = schedule.dayOfWeek.name.take(1)
            cardBinding.tvScheduleDay.text = schedule.dateLabel
            cardBinding.tvScheduleTime.text = schedule.formattedRange
            binding.layoutScheduleContainer.addView(cardBinding.root)
        }
    }

    private fun stopCountdownTimer() {
        countdownTimer?.cancel()
        countdownTimer = null
    }

    /**
     * Safely unbinds all CountDownTimer instances and UI coroutines per PRINCIPLES.md:37-38.
     */
    private fun stopTimersAndCoroutines() {
        stopCountdownTimer()
        uiCoroutineJob?.cancel()
        uiCoroutineJob = null
    }

    /**
     * Retains container counts to lightweight SharedPreferences enforcing Law 3 invariants.
     */
    private fun persistContainerCounts() {
        if (!::userPreferences.isInitialized || !::viewModel.isInitialized || !viewModel.isInitialized) return
        userPreferences.saveContainerCounts(
            drums = viewModel.drums200L.value,
            tanks = viewModel.tanks1000L.value,
            gallons = viewModel.gallons20L.value
        )
    }

    private fun startLiveCountdown() {
        stopCountdownTimer()

        val calculation = ScheduleCountdownCalculator.calculate(
            zone = viewModel.selectedZone.value,
            userMode = viewModel.userMode.value,
            currentTime = LocalDateTime.now()
        )

        updateCountdownUi(calculation)

        if (calculation.remainingSeconds <= 0L) {
            return
        }

        countdownTimer = object : CountDownTimer(calculation.remainingSeconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val totalSecs = millisUntilFinished / 1000L
                val hrs = totalSecs / 3600
                val mins = (totalSecs % 3600) / 60
                val secs = totalSecs % 60
                binding.tvCountdownTimer.text = String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
            }

            override fun onFinish() {
                // Law 2 Invariant 3: Deterministic transition to next state upon expiry
                startLiveCountdown()
            }
        }.start()
    }

    private fun updateCountdownUi(result: CountdownCalculationResult) {
        binding.tvCountdownTimer.text = result.formattedCountdown

        when (result.flowState) {
            ScheduleFlowState.FLOW_ACTIVE -> {
                binding.tvFlowBadge.text = "Flow active"
                binding.tvFlowBadge.setTextColor(getColor(R.color.status_ok))
                binding.flowPulseDot.setBackgroundResource(R.drawable.bg_dot_active)
                binding.tvCountdownSubtext.text = "Remaining supply window time"
            }
            ScheduleFlowState.RATIONING_ACTIVE -> {
                binding.tvFlowBadge.text = "Rationing"
                binding.tvFlowBadge.setTextColor(getColor(R.color.status_warn))
                binding.flowPulseDot.setBackgroundResource(R.drawable.bg_dot_inactive)
                binding.tvCountdownSubtext.text = "Time until next supply window"
            }
        }
    }


    // --- FEATURE 2: Drum Reserve & Supply Runout Estimator (SDG 6 Exclusive) ---
    private fun setupReservesCalculator() {
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

        // Sync with Phase 1 Schedule Countdown button
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

    /**
     * Discrete Baguio drum fill level selector (Empty, 1/4, 1/2, 3/4, Full) with physical visual cues.
     */
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
            MaterialAlertDialogBuilder(this)
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
        val activeColor = getColor(R.color.white)
        val inactiveColor = getColor(R.color.ink_soft)

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
        val currentTextValue = binding.etDailyConsumption.text?.toString()?.toDoubleOrNull() ?: 0.0
        if (currentTextValue != consumption) {
            binding.etDailyConsumption.setText(formatConsumption(consumption))
        }
    }

    private fun updateContainerCountUi(editText: EditText, count: Int) {
        val current = editText.text?.toString()?.toIntOrNull() ?: 0
        if (current != count) {
            editText.setText(count.toString())
        }
    }

    /**
     * Interconnects the reserve estimator with the schedule engine from Phase 1.
     * Auto-populates hoursUntilNextSupply from CountdownCalculationResult.hoursUntilNextOpening.
     */
    private fun autoPopulateScheduleHours() {
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

    /**
     * Recalculates storage capacity, current volume, remaining runout buffer hours,
     * and recommended daily consumption target.
     *
     * Validates [ScheduleFlowState.FLOW_ACTIVE] directly via [TargetRecommendationFormatter]
     * rather than inferring flow from infinite daily targets. This prevents empty schedules
     * or 0-hour supply inputs (which return [ScheduleFlowState.RATIONING_ACTIVE] with 0.0 hours)
     * from falsely showing residents an active supply refill message.
     * When a manual supply hours override is active, [ScheduleFlowState.RATIONING_ACTIVE] is supplied
     * to ensure the manual override target is properly formatted instead of showing "Unrestricted".
     */
    private fun recalculateRunoutBuffer() {
        val drumsCount = viewModel.drums200L.value
        val overheadCount = viewModel.tanks1000L.value
        val gallonsCount = viewModel.gallons20L.value
        val fillPercent = viewModel.selectedFillPercentage.value.coerceIn(0.0, 100.0)

        val dailyBurn = viewModel.dailyConsumption.value.coerceAtLeast(0.0)

        // Auto-fallback to Phase 1 hoursUntilNextOpening if override is not set
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

        // Visual status color transition: Emerald for Surplus Buffer vs. Rose for Deficit Risk
        if (result.isDepletedBeforeNextSupply) {
            // Deficit Risk (Rose)
            binding.cardRunoutStatus.setBackgroundResource(R.drawable.bg_card_danger)
            binding.tvRunoutStatusBadge.text = "Deficit risk"
            binding.tvRunoutStatusBadge.setTextColor(getColor(R.color.status_alert))
            binding.tvRunoutStatusTitle.text = String.format(Locale.US, "%.0f L short", result.deficitLiters)
            binding.tvRunoutStatusTitle.setTextColor(getColor(R.color.status_alert))
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
            binding.tvRunoutStatusBadge.setTextColor(getColor(R.color.status_ok))
            val bufferLabel = if (result.safetyBufferHours.isInfinite()) "Indefinite" else String.format(Locale.US, "+%.1f hrs", result.safetyBufferHours)
            binding.tvRunoutStatusTitle.text = bufferLabel
            binding.tvRunoutStatusTitle.setTextColor(getColor(R.color.status_ok))
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

        // Recommended Daily Consumption Target
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
            binding.tvTargetLabel.setTextColor(getColor(R.color.status_alert))
            binding.tvRecommendedTargetValue.text = targetUi.targetValueText
            binding.tvRecommendedTargetValue.setTextColor(getColor(R.color.status_alert))
            binding.tvRecommendedTargetSubtext.text = targetUi.subtext
        } else {
            binding.cardRecommendedTarget.setBackgroundResource(R.drawable.bg_target_box_safe)
            binding.tvTargetLabel.setTextColor(getColor(R.color.ink_muted))
            binding.tvRecommendedTargetValue.text = targetUi.targetValueText
            val valueColor = if (nextSupplyHours <= 0.0 && effectiveFlowState == ScheduleFlowState.RATIONING_ACTIVE) {
                getColor(R.color.ink_muted)
            } else {
                getColor(R.color.status_ok)
            }
            binding.tvRecommendedTargetValue.setTextColor(valueColor)
            binding.tvRecommendedTargetSubtext.text = targetUi.subtext
        }
    }

    // --- FEATURE 3: Crowdsourced Tap Flow & Pressure Verification ---
    private fun setupReportFeature() {
        binding.tvReportZoneName.text = viewModel.selectedZone.value.name

        binding.btnSubmitReport.setOnClickListener {
            val selectedRadioId = binding.rgWaterStatus.checkedRadioButtonId
            if (selectedRadioId == -1) {
                Toast.makeText(this, "Select the water status at your tap.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val status = when (selectedRadioId) {
                R.id.rbStatusRunning -> TapFlowStatus.RUNNING
                R.id.rbStatusLowPressure -> TapFlowStatus.LOW_PRESSURE
                else -> TapFlowStatus.DRY
            }

            val notes = binding.etReportNotes.text.toString().trim()
            val currentZone = viewModel.selectedZone.value
            val report = GroundTruthReport(
                id = "rep_${System.currentTimeMillis()}",
                zoneId = currentZone.id,
                zoneName = currentZone.name,
                status = status,
                notes = notes,
                timestamp = System.currentTimeMillis()
            )

            // Feed report into the in-memory repository to close the feedback loop
            reportRepository.addReport(report)
            val now = System.currentTimeMillis()
            val activeReports = reportRepository.getAllReports().filter { activeReport ->
                (now - activeReport.timestamp) <= ZoneConsensusCalculator.REPORT_EXPIRATION_WINDOW_MS
            }
            userPreferences.saveReports(activeReports)

            // Immediate UI acknowledgment: Toast notification and consensus card confirmation
            Toast.makeText(
                this,
                "Report submitted for ${report.zoneName}",
                Toast.LENGTH_LONG
            ).show()

            binding.tvConsensusAcknowledgment.visibility = View.VISIBLE
            binding.tvConsensusAcknowledgment.text =
                "Report recorded: ${report.status.label}. Status refreshed."

            binding.etReportNotes.setText("")
            binding.rgWaterStatus.clearCheck()

            // Immediate status refresh on user's view
            updateConsensusCard()
            switchTab(Tab.CALENDAR)
        }
    }

    /**
     * Refreshes the in-memory zone consensus card beneath the schedule,
     * computing verified report distribution, dominant status, and relative timestamp.
     */
    private fun updateConsensusCard() {
        val currentZone = viewModel.selectedZone.value
        val consensus = try {
            reportRepository.getConsensus(
                zoneId = currentZone.id,
                zoneName = currentZone.name,
                currentTimeMillis = System.currentTimeMillis()
            )
        } catch (e: IllegalArgumentException) {
            val shortZoneName = currentZone.name.substringBefore("—").substringBefore("-").trim().ifBlank { currentZone.name }
            ZoneConsensus(
                zoneId = currentZone.id,
                zoneName = currentZone.name,
                totalReports = 0,
                statusCounts = emptyMap(),
                statusPercentages = emptyMap(),
                dominantStatus = null,
                summaryText = "$shortZoneName: No verified reports today",
                latestReport = null,
                formattedRelativeTime = null
            )
        }

        binding.tvConsensusSummary.text = consensus.summaryText

        if (consensus.totalReports == 0) {
            binding.tvConsensusBadge.text = "No reports"
            binding.tvConsensusBadge.setTextColor(getColor(R.color.ink_muted))
            binding.tvConsensusTimestamp.text = "Today"
            binding.tvConsensusDetails.text = "Be the first to report for this zone."
        } else {
            val latest = consensus.latestReport
            val relativeTime = consensus.formattedRelativeTime ?: "Recently"
            binding.tvConsensusTimestamp.text = relativeTime

            val dominant = consensus.dominantStatus
            val badgeColor = when (dominant) {
                TapFlowStatus.RUNNING -> getColor(R.color.status_ok)
                TapFlowStatus.LOW_PRESSURE -> getColor(R.color.status_warn)
                TapFlowStatus.DRY -> getColor(R.color.status_alert)
                null -> getColor(R.color.ink_muted)
            }
            val dominantPct = dominant?.let { consensus.statusPercentages[it] } ?: 0
            val dominantLabel = dominant?.let {
                when (it) {
                    TapFlowStatus.RUNNING -> "RUNNING"
                    TapFlowStatus.LOW_PRESSURE -> "LOW PRESSURE"
                    TapFlowStatus.DRY -> "DRY"
                }
            } ?: "CONSENSUS"

            binding.tvConsensusBadge.text = "$dominantPct% $dominantLabel"
            binding.tvConsensusBadge.setTextColor(badgeColor)

            val notesStr = if (!latest?.notes.isNullOrBlank()) " (\"${latest?.notes}\")" else ""
            binding.tvConsensusDetails.text = "Latest: ${latest?.status?.label ?: "Unknown"}$notesStr · $relativeTime"
        }
    }

    /**
     * Observes all 9 StateFlows from [MainViewModel], keeping UI components in sync
     * and driving reserve buffer calculations via a single unified combine pipeline.
     */
    private fun observeViewModelState() {
        uiCoroutineJob?.cancel()
        uiCoroutineJob = lifecycleScope.launch {
            launch {
                combine(viewModel.selectedZone, viewModel.userMode) { zone, mode ->
                    zone to mode
                }.collect { (zone, mode) ->
                    val index = zones.indexOfFirst { it.id == zone.id }
                    if (index >= 0 && binding.spinnerZones.selectedItemPosition != index) {
                        binding.spinnerZones.setSelection(index, false)
                    }
                    binding.tvReportZoneName.text = zone.name
                    updateModeToggleUi(mode)
                    updateScheduleCards()
                    startLiveCountdown()
                    updateConsensusCard()
                }
            }
            launch {
                viewModel.currentTab.collect { tab ->
                    applyTab(tab)
                }
            }
            launch {
                viewModel.selectedFillPercentage.collect { percent ->
                    updateFillLevelUi(percent)
                }
            }
            launch {
                viewModel.dailyConsumption.collect { consumption ->
                    updateDailyConsumptionUi(consumption)
                }
            }
            launch {
                viewModel.drums200L.collect { count ->
                    updateContainerCountUi(binding.etDrums200L, count)
                    persistContainerCounts()
                }
            }
            launch {
                viewModel.tanks1000L.collect { count ->
                    updateContainerCountUi(binding.etTanks1000L, count)
                    persistContainerCounts()
                }
            }
            launch {
                viewModel.gallons20L.collect { count ->
                    updateContainerCountUi(binding.etGallons20L, count)
                    persistContainerCounts()
                }
            }
            launch {
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
            launch {
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
    }

    /**
     * Retains container counts, user mode, fill level, and daily consumption to lightweight SharedPreferences.
     */
    private fun persistUserPreferences() {
        if (!::userPreferences.isInitialized || !::viewModel.isInitialized || !viewModel.isInitialized) return
        persistContainerCounts()
        userPreferences.userMode = viewModel.userMode.value
        userPreferences.fillPercentage = viewModel.selectedFillPercentage.value
        userPreferences.dailyConsumption = viewModel.dailyConsumption.value
    }

    override fun onStart() {
        super.onStart()
        observeViewModelState()
    }

    override fun onStop() {
        super.onStop()
        persistUserPreferences()
        stopTimersAndCoroutines()
    }

    override fun onDestroy() {
        super.onDestroy()
        persistUserPreferences()
        stopTimersAndCoroutines()
    }
}
