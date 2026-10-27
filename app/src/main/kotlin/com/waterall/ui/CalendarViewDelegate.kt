package com.waterall.ui

import android.os.CountDownTimer
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.waterall.R
import com.waterall.databinding.ActivityMainBinding
import com.waterall.databinding.ItemScheduleCardBinding
import com.waterall.domain.calculator.CountdownCalculationResult
import com.waterall.domain.calculator.ScheduleCountdownCalculator
import com.waterall.domain.calculator.ScheduleFlowState
import com.waterall.domain.calculator.ZoneConsensus
import com.waterall.domain.calculator.ZoneConsensusCalculator
import com.waterall.domain.model.BarangayZone
import com.waterall.domain.model.TapFlowStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.util.Locale

/**
 * Screen delegate handling Barangay Water Rotation Calendar & Countdown UI.
 *
 * Encapsulates zone selection, schedule card lists,
 * live countdown timers (satisfying Law 2 invariants and lifecycle leak prevention),
 * and community consensus card rendering.
 */
class CalendarViewDelegate(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val viewModel: MainViewModel,
    private val userPreferences: UserPreferences,
    private val zones: List<BarangayZone> = MainViewModel.DEFAULT_ZONES,
    private val onQuickReportClicked: () -> Unit = {},
    private val onScheduleChanged: () -> Unit = {}
) : ViewDelegate {

    private var countdownTimer: CountDownTimer? = null

    override fun setup() {
        setupZoneSelector()

        binding.btnQuickReport.setOnClickListener {
            onQuickReportClicked()
        }

        binding.btnLoadSampleReports.setOnClickListener {
            val now = System.currentTimeMillis()
            viewModel.loadSampleReports(now)
            val activeReports = viewModel.reportRepository.getAllReports().filter { activeReport ->
                (now - activeReport.timestamp) <= ZoneConsensusCalculator.REPORT_EXPIRATION_WINDOW_MS
            }
            userPreferences.saveReports(activeReports)
            Toast.makeText(
                activity,
                "Sample community reports loaded for live demo",
                Toast.LENGTH_SHORT
            ).show()
            updateConsensusCard()
        }
    }

    override fun observeState(scope: CoroutineScope) {
        scope.launch {
            viewModel.selectedZone.collect { zone ->
                val index = zones.indexOfFirst { it.id == zone.id }
                if (index >= 0 && binding.spinnerZones.selectedItemPosition != index) {
                    binding.spinnerZones.setSelection(index, false)
                }
                updateScheduleCards()
                startLiveCountdown()
                updateConsensusCard()
            }
        }
    }

    override fun setVisible(visible: Boolean) {
        binding.viewCalendar.visibility = if (visible) View.VISIBLE else View.GONE
    }

    override fun onTabSelected() {
        updateConsensusCard()
    }

    override fun cleanup() {
        stopCountdownTimer()
    }

    private fun setupZoneSelector() {
        val adapter = ArrayAdapter(
            activity,
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
                    onScheduleChanged()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun updateScheduleCards() {
        val schedules = viewModel.selectedZone.value.schedules
        binding.layoutScheduleContainer.removeAllViews()

        if (schedules.isEmpty()) {
            binding.tvNoScheduleMessage.visibility = View.VISIBLE
            return
        }
        binding.tvNoScheduleMessage.visibility = View.GONE

        schedules.forEachIndexed { index, schedule ->
            if (index > 0) {
                val divider = View(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        (1 * activity.resources.displayMetrics.density).toInt()
                    ).apply {
                        marginStart = (48 * activity.resources.displayMetrics.density).toInt()
                    }
                    setBackgroundColor(activity.getColor(R.color.hairline))
                }
                binding.layoutScheduleContainer.addView(divider)
            }

            val cardBinding = ItemScheduleCardBinding.inflate(activity.layoutInflater, binding.layoutScheduleContainer, false)
            cardBinding.tvScheduleDayBadge.text = schedule.dayOfWeek.name.take(1)
            cardBinding.tvScheduleDay.text = schedule.dateLabel
            cardBinding.tvScheduleTime.text = schedule.formattedRange
            binding.layoutScheduleContainer.addView(cardBinding.root)
        }
    }

    fun startLiveCountdown() {
        stopCountdownTimer()

        val calculation = ScheduleCountdownCalculator.calculate(
            zone = viewModel.selectedZone.value,
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

    fun stopCountdownTimer() {
        countdownTimer?.cancel()
        countdownTimer = null
    }

    private fun updateCountdownUi(result: CountdownCalculationResult) {
        binding.tvCountdownTimer.text = result.formattedCountdown

        when (result.flowState) {
            ScheduleFlowState.FLOW_ACTIVE -> {
                binding.tvFlowBadge.text = "Flow active"
                binding.tvFlowBadge.setTextColor(activity.getColor(R.color.status_ok))
                binding.flowPulseDot.setBackgroundResource(R.drawable.bg_dot_active)
                binding.tvCountdownSubtext.text = "Remaining supply window time"
            }
            ScheduleFlowState.RATIONING_ACTIVE -> {
                binding.tvFlowBadge.text = "Rationing"
                binding.tvFlowBadge.setTextColor(activity.getColor(R.color.status_warn))
                binding.flowPulseDot.setBackgroundResource(R.drawable.bg_dot_inactive)
                binding.tvCountdownSubtext.text = "Time until next supply window"
            }
        }
    }

    fun updateConsensusCard() {
        val currentZone = viewModel.selectedZone.value
        val consensus = try {
            viewModel.reportRepository.getConsensus(
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
            binding.tvConsensusBadge.setTextColor(activity.getColor(R.color.ink_muted))
            binding.tvConsensusTimestamp.text = "Today"
            binding.tvConsensusDetails.text = "Be the first to report for this zone."
        } else {
            val latest = consensus.latestReport
            val relativeTime = consensus.formattedRelativeTime ?: "Recently"
            binding.tvConsensusTimestamp.text = relativeTime

            val dominant = consensus.dominantStatus
            val badgeColor = when (dominant) {
                TapFlowStatus.RUNNING -> activity.getColor(R.color.status_ok)
                TapFlowStatus.LOW_PRESSURE -> activity.getColor(R.color.status_warn)
                TapFlowStatus.DRY -> activity.getColor(R.color.status_alert)
                null -> activity.getColor(R.color.ink_muted)
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
}
