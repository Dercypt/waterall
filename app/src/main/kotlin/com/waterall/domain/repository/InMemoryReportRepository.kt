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
        reports.addAll(initialReports)
    }

    override fun addReport(report: GroundTruthReport) {
        require(report.timestamp <= System.currentTimeMillis() + GroundTruthReport.CLOCK_SKEW_TOLERANCE_MS) {
            "Law 4 violation: Report timestamp cannot be in the future."
        }
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
        return ZoneConsensusCalculator.calculate(
            zoneId = zoneId,
            zoneName = zoneName,
            reports = reports,
            currentTimeMillis = currentTimeMillis
        )
    }

    override fun clear() {
        reports.clear()
    }

    companion object {
        /**
         * Creates an empty in-memory repository by default.
         * Reports are only populated when the user or peers submit verified reports.
         */
        fun createDefault(): InMemoryReportRepository {
            return InMemoryReportRepository()
        }
    }
}
