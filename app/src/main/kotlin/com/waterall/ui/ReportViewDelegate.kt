package com.waterall.ui

import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.waterall.R
import com.waterall.databinding.ActivityMainBinding
import com.waterall.domain.calculator.ZoneConsensusCalculator
import com.waterall.domain.model.GroundTruthReport
import com.waterall.domain.model.TapFlowStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Screen delegate handling Crowdsourced Tap Flow & Pressure Verification UI (Feature 3).
 *
 * Encapsulates report submission form validation, report instantiation satisfying
 * Law 4 validity invariants, in-memory repository dispatch, persistent report caching,
 * and immediate user feedback notifications.
 */
class ReportViewDelegate(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val viewModel: MainViewModel,
    private val userPreferences: UserPreferences,
    private val onReportSubmitted: (GroundTruthReport) -> Unit = {}
) : ViewDelegate {

    override fun setup() {
        binding.tvReportZoneName.text = viewModel.selectedZone.value.name

        binding.btnSubmitReport.setOnClickListener {
            val selectedRadioId = binding.rgWaterStatus.checkedRadioButtonId
            if (selectedRadioId == -1) {
                Toast.makeText(activity, "Select the water status at your tap.", Toast.LENGTH_SHORT).show()
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

            // Feed report into in-memory repository to close the feedback loop
            viewModel.reportRepository.addReport(report)
            val now = System.currentTimeMillis()
            val activeReports = viewModel.reportRepository.getAllReports().filter { activeReport ->
                (now - activeReport.timestamp) <= ZoneConsensusCalculator.REPORT_EXPIRATION_WINDOW_MS
            }
            userPreferences.saveReports(activeReports)

            // Immediate UI acknowledgment: Toast notification and consensus confirmation
            Toast.makeText(
                activity,
                "Report submitted for ${report.zoneName}",
                Toast.LENGTH_LONG
            ).show()

            binding.tvConsensusAcknowledgment.visibility = View.VISIBLE
            binding.tvConsensusAcknowledgment.text =
                "Report recorded: ${report.status.label}. Status refreshed."

            binding.etReportNotes.setText("")
            binding.rgWaterStatus.clearCheck()

            onReportSubmitted(report)
        }
    }

    override fun observeState(scope: CoroutineScope) {
        scope.launch {
            viewModel.selectedZone.collect { zone ->
                binding.tvReportZoneName.text = zone.name
            }
        }
    }

    override fun setVisible(visible: Boolean) {
        binding.viewReport.visibility = if (visible) View.VISIBLE else View.GONE
    }

    override fun onTabSelected() {
        binding.tvReportZoneName.text = viewModel.selectedZone.value.name
    }
}
