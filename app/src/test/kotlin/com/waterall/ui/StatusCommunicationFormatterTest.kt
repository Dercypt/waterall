package com.waterall.ui

import com.waterall.domain.calculator.CountdownCalculationResult
import com.waterall.domain.calculator.ScheduleFlowState
import com.waterall.domain.calculator.ZoneConsensus
import com.waterall.domain.model.BarangayZone
import com.waterall.domain.model.GroundTruthReport
import com.waterall.domain.model.SupplyWindow
import com.waterall.domain.model.TapFlowStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

class StatusCommunicationFormatterTest {

    @Test
    fun testFormatShareZoneSummaryDuringActiveFlowWithConsensus() {
        val window = SupplyWindow(DayOfWeek.MONDAY, 6, 0, 10, 0)
        val zone = BarangayZone(
            id = "brgy4",
            name = "Zone 4 — Brgy. San Isidro",
            schedules = listOf(window)
        )
        val countdown = CountdownCalculationResult(
            flowState = ScheduleFlowState.FLOW_ACTIVE,
            remainingSeconds = 3661L,
            targetDateTime = LocalDateTime.of(2026, 11, 20, 10, 0),
            activeWindow = window,
            nextWindow = SupplyWindow(DayOfWeek.WEDNESDAY, 5, 0, 9, 0)
        )
        val consensus = ZoneConsensus(
            zoneId = "brgy4",
            zoneName = zone.name,
            totalReports = 2,
            statusCounts = mapOf(TapFlowStatus.RUNNING to 2),
            statusPercentages = mapOf(TapFlowStatus.RUNNING to 100),
            dominantStatus = TapFlowStatus.RUNNING,
            summaryText = "Zone 4: 2 verified reports today — 100% Running",
            latestReport = null,
            formattedRelativeTime = "Reported just now"
        )

        val formatted = StatusCommunicationFormatter.formatShareZoneSummary(zone, countdown, consensus)

        assertTrue(formatted.contains("BWD HydroTrack — Zone 4 — Brgy. San Isidro"))
        assertTrue(formatted.contains("Flow active (01:01:01 remaining)"))
        assertTrue(formatted.contains("Next window: Wed 5:00 AM – 9:00 AM"))
        assertTrue(formatted.contains("Community reports: Zone 4: 2 verified reports today — 100% Running"))
    }

    @Test
    fun testFormatShareZoneSummaryDuringRationingWithEmptySchedule() {
        val zone = BarangayZone(
            id = "empty_zone",
            name = "Zone 9 — Test Barangay",
            schedules = emptyList()
        )
        val countdown = CountdownCalculationResult(
            flowState = ScheduleFlowState.RATIONING_ACTIVE,
            remainingSeconds = 0L,
            targetDateTime = null,
            activeWindow = null,
            nextWindow = null
        )
        val consensus = ZoneConsensus(
            zoneId = "empty_zone",
            zoneName = zone.name,
            totalReports = 0,
            statusCounts = emptyMap(),
            statusPercentages = emptyMap(),
            dominantStatus = null,
            summaryText = "Zone 9: No verified reports today",
            latestReport = null,
            formattedRelativeTime = null
        )

        val formatted = StatusCommunicationFormatter.formatShareZoneSummary(zone, countdown, consensus)

        assertTrue(formatted.contains("Rationing (no scheduled windows)"))
        assertTrue(formatted.contains("Next window: Not scheduled"))
        assertTrue(formatted.contains("Zone 9: No verified reports today"))
    }

    @Test
    fun testFormatReportNotificationTitleAndBodyWithAndWithoutNotes() {
        val consensus = ZoneConsensus(
            zoneId = "brgy4",
            zoneName = "Zone 4 — Brgy. San Isidro",
            totalReports = 1,
            statusCounts = mapOf(TapFlowStatus.LOW_PRESSURE to 1),
            statusPercentages = mapOf(TapFlowStatus.LOW_PRESSURE to 100),
            dominantStatus = TapFlowStatus.LOW_PRESSURE,
            summaryText = "Zone 4: 1 verified report today — 100% Low Pressure",
            latestReport = null,
            formattedRelativeTime = "Reported just now"
        )

        val reportWithNote = GroundTruthReport(
            id = "rep_1",
            zoneId = "brgy4",
            zoneName = "Zone 4 — Brgy. San Isidro",
            status = TapFlowStatus.LOW_PRESSURE,
            notes = "  Trickle since 6:30 AM  ",
            timestamp = 1_700_000_000_000L
        )
        assertEquals(
            "Zone 4 · Low Pressure",
            StatusCommunicationFormatter.formatReportNotificationTitle(reportWithNote)
        )
        assertEquals(
            "Note: \"Trickle since 6:30 AM\" — Zone 4: 1 verified report today — 100% Low Pressure",
            StatusCommunicationFormatter.formatReportNotificationBody(reportWithNote, consensus)
        )

        val reportWithoutNote = reportWithNote.copy(notes = "   ")
        assertEquals(
            "Tap report recorded. Zone 4: 1 verified report today — 100% Low Pressure",
            StatusCommunicationFormatter.formatReportNotificationBody(reportWithoutNote, consensus)
        )
    }

    @Test
    fun testResolveTargetTabAndZoneEdgeCases() {
        assertEquals(Tab.CALENDAR, StatusCommunicationFormatter.resolveTargetTab("CALENDAR"))
        assertEquals(Tab.RESERVES, StatusCommunicationFormatter.resolveTargetTab(" reserves "))
        assertEquals(Tab.REPORT, StatusCommunicationFormatter.resolveTargetTab("Report"))
        assertNull(StatusCommunicationFormatter.resolveTargetTab(null))
        assertNull(StatusCommunicationFormatter.resolveTargetTab("   "))
        assertNull(StatusCommunicationFormatter.resolveTargetTab("UNKNOWN_TAB"))

        val zones = MainViewModel.DEFAULT_ZONES
        assertEquals(zones[1], StatusCommunicationFormatter.resolveTargetZone("brgy1", zones))
        assertEquals(zones[2], StatusCommunicationFormatter.resolveTargetZone(" brgy7 ", zones))
        assertNull(StatusCommunicationFormatter.resolveTargetZone(null, zones))
        assertNull(StatusCommunicationFormatter.resolveTargetZone("  ", zones))
        assertNull(StatusCommunicationFormatter.resolveTargetZone("missing", zones))
    }
}
