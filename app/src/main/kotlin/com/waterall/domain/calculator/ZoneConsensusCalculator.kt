package com.waterall.domain.calculator

import com.waterall.domain.model.GroundTruthReport
import com.waterall.domain.model.TapFlowStatus

/**
 * Encapsulates aggregated crowdsourced consensus for a specific Barangay zone.
 */
data class ZoneConsensus(
    val zoneId: String,
    val zoneName: String,
    val totalReports: Int,
    val statusCounts: Map<TapFlowStatus, Int>,
    val statusPercentages: Map<TapFlowStatus, Int>,
    val dominantStatus: TapFlowStatus?,
    val summaryText: String,
    val latestReport: GroundTruthReport?,
    val formattedRelativeTime: String?
)

/**
 * Pure domain calculator aggregating crowdsourced ground truth reports for a zone
 * and enforcing Law 4 invariants (closed enum states, non-future timestamps, zone integrity).
 */
object ZoneConsensusCalculator {

    const val REPORT_EXPIRATION_WINDOW_MS = 24 * 60 * 60 * 1000L

    fun formatRelativeTime(timestamp: Long, now: Long = System.currentTimeMillis()): String =
        GroundTruthReport.formatRelativeTimestamp(timestamp, now)

    fun calculate(
        zoneId: String,
        zoneName: String,
        reports: List<GroundTruthReport>,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): ZoneConsensus {
        // Enforce Law 4 temporal integrity on all provided reports
        reports.forEach { report ->
            require(report.timestamp <= currentTimeMillis + GroundTruthReport.CLOCK_SKEW_TOLERANCE_MS) {
                "Law 4 violation: Report timestamp cannot be in the future (timestamp=${report.timestamp}, now=$currentTimeMillis)."
            }
            require(report.zoneId.isNotBlank()) {
                "Law 4 violation: Zone ID must not be blank."
            }
        }

        // Filter reports specifically for this zone (Law 4: Zone Integrity) and within 24-hour expiration window
        val zoneReports = reports.filter {
            it.zoneId == zoneId && (currentTimeMillis - it.timestamp) <= REPORT_EXPIRATION_WINDOW_MS
        }
        val totalReports = zoneReports.size

        val shortZoneName = zoneName.substringBefore("—").substringBefore("-").trim().ifBlank { zoneName }

        if (totalReports == 0) {
            return ZoneConsensus(
                zoneId = zoneId,
                zoneName = zoneName,
                totalReports = 0,
                statusCounts = emptyMap(),
                statusPercentages = emptyMap(),
                dominantStatus = null,
                summaryText = "$shortZoneName: No verified reports today",
                latestReport = null,
                formattedRelativeTime = null
            )
        }

        val counts = mutableMapOf<TapFlowStatus, Int>()
        for (status in TapFlowStatus.values()) {
            counts[status] = 0
        }
        for (report in zoneReports) {
            counts[report.status] = (counts[report.status] ?: 0) + 1
        }

        val activeCounts = counts.filter { it.value > 0 }
        val percentages = calculatePercentages(activeCounts, totalReports)

        // Sort statuses by count descending, then by enum ordinal priority
        val sortedActive = activeCounts.entries.sortedWith(
            compareByDescending<Map.Entry<TapFlowStatus, Int>> { it.value }.thenBy { it.key.ordinal }
        )

        val dominantStatus = sortedActive.firstOrNull()?.key

        val breakdownText = sortedActive.joinToString(", ") { entry ->
            val pct = percentages[entry.key] ?: 0
            "$pct% ${entry.key.consensusLabel()}"
        }

        val reportPlural = if (totalReports == 1) "report" else "reports"
        val summaryText = "$shortZoneName: $totalReports verified $reportPlural today — $breakdownText"

        val latestReport = zoneReports.maxByOrNull { it.timestamp }
        val formattedTime = latestReport?.let { formatRelativeTime(it.timestamp, currentTimeMillis) }

        return ZoneConsensus(
            zoneId = zoneId,
            zoneName = zoneName,
            totalReports = totalReports,
            statusCounts = activeCounts,
            statusPercentages = percentages,
            dominantStatus = dominantStatus,
            summaryText = summaryText,
            latestReport = latestReport,
            formattedRelativeTime = formattedTime
        )
    }

    private fun calculatePercentages(
        activeCounts: Map<TapFlowStatus, Int>,
        total: Int
    ): Map<TapFlowStatus, Int> {
        if (total == 0 || activeCounts.isEmpty()) return emptyMap()

        // Largest Remainder Method (Hamilton/Hare) guarantees discrete percentages sum to exactly 100%
        val rawMap = activeCounts.mapValues { (_, count) -> (count * 100.0) / total }
        val floorMap = rawMap.mapValues { (_, raw) -> raw.toInt() }.toMutableMap()
        val remainderMap = rawMap.mapValues { (k, raw) -> raw - floorMap.getValue(k) }

        var remainderSum = 100 - floorMap.values.sum()
        val sortedRemainders = remainderMap.entries.sortedByDescending { it.value }
        for (entry in sortedRemainders) {
            if (remainderSum <= 0) break
            floorMap[entry.key] = floorMap.getValue(entry.key) + 1
            remainderSum--
        }

        return floorMap
    }

    private fun TapFlowStatus.consensusLabel(): String = when (this) {
        TapFlowStatus.RUNNING -> "Running"
        TapFlowStatus.LOW_PRESSURE -> "Low Pressure"
        TapFlowStatus.DRY -> "Dry"
    }
}
