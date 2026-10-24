package com.waterall.domain.repository

import com.waterall.domain.calculator.ZoneConsensus
import com.waterall.domain.calculator.ZoneConsensusCalculator
import com.waterall.domain.model.GroundTruthReport
import com.waterall.domain.model.TapFlowStatus

/**
 * Lightweight in-memory implementation of ReportRepository.
 * Manages verified crowdsourced reports across zones and enforces Law 4 invariants.
 */
class InMemoryReportRepository(
    initialReports: List<GroundTruthReport> = emptyList()
) : ReportRepository {

    private val reports = mutableListOf<GroundTruthReport>()

    init {
        reports.addAll(initialReports.distinctBy { it.id })
    }

    /**
     * Adds a verified report to the repository.
     * Deduplicates by report ID before insertion so that repeated submissions or sample re-seeding
     * replace existing entries rather than duplicating records indefinitely.
     */
    override fun addReport(report: GroundTruthReport) {
        require(report.timestamp <= System.currentTimeMillis() + GroundTruthReport.CLOCK_SKEW_TOLERANCE_MS) {
            "Law 4 violation: Report timestamp cannot be in the future."
        }
        reports.removeAll { it.id == report.id }
        reports.add(0, report)
    }

    override fun getReportsForZone(zoneId: String): List<GroundTruthReport> {
        return reports.filter { it.zoneId == zoneId }
    }

    override fun getAllReports(): List<GroundTruthReport> {
        return reports.toList()
    }

    override fun getConsensus(
        zoneId: String,
        zoneName: String,
        currentTimeMillis: Long
    ): ZoneConsensus {
        // Gracefully filter out reports with expired or future-skewed timestamps
        // when device clock adjusts backward, matching UserPreferences.getReports
        val validReports = reports.filter { report ->
            report.timestamp in (currentTimeMillis - ZoneConsensusCalculator.REPORT_EXPIRATION_WINDOW_MS)..(currentTimeMillis + GroundTruthReport.CLOCK_SKEW_TOLERANCE_MS)
        }
        return ZoneConsensusCalculator.calculate(
            zoneId = zoneId,
            zoneName = zoneName,
            reports = validReports,
            currentTimeMillis = currentTimeMillis
        )
    }

    override fun clear() {
        reports.clear()
    }

    /**
     * Seeds the repository with realistic multi-report crowdsourced sample data.
     */
    fun seedSampleReports(nowMillis: Long = System.currentTimeMillis()) {
        val sampleReports = getSampleReports(nowMillis)
        sampleReports.forEach { addReport(it) }
    }

    companion object {
        /**
         * Creates an empty in-memory repository by default.
         * Reports are only populated when the user or peers submit verified reports.
         */
        fun createDefault(): InMemoryReportRepository {
            return InMemoryReportRepository()
        }

        /**
         * Creates a seeded in-memory repository populated with realistic sample reports across zones.
         * Useful for offline demonstrations and testing multi-report consensus algorithms.
         */
        fun createSeeded(nowMillis: Long = System.currentTimeMillis()): InMemoryReportRepository {
            return InMemoryReportRepository(getSampleReports(nowMillis))
        }

        /**
         * Returns a list of realistic sample reports across Baguio zones.
         */
        fun getSampleReports(nowMillis: Long = System.currentTimeMillis()): List<GroundTruthReport> {
            return listOf(
                GroundTruthReport(
                    id = "seed-z4-1",
                    zoneId = "brgy4",
                    zoneName = "Zone 4 — Brgy. San Isidro",
                    status = TapFlowStatus.RUNNING,
                    notes = "Strong flow at Purok 1",
                    timestamp = nowMillis - 45 * 60 * 1000L
                ),
                GroundTruthReport(
                    id = "seed-z4-2",
                    zoneId = "brgy4",
                    zoneName = "Zone 4 — Brgy. San Isidro",
                    status = TapFlowStatus.RUNNING,
                    notes = "Purok 2 tap running",
                    timestamp = nowMillis - 30 * 60 * 1000L
                ),
                GroundTruthReport(
                    id = "seed-z4-3",
                    zoneId = "brgy4",
                    zoneName = "Zone 4 — Brgy. San Isidro",
                    status = TapFlowStatus.RUNNING,
                    notes = "Good pressure for filling drums",
                    timestamp = nowMillis - 20 * 60 * 1000L
                ),
                GroundTruthReport(
                    id = "seed-z4-4",
                    zoneId = "brgy4",
                    zoneName = "Zone 4 — Brgy. San Isidro",
                    status = TapFlowStatus.LOW_PRESSURE,
                    notes = "Pressure dropped slightly at upper road",
                    timestamp = nowMillis - 12 * 60 * 1000L
                ),
                GroundTruthReport(
                    id = "seed-z1-1",
                    zoneId = "brgy1",
                    zoneName = "Zone 1 — Brgy. Cabinet Hill",
                    status = TapFlowStatus.LOW_PRESSURE,
                    notes = "Trickle flow observed",
                    timestamp = nowMillis - 25 * 60 * 1000L
                ),
                GroundTruthReport(
                    id = "seed-z1-2",
                    zoneId = "brgy1",
                    zoneName = "Zone 1 — Brgy. Cabinet Hill",
                    status = TapFlowStatus.LOW_PRESSURE,
                    notes = "Low pressure line",
                    timestamp = nowMillis - 15 * 60 * 1000L
                ),
                GroundTruthReport(
                    id = "seed-z7-1",
                    zoneId = "brgy7",
                    zoneName = "Zone 7 — Brgy. Trancoville",
                    status = TapFlowStatus.DRY,
                    notes = "No supply yet",
                    timestamp = nowMillis - 50 * 60 * 1000L
                ),
                GroundTruthReport(
                    id = "seed-z7-2",
                    zoneId = "brgy7",
                    zoneName = "Zone 7 — Brgy. Trancoville",
                    status = TapFlowStatus.DRY,
                    notes = "Main line dry",
                    timestamp = nowMillis - 10 * 60 * 1000L
                )
            )
        }
    }
}
