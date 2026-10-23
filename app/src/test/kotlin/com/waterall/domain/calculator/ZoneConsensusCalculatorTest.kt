package com.waterall.domain.calculator

import com.waterall.domain.model.GroundTruthReport
import com.waterall.domain.model.TapFlowStatus
import com.waterall.domain.repository.InMemoryReportRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoneConsensusCalculatorTest {

    private val baseTime = 1_700_000_000_000L

    @Test
    fun testEmptyReportsProducesDefaultConsensus() {
        val consensus = ZoneConsensusCalculator.calculate(
            zoneId = "brgy4",
            zoneName = "Zone 4 — Brgy. San Isidro",
            reports = emptyList(),
            currentTimeMillis = baseTime
        )

        assertEquals("brgy4", consensus.zoneId)
        assertEquals(0, consensus.totalReports)
        assertNull(consensus.dominantStatus)
        assertNull(consensus.latestReport)
        assertNull(consensus.formattedRelativeTime)
        assertEquals("Zone 4: No verified reports today", consensus.summaryText)
        assertTrue(consensus.statusPercentages.isEmpty())
        assertTrue(consensus.statusCounts.isEmpty())
    }

    @Test
    fun testSingleReportFormattingAndSingularForm() {
        val single = listOf(
            GroundTruthReport(
                id = "r-single",
                zoneId = "brgy4",
                zoneName = "Zone 4",
                status = TapFlowStatus.RUNNING,
                timestamp = baseTime - 5 * 60 * 1000L
            )
        )

        val consensus = ZoneConsensusCalculator.calculate(
            zoneId = "brgy4",
            zoneName = "Zone 4 — Brgy. San Isidro",
            reports = single,
            currentTimeMillis = baseTime
        )

        assertEquals(1, consensus.totalReports)
        assertEquals(TapFlowStatus.RUNNING, consensus.dominantStatus)
        assertEquals(100, consensus.statusPercentages[TapFlowStatus.RUNNING])
        assertEquals("Reported 5m ago", consensus.formattedRelativeTime)
        assertEquals("Zone 4: 1 verified report today — 100% Running", consensus.summaryText)
    }

    @Test
    fun testZone4ConsensusExactPromptExample() {
        // Zone 4: 4 verified reports today — 75% Running, 25% Low Pressure
        val reports = listOf(
            GroundTruthReport("r1", "brgy4", "Zone 4", TapFlowStatus.RUNNING, timestamp = baseTime - 45 * 60 * 1000L),
            GroundTruthReport("r2", "brgy4", "Zone 4", TapFlowStatus.RUNNING, timestamp = baseTime - 30 * 60 * 1000L),
            GroundTruthReport("r3", "brgy4", "Zone 4", TapFlowStatus.RUNNING, timestamp = baseTime - 20 * 60 * 1000L),
            GroundTruthReport("r4", "brgy4", "Zone 4", TapFlowStatus.LOW_PRESSURE, timestamp = baseTime - 12 * 60 * 1000L)
        )

        val consensus = ZoneConsensusCalculator.calculate(
            zoneId = "brgy4",
            zoneName = "Zone 4 — Brgy. San Isidro",
            reports = reports,
            currentTimeMillis = baseTime
        )

        assertEquals(4, consensus.totalReports)
        assertEquals(TapFlowStatus.RUNNING, consensus.dominantStatus)
        assertEquals(75, consensus.statusPercentages[TapFlowStatus.RUNNING])
        assertEquals(25, consensus.statusPercentages[TapFlowStatus.LOW_PRESSURE])
        assertEquals("Reported 12m ago", consensus.formattedRelativeTime)
        assertEquals("Zone 4: 4 verified reports today — 75% Running, 25% Low Pressure", consensus.summaryText)
    }

    @Test
    fun testAllThreeStatesPercentagesSumTo100() {
        val reports = listOf(
            GroundTruthReport("r1", "brgy7", "Zone 7", TapFlowStatus.RUNNING, timestamp = baseTime - 50 * 60 * 1000L),
            GroundTruthReport("r2", "brgy7", "Zone 7", TapFlowStatus.LOW_PRESSURE, timestamp = baseTime - 30 * 60 * 1000L),
            GroundTruthReport("r3", "brgy7", "Zone 7", TapFlowStatus.DRY, timestamp = baseTime - 10 * 60 * 1000L)
        )

        val consensus = ZoneConsensusCalculator.calculate(
            zoneId = "brgy7",
            zoneName = "Zone 7 — Brgy. Trancoville",
            reports = reports,
            currentTimeMillis = baseTime
        )

        assertEquals(3, consensus.totalReports)
        val sumOfPercentages = consensus.statusPercentages.values.sum()
        assertEquals(100, sumOfPercentages)
        assertEquals("Reported 10m ago", consensus.formattedRelativeTime)
    }

    @Test
    fun testZoneIntegrityFiltersOutOtherZones() {
        val mixedReports = listOf(
            GroundTruthReport("r1", "brgy4", "Zone 4", TapFlowStatus.RUNNING, timestamp = baseTime - 20 * 60 * 1000L),
            GroundTruthReport("r2", "brgy1", "Zone 1", TapFlowStatus.DRY, timestamp = baseTime - 10 * 60 * 1000L),
            GroundTruthReport("r3", "brgy1", "Zone 1", TapFlowStatus.DRY, timestamp = baseTime - 5 * 60 * 1000L)
        )

        val z4Consensus = ZoneConsensusCalculator.calculate("brgy4", "Zone 4", mixedReports, baseTime)
        assertEquals(1, z4Consensus.totalReports)
        assertEquals(TapFlowStatus.RUNNING, z4Consensus.dominantStatus)

        val z1Consensus = ZoneConsensusCalculator.calculate("brgy1", "Zone 1", mixedReports, baseTime)
        assertEquals(2, z1Consensus.totalReports)
        assertEquals(TapFlowStatus.DRY, z1Consensus.dominantStatus)
    }

    @Test
    fun testRelativeTimestampFormattingScales() {
        assertEquals("Reported just now", ZoneConsensusCalculator.formatRelativeTime(baseTime, baseTime))
        assertEquals("Reported just now", ZoneConsensusCalculator.formatRelativeTime(baseTime - 45_000L, baseTime))
        assertEquals("Reported 1m ago", ZoneConsensusCalculator.formatRelativeTime(baseTime - 60_000L, baseTime))
        assertEquals("Reported 12m ago", ZoneConsensusCalculator.formatRelativeTime(baseTime - 12 * 60 * 1000L, baseTime))
        assertEquals("Reported 59m ago", ZoneConsensusCalculator.formatRelativeTime(baseTime - 59 * 60 * 1000L, baseTime))
        assertEquals("Reported 1h ago", ZoneConsensusCalculator.formatRelativeTime(baseTime - 60 * 60 * 1000L, baseTime))
        assertEquals("Reported 3h ago", ZoneConsensusCalculator.formatRelativeTime(baseTime - 3 * 3600 * 1000L, baseTime))
        assertEquals("Reported 1d ago", ZoneConsensusCalculator.formatRelativeTime(baseTime - 24 * 3600 * 1000L, baseTime))
        assertEquals("Reported 4d ago", ZoneConsensusCalculator.formatRelativeTime(baseTime - 4 * 24 * 3600 * 1000L, baseTime))
    }

    @Test
    fun testLaw4FutureTimestampRejected() {
        var caught = false
        try {
            ZoneConsensusCalculator.formatRelativeTime(baseTime + 10_000L, baseTime)
        } catch (e: IllegalArgumentException) {
            caught = true
            assertTrue(e.message?.contains("Law 4 violation") == true)
        }
        assertTrue("Future timestamp must throw Law 4 violation", caught)
    }

    @Test
    fun testClockSkewToleranceSafelyFormatsJustNowAndRejectsExceedingThreshold() {
        // Skewed within 1,000ms tolerance: safely format as "Reported just now"
        assertEquals("Reported just now", ZoneConsensusCalculator.formatRelativeTime(baseTime + 500L, baseTime))
        assertEquals("Reported just now", ZoneConsensusCalculator.formatRelativeTime(baseTime + 1000L, baseTime))

        // Exceeding 1,000ms skew: must throw Law 4 violation
        var caught = false
        try {
            ZoneConsensusCalculator.formatRelativeTime(baseTime + 1001L, baseTime)
        } catch (e: IllegalArgumentException) {
            caught = true
            assertTrue(e.message?.contains("Law 4 violation") == true)
        }
        assertTrue("Timestamp exceeding 1000ms tolerance must throw Law 4 violation", caught)

        // Zone consensus calculation with millisecond clock skew report
        val skewedReports = listOf(
            GroundTruthReport(
                id = "r-skewed",
                zoneId = "brgy4",
                zoneName = "Zone 4",
                status = TapFlowStatus.RUNNING,
                timestamp = baseTime + 800L
            )
        )
        val consensus = ZoneConsensusCalculator.calculate(
            zoneId = "brgy4",
            zoneName = "Zone 4 — Brgy. San Isidro",
            reports = skewedReports,
            currentTimeMillis = baseTime
        )
        assertEquals(1, consensus.totalReports)
        assertEquals("Reported just now", consensus.formattedRelativeTime)

        // Zone consensus calculation with timestamp exceeding 1000ms tolerance must throw
        val excessiveFutureReports = listOf(
            GroundTruthReport(
                id = "r-future-excessive",
                zoneId = "brgy4",
                zoneName = "Zone 4",
                status = TapFlowStatus.RUNNING,
                timestamp = baseTime + 1001L
            )
        )
        var caughtConsensus = false
        try {
            ZoneConsensusCalculator.calculate(
                zoneId = "brgy4",
                zoneName = "Zone 4",
                reports = excessiveFutureReports,
                currentTimeMillis = baseTime
            )
        } catch (e: IllegalArgumentException) {
            caughtConsensus = true
            assertTrue(e.message?.contains("Law 4 violation") == true)
        }
        assertTrue("Consensus with report exceeding clock skew tolerance must throw", caughtConsensus)
    }

    @Test
    fun testInMemoryReportRepositoryDefaultIsEmpty() {
        val repo = InMemoryReportRepository.createDefault()
        assertEquals(0, repo.getAllReports().size)
        val consensus = repo.getConsensus("brgy4", "Zone 4", baseTime)
        assertEquals(0, consensus.totalReports)
        assertNull(consensus.latestReport)
        assertEquals("Zone 4: No verified reports today", consensus.summaryText)
    }

    @Test
    fun testInMemoryReportRepositoryDefaultSeedAndAdd() {
        val repo = InMemoryReportRepository.createSeeded(baseTime)

        // Seeded repo has 4 reports for Zone 4
        val z4Consensus = repo.getConsensus("brgy4", "Zone 4 — Brgy. San Isidro", baseTime)
        assertEquals(4, z4Consensus.totalReports)
        assertEquals("Zone 4: 4 verified reports today — 75% Running, 25% Low Pressure", z4Consensus.summaryText)
        assertEquals("Reported 12m ago", z4Consensus.formattedRelativeTime)

        // Resident reports Dry
        val newReport = GroundTruthReport(
            id = "resident-01",
            zoneId = "brgy4",
            zoneName = "Zone 4 — Brgy. San Isidro",
            status = TapFlowStatus.DRY,
            timestamp = baseTime
        )
        repo.addReport(newReport)

        // Updated consensus immediately reflects the submission: 5 reports, 60% Running, 20% Low Pressure, 20% Dry
        val refreshed = repo.getConsensus("brgy4", "Zone 4 — Brgy. San Isidro", baseTime)
        assertEquals(5, refreshed.totalReports)
        assertEquals(60, refreshed.statusPercentages[TapFlowStatus.RUNNING])
        assertEquals(20, refreshed.statusPercentages[TapFlowStatus.LOW_PRESSURE])
        assertEquals(20, refreshed.statusPercentages[TapFlowStatus.DRY])
        assertEquals("Reported just now", refreshed.formattedRelativeTime)
        assertEquals("Zone 4: 5 verified reports today — 60% Running, 20% Low Pressure, 20% Dry", refreshed.summaryText)
    }

    @Test
    fun testInMemoryReportRepositorySkipsFutureSkewedReportsWhenClockShiftsBackward() {
        val pastReport = GroundTruthReport(
            id = "rep-valid",
            zoneId = "brgy4",
            zoneName = "Zone 4",
            status = TapFlowStatus.RUNNING,
            timestamp = baseTime - 10_000L
        )
        val futureSkewedReport = GroundTruthReport(
            id = "rep-future",
            zoneId = "brgy4",
            zoneName = "Zone 4",
            status = TapFlowStatus.DRY,
            timestamp = baseTime + 50_000L
        )
        val repo = InMemoryReportRepository(listOf(pastReport, futureSkewedReport))

        // Device clock shifted backward to baseTime, making rep-future appear 50s in the future
        val consensus = repo.getConsensus("brgy4", "Zone 4", baseTime)

        assertEquals(1, consensus.totalReports)
        assertEquals(TapFlowStatus.RUNNING, consensus.dominantStatus)
        assertEquals(100, consensus.statusPercentages[TapFlowStatus.RUNNING])
        assertEquals("Reported just now", consensus.formattedRelativeTime)
    }

    @Test
    fun testInMemoryReportRepositoryAllFutureSkewedReportsReturnsEmptyConsensus() {
        val futureReport = GroundTruthReport(
            id = "rep-future-only",
            zoneId = "brgy4",
            zoneName = "Zone 4",
            status = TapFlowStatus.RUNNING,
            timestamp = baseTime + 100_000L
        )
        val repo = InMemoryReportRepository(listOf(futureReport))

        val consensus = repo.getConsensus("brgy4", "Zone 4", baseTime)
        assertEquals(0, consensus.totalReports)
        assertNull(consensus.dominantStatus)
        assertNull(consensus.latestReport)
        assertNull(consensus.formattedRelativeTime)
        assertEquals("Zone 4: No verified reports today", consensus.summaryText)
    }

    @Test
    fun testInMemoryReportRepositoryMixedMultiZoneAndClockSkewReports() {
        val repo = InMemoryReportRepository(
            listOf(
                GroundTruthReport("r1", "brgy4", "Zone 4", TapFlowStatus.RUNNING, timestamp = baseTime - 20_000L),
                GroundTruthReport("r2", "brgy4", "Zone 4", TapFlowStatus.DRY, timestamp = baseTime + 60_000L),
                GroundTruthReport("r3", "brgy1", "Zone 1", TapFlowStatus.LOW_PRESSURE, timestamp = baseTime - 10_000L),
                GroundTruthReport("r4", "brgy1", "Zone 1", TapFlowStatus.RUNNING, timestamp = baseTime + 30_000L)
            )
        )

        val consensus = repo.getConsensus("brgy4", "Zone 4", baseTime)
        assertEquals(1, consensus.totalReports)
        assertEquals(TapFlowStatus.RUNNING, consensus.dominantStatus)
        assertEquals("Reported just now", consensus.formattedRelativeTime)
    }

    @Test
    fun testStaleReportsOlderThan24HoursAreExcludedFromConsensus() {
        val mixedReports = listOf(
            // Stale report 5 days ago
            GroundTruthReport(
                id = "stale-5d",
                zoneId = "brgy4",
                zoneName = "Zone 4",
                status = TapFlowStatus.DRY,
                timestamp = baseTime - 5 * 24 * 60 * 60 * 1000L
            ),
            // Stale report 25 hours ago (exceeding 24h expiration)
            GroundTruthReport(
                id = "stale-25h",
                zoneId = "brgy4",
                zoneName = "Zone 4",
                status = TapFlowStatus.LOW_PRESSURE,
                timestamp = baseTime - 25 * 60 * 60 * 1000L
            ),
            // Fresh report exactly 24 hours ago (boundary)
            GroundTruthReport(
                id = "fresh-24h",
                zoneId = "brgy4",
                zoneName = "Zone 4",
                status = TapFlowStatus.RUNNING,
                timestamp = baseTime - 24 * 60 * 60 * 1000L
            ),
            // Fresh report 2 hours ago
            GroundTruthReport(
                id = "fresh-2h",
                zoneId = "brgy4",
                zoneName = "Zone 4",
                status = TapFlowStatus.RUNNING,
                timestamp = baseTime - 2 * 60 * 60 * 1000L
            )
        )

        val consensus = ZoneConsensusCalculator.calculate(
            zoneId = "brgy4",
            zoneName = "Zone 4 — Brgy. San Isidro",
            reports = mixedReports,
            currentTimeMillis = baseTime
        )

        // Only the 2 fresh reports within 24h should be counted; stale reports 5d and 25h ago must not skew active consensus
        assertEquals(2, consensus.totalReports)
        assertEquals(TapFlowStatus.RUNNING, consensus.dominantStatus)
        assertEquals(100, consensus.statusPercentages[TapFlowStatus.RUNNING])
        assertEquals("Reported 2h ago", consensus.formattedRelativeTime)
        assertEquals("Zone 4: 2 verified reports today — 100% Running", consensus.summaryText)
    }

    @Test
    fun testAllStaleReportsProducesEmptyConsensus() {
        val staleReports = listOf(
            GroundTruthReport(
                id = "stale-past-rotation",
                zoneId = "brgy4",
                zoneName = "Zone 4",
                status = TapFlowStatus.RUNNING,
                timestamp = baseTime - 48 * 60 * 60 * 1000L
            )
        )

        val consensus = ZoneConsensusCalculator.calculate(
            zoneId = "brgy4",
            zoneName = "Zone 4 — Brgy. San Isidro",
            reports = staleReports,
            currentTimeMillis = baseTime
        )

        assertEquals(0, consensus.totalReports)
        assertNull(consensus.dominantStatus)
        assertNull(consensus.latestReport)
        assertNull(consensus.formattedRelativeTime)
        assertEquals("Zone 4: No verified reports today", consensus.summaryText)
        assertTrue(consensus.statusPercentages.isEmpty())
    }

    companion object {
        fun createSeededRepository(nowMillis: Long): InMemoryReportRepository {
            val seedReports = listOf(
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
            return InMemoryReportRepository(seedReports)
        }
    }
}

private fun InMemoryReportRepository.Companion.createSeeded(nowMillis: Long = System.currentTimeMillis()): InMemoryReportRepository =
    ZoneConsensusCalculatorTest.createSeededRepository(nowMillis)


