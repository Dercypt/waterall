package com.waterall.ui

import com.waterall.domain.calculator.CountdownCalculationResult
import com.waterall.domain.calculator.ScheduleFlowState
import com.waterall.domain.calculator.ZoneConsensus
import com.waterall.domain.model.BarangayZone
import com.waterall.domain.model.GroundTruthReport

/**
 * Pure formatting and intent-routing helper for user communication features
 * (shareable zone summaries, local notification copy, and deep-link intent extras).
 *
 * Kept free of Android framework dependencies so all copy and edge cases
 * can be verified in pure JVM unit tests.
 */
object StatusCommunicationFormatter {

    const val EXTRA_TARGET_TAB = "com.waterall.extra.TARGET_TAB"
    const val EXTRA_ZONE_ID = "com.waterall.extra.ZONE_ID"

    /**
     * Formats a concise, plain-text summary of the selected [zone]'s current rotation
     * countdown and community consensus for implicit sharing (`Intent.ACTION_SEND`).
     */
    fun formatShareZoneSummary(
        zone: BarangayZone,
        countdown: CountdownCalculationResult,
        consensus: ZoneConsensus
    ): String {
        val statusLine = when (countdown.flowState) {
            ScheduleFlowState.FLOW_ACTIVE ->
                "Flow active (${countdown.formattedCountdown} remaining)"
            ScheduleFlowState.RATIONING_ACTIVE -> if (countdown.nextWindow != null) {
                "Rationing (next window in ${countdown.formattedCountdown})"
            } else {
                "Rationing (no scheduled windows)"
            }
        }
        val nextScheduleLine = countdown.nextWindow?.let {
            "Next window: ${it.dateLabel} ${it.formattedRange}"
        } ?: "Next window: Not scheduled"

        return buildString {
            appendLine("BWD HydroTrack — ${zone.name}")
            appendLine("Status: $statusLine")
            appendLine(nextScheduleLine)
            append("Community reports: ${consensus.summaryText}")
        }.trim()
    }

    /**
     * Formats the notification title when a tap status [report] is recorded.
     */
    fun formatReportNotificationTitle(report: GroundTruthReport): String {
        val shortZoneName = report.zoneName
            .substringBefore("—")
            .substringBefore("-")
            .trim()
            .ifBlank { report.zoneName }
        return "$shortZoneName · ${report.status.label}"
    }

    /**
     * Formats the notification body copy combining the submitted [report] note (if any)
     * and the refreshed [consensus] summary.
     */
    fun formatReportNotificationBody(
        report: GroundTruthReport,
        consensus: ZoneConsensus
    ): String {
        val trimmedNote = report.notes.trim()
        return if (trimmedNote.isNotEmpty()) {
            "Note: \"$trimmedNote\" — ${consensus.summaryText}"
        } else {
            "Tap report recorded. ${consensus.summaryText}"
        }
    }

    /**
     * Safely resolves a raw extra string to a [Tab], returning null for null, blank, or unknown values.
     */
    fun resolveTargetTab(rawTabName: String?): Tab? {
        val normalized = rawTabName?.trim()?.uppercase()
        if (normalized.isNullOrEmpty()) return null
        return Tab.values().firstOrNull { it.name == normalized }
    }

    /**
     * Finds the zone matching [zoneId] in [zones], returning null if missing or blank.
     */
    fun resolveTargetZone(zoneId: String?, zones: List<BarangayZone>): BarangayZone? {
        val normalized = zoneId?.trim()
        if (normalized.isNullOrEmpty()) return null
        return zones.firstOrNull { it.id == normalized }
    }
}
