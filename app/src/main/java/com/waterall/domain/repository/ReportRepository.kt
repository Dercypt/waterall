package com.waterall.domain.repository

import com.waterall.domain.calculator.ZoneConsensus
import com.waterall.domain.model.GroundTruthReport

/**
 * Repository abstraction for storing and retrieving crowdsourced tap flow reports.
 */
interface ReportRepository {
    fun addReport(report: GroundTruthReport)
    fun getReportsForZone(zoneId: String): List<GroundTruthReport>
    fun getAllReports(): List<GroundTruthReport>
    fun getConsensus(zoneId: String, zoneName: String, currentTimeMillis: Long = System.currentTimeMillis()): ZoneConsensus
    fun clear()
}
