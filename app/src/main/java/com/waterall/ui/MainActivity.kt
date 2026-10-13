package com.waterall.ui

import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.waterall.R
import com.waterall.databinding.ActivityMainBinding
import com.waterall.domain.calculator.CountdownCalculationResult
import com.waterall.domain.calculator.RunoutEstimator
import com.waterall.domain.calculator.ScheduleCountdownCalculator
import com.waterall.domain.calculator.ScheduleFlowState
import com.waterall.domain.model.BarangayZone
import com.waterall.domain.model.GroundTruthReport
import com.waterall.domain.model.StorageInventory
import com.waterall.domain.model.SupplyWindow
import com.waterall.domain.model.TapFlowStatus
import com.waterall.domain.model.UserMode
import java.time.LocalDateTime
import java.util.Locale


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Mock Baguio Water District Rotation Data
    private val zones = listOf(
        BarangayZone(
            id = "brgy4",
            name = "Zone 4 — Brgy. San Isidro",
            domesticSchedules = listOf(
                SupplyWindow("Mon", "Mon 09", 6, 0, 10, 0),
                SupplyWindow("Wed", "Wed 11", 5, 0, 9, 0),
                SupplyWindow("Fri", "Fri 13", 6, 0, 11, 0)
            ),
            commercialSchedules = listOf(
                SupplyWindow("Tue", "Tue 10", 8, 0, 14, 0),
                SupplyWindow("Thu", "Thu 12", 8, 0, 14, 0)
            )
        ),
        BarangayZone(
            id = "brgy1",
            name = "Zone 1 — Brgy. Cabinet Hill",
            domesticSchedules = listOf(
                SupplyWindow("Tue", "Tue 10", 5, 30, 9, 30),
                SupplyWindow("Thu", "Thu 12", 5, 30, 9, 30),
                SupplyWindow("Sat", "Sat 14", 6, 0, 12, 0)
            ),
            commercialSchedules = listOf(
                SupplyWindow("Mon", "Mon 09", 7, 0, 13, 0),
                SupplyWindow("Wed", "Wed 11", 7, 0, 13, 0)
            )
        ),
        BarangayZone(
            id = "brgy7",
            name = "Zone 7 — Brgy. Trancoville",
            domesticSchedules = listOf(
                SupplyWindow("Wed", "Wed 11", 6, 0, 10, 30),
                SupplyWindow("Sat", "Sat 14", 5, 0, 10, 0)
            ),
            commercialSchedules = listOf(
                SupplyWindow("Fri", "Fri 13", 8, 0, 15, 0)
            )
        )
    )

    private var selectedZone: BarangayZone = zones[0]
    private var currentUserMode: UserMode = UserMode.DOMESTIC
    private var countdownTimer: CountDownTimer? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBottomNavigation()
        setupZoneSelector()
        setupModeToggle()
        startLiveCountdown()
        setupReservesCalculator()
        setupReportFeature()
    }

    private fun setupBottomNavigation() {
        binding.navCalendar.setOnClickListener { switchTab(Tab.CALENDAR) }
        binding.navReserves.setOnClickListener { switchTab(Tab.RESERVES) }
        binding.navReport.setOnClickListener { switchTab(Tab.REPORT) }
        binding.btnBack.setOnClickListener { switchTab(Tab.CALENDAR) }
    }

    private enum class Tab { CALENDAR, RESERVES, REPORT }

    private fun switchTab(tab: Tab) {
        binding.viewCalendar.visibility = if (tab == Tab.CALENDAR) View.VISIBLE else View.GONE
        binding.viewReserves.visibility = if (tab == Tab.RESERVES) View.VISIBLE else View.GONE
        binding.viewReport.visibility = if (tab == Tab.REPORT) View.VISIBLE else View.GONE

        binding.btnBack.visibility = if (tab == Tab.CALENDAR) View.GONE else View.VISIBLE

        binding.tvScreenTitle.text = when (tab) {
            Tab.CALENDAR -> getString(R.string.title_hydro_track)
            Tab.RESERVES -> "Drum Reserve & Runout"
            Tab.REPORT -> "Water Flow Ground Truth"
        }

        // Highlight selected nav tab
        val activeColor = getColor(R.color.slate_900)
        val inactiveColor = getColor(R.color.slate_400)

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
        binding.spinnerZones.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedZone = zones[position]
                binding.tvReportZoneName.text = selectedZone.name
                updateScheduleCards()
                startLiveCountdown()
                recalculateRunoutBuffer()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupModeToggle() {
        binding.btnModeDomestic.setOnClickListener {
            currentUserMode = UserMode.DOMESTIC
            binding.btnModeDomestic.setBackgroundResource(R.drawable.bg_pill_active)
            binding.btnModeDomestic.setTextColor(getColor(R.color.white))
            binding.btnModeCommercial.setBackgroundResource(R.drawable.bg_pill_inactive)
            binding.btnModeCommercial.setTextColor(getColor(R.color.slate_700))
            updateScheduleCards()
            startLiveCountdown()
            recalculateRunoutBuffer()
        }

        binding.btnModeCommercial.setOnClickListener {
            currentUserMode = UserMode.COMMERCIAL
            binding.btnModeCommercial.setBackgroundResource(R.drawable.bg_pill_active)
            binding.btnModeCommercial.setTextColor(getColor(R.color.white))
            binding.btnModeDomestic.setBackgroundResource(R.drawable.bg_pill_inactive)
            binding.btnModeDomestic.setTextColor(getColor(R.color.slate_700))
            updateScheduleCards()
            startLiveCountdown()
            recalculateRunoutBuffer()
        }
    }


    private fun updateScheduleCards() {
        val schedules = if (currentUserMode == UserMode.DOMESTIC) {
            selectedZone.domesticSchedules
        } else {
            selectedZone.commercialSchedules
        }

        val card1 = binding.scheduleItem1
        val card2 = binding.scheduleItem2
        val card3 = binding.scheduleItem3

        if (schedules.isNotEmpty()) {
            card1.root.visibility = View.VISIBLE
            card1.tvScheduleDayBadge.text = schedules[0].dayOfWeek.first().toString()
            card1.tvScheduleDay.text = schedules[0].dateLabel
            card1.tvScheduleTime.text = schedules[0].formattedRange
        } else {
            card1.root.visibility = View.GONE
        }

        if (schedules.size > 1) {
            card2.root.visibility = View.VISIBLE
            card2.tvScheduleDayBadge.text = schedules[1].dayOfWeek.first().toString()
            card2.tvScheduleDay.text = schedules[1].dateLabel
            card2.tvScheduleTime.text = schedules[1].formattedRange
        } else {
            card2.root.visibility = View.GONE
        }

        if (schedules.size > 2) {
            card3.root.visibility = View.VISIBLE
            card3.tvScheduleDayBadge.text = schedules[2].dayOfWeek.first().toString()
            card3.tvScheduleDay.text = schedules[2].dateLabel
            card3.tvScheduleTime.text = schedules[2].formattedRange
        } else {
            card3.root.visibility = View.GONE
        }
    }

    private fun startLiveCountdown() {
        countdownTimer?.cancel()

        val calculation = ScheduleCountdownCalculator.calculate(
            zone = selectedZone,
            userMode = currentUserMode,
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
                binding.tvFlowBadge.text = "FLOW ACTIVE"
                binding.tvFlowBadge.setTextColor(getColor(R.color.emerald_400))
                binding.flowPulseDot.setBackgroundResource(R.drawable.bg_dot_active)
                binding.tvCountdownSubtext.text = "Remaining supply window time"
            }
            ScheduleFlowState.RATIONING_ACTIVE -> {
                binding.tvFlowBadge.text = "RATIONING ACTIVE"
                binding.tvFlowBadge.setTextColor(getColor(R.color.amber_500))
                binding.flowPulseDot.setBackgroundResource(R.drawable.bg_dot_inactive)
                binding.tvCountdownSubtext.text = "Time until next supply window"
            }
        }
    }


    // --- FEATURE 2: Drum Reserve & Supply Runout Estimator (SDG 6 Exclusive) ---
    private fun setupReservesCalculator() {
        binding.btnCalculateRunout.setOnClickListener {
            recalculateRunoutBuffer()
        }
    }

    private fun recalculateRunoutBuffer() {
        val drumsCount = binding.etDrums200L.text.toString().toIntOrNull() ?: 2
        val overheadCount = binding.etTanks1000L.text.toString().toIntOrNull() ?: 0
        val gallonsCount = binding.etGallons20L.text.toString().toIntOrNull() ?: 2
        val fillPercent = binding.sliderFill.progress.toDouble().coerceIn(0.0, 100.0)

        val dailyBurn = binding.etDailyConsumption.text.toString().toDoubleOrNull() ?: 250.0
        val nextSupplyHours = binding.etHoursUntilNextSupply.text.toString().toDoubleOrNull() ?: 26.0

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
        binding.tvRunoutHoursValue.text = if (result.runoutHours.isInfinite()) "∞ hrs" else String.format(Locale.US, "%.1f hrs", result.runoutHours)

        if (result.isDepletedBeforeNextSupply) {
            binding.cardRunoutStatus.setBackgroundResource(R.drawable.bg_card_danger)
            binding.tvRunoutStatusTitle.text = "DEFICIT WARNING"
            binding.tvRunoutStatusTitle.setTextColor(getColor(R.color.rose_500))
            binding.tvRunoutStatusMessage.text = String.format(
                Locale.US,
                "Reserves run dry %.1f hrs BEFORE the next BWD supply window. Estimated water shortfall: %.0f Liters. Reduce consumption immediately!",
                -result.safetyBufferHours,
                result.deficitLiters
            )
        } else {
            binding.cardRunoutStatus.setBackgroundResource(R.drawable.bg_card_safe)
            binding.tvRunoutStatusTitle.text = "SUFFICIENT RESERVE"
            binding.tvRunoutStatusTitle.setTextColor(getColor(R.color.emerald_500))
            binding.tvRunoutStatusMessage.text = String.format(
                Locale.US,
                "Your stored reserve will outlast the next BWD supply window with a surplus safety buffer of +%.1f hrs.",
                result.safetyBufferHours
            )
        }
    }

    // --- FEATURE 3: Crowdsourced Tap Flow & Pressure Verification ---
    private fun setupReportFeature() {
        binding.tvReportZoneName.text = selectedZone.name

        binding.btnSubmitReport.setOnClickListener {
            val selectedRadioId = binding.rgWaterStatus.checkedRadioButtonId
            if (selectedRadioId == -1) {
                Toast.makeText(this, "Please select the current water status at your tap.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val status = when (selectedRadioId) {
                R.id.rbStatusRunning -> TapFlowStatus.RUNNING
                R.id.rbStatusLowPressure -> TapFlowStatus.LOW_PRESSURE
                else -> TapFlowStatus.DRY
            }

            val notes = binding.etReportNotes.text.toString()
            val report = GroundTruthReport(
                id = "rep_${System.currentTimeMillis()}",
                zoneId = selectedZone.id,
                zoneName = selectedZone.name,
                status = status,
                notes = notes
            )

            // Feedback Toast & switch back to calendar view
            Toast.makeText(
                this,
                "Ground truth submitted for ${report.zoneName}: ${report.status.label}! Recalibrating distribution model...",
                Toast.LENGTH_LONG
            ).show()

            binding.etReportNotes.setText("")
            binding.rgWaterStatus.clearCheck()
            switchTab(Tab.CALENDAR)
        }
    }

    override fun onStart() {
        super.onStart()
        startLiveCountdown()
    }

    override fun onStop() {
        super.onStop()
        countdownTimer?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        countdownTimer?.cancel()
    }
}

