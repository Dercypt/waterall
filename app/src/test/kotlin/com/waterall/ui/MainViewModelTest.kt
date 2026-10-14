package com.waterall.ui

import com.waterall.domain.model.GroundTruthReport
import com.waterall.domain.model.TapFlowStatus
import com.waterall.domain.model.UserMode
import com.waterall.domain.repository.InMemoryReportRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test verifying [MainViewModel] preserves [com.waterall.domain.repository.ReportRepository]
 * and UI state (selectedZone, selectedFillPercentage, dailyConsumption, userMode) across ViewModel lifecycle
 * and configuration changes.
 */
class MainViewModelTest {

    @Test
    fun testDefaultReportRepositoryIsInstantiatedAndInitiallyEmpty() {
        val viewModel = MainViewModel()
        assertNotNull(viewModel.reportRepository)

        // When first opening the app, no reports exist until a report is submitted
        val allReports = viewModel.reportRepository.getAllReports()
        assertTrue(allReports.isEmpty())
    }

    @Test
    fun testReportRepositoryRetainsSubmissions() {
        val viewModel = MainViewModel()
        val initialCount = viewModel.reportRepository.getAllReports().size
        assertEquals(0, initialCount)

        val report = GroundTruthReport(
            id = "test-session-report-1",
            zoneId = "brgy4",
            zoneName = "Zone 4 — Brgy. San Isidro",
            status = TapFlowStatus.RUNNING,
            notes = "Flow active during rotation test",
            timestamp = System.currentTimeMillis()
        )

        viewModel.reportRepository.addReport(report)

        val updatedReports = viewModel.reportRepository.getAllReports()
        assertEquals(1, updatedReports.size)
        assertEquals(report, updatedReports.first())
    }

    @Test
    fun testCustomRepositoryInjection() {
        val customRepo = InMemoryReportRepository()
        val viewModel = MainViewModel(customRepo)

        assertEquals(0, viewModel.reportRepository.getAllReports().size)

        val report = GroundTruthReport(
            id = "custom-report",
            zoneId = "brgy1",
            zoneName = "Zone 1",
            status = TapFlowStatus.DRY,
            timestamp = System.currentTimeMillis()
        )
        viewModel.reportRepository.addReport(report)

        assertEquals(1, viewModel.reportRepository.getAllReports().size)
    }

    @Test
    fun testFactoryCreatesMainViewModelWithEmptyRepository() {
        val created = MainViewModel.Factory.create(MainViewModel::class.java)
        assertNotNull(created)
        assertTrue(created.reportRepository.getAllReports().isEmpty())
    }

    @Test
    fun testInitialUiStateDefaults() {
        val viewModel = MainViewModel()
        assertEquals(MainViewModel.DEFAULT_ZONES[0], viewModel.selectedZone.value)
        assertEquals(0.0, viewModel.selectedFillPercentage.value, 0.001)
        assertEquals(0.0, viewModel.dailyConsumption.value, 0.001)
        assertEquals(UserMode.DOMESTIC, viewModel.userMode.value)
    }

    @Test
    fun testSetSelectedZoneUpdatesStateFlow() {
        val viewModel = MainViewModel()
        val targetZone = MainViewModel.DEFAULT_ZONES[1]
        viewModel.setSelectedZone(targetZone)

        assertEquals(targetZone, viewModel.selectedZone.value)
    }

    @Test
    fun testSetSelectedFillPercentageUpdatesStateFlow() {
        val viewModel = MainViewModel()
        viewModel.setSelectedFillPercentage(50.0)
        assertEquals(50.0, viewModel.selectedFillPercentage.value, 0.001)

        // Test clamping bounds
        viewModel.setSelectedFillPercentage(120.0)
        assertEquals(100.0, viewModel.selectedFillPercentage.value, 0.001)

        viewModel.setSelectedFillPercentage(-10.0)
        assertEquals(0.0, viewModel.selectedFillPercentage.value, 0.001)
    }

    @Test
    fun testSetDailyConsumptionUpdatesStateFlow() {
        val viewModel = MainViewModel()
        viewModel.setDailyConsumption(75.5)
        assertEquals(75.5, viewModel.dailyConsumption.value, 0.001)

        // Coerce negative consumption to 0.0
        viewModel.setDailyConsumption(-25.0)
        assertEquals(0.0, viewModel.dailyConsumption.value, 0.001)
    }

    @Test
    fun testSetUserModeUpdatesStateFlow() {
        val viewModel = MainViewModel()
        assertEquals(UserMode.DOMESTIC, viewModel.userMode.value)

        viewModel.setUserMode(UserMode.COMMERCIAL)
        assertEquals(UserMode.COMMERCIAL, viewModel.userMode.value)

        viewModel.setUserMode(UserMode.DOMESTIC)
        assertEquals(UserMode.DOMESTIC, viewModel.userMode.value)
    }

    @Test
    fun testUiStatePreservationAcrossSimulatedConfigurationChange() {
        val viewModel = MainViewModel()
        val selectedZone = MainViewModel.DEFAULT_ZONES[2]
        viewModel.setSelectedZone(selectedZone)
        viewModel.setSelectedFillPercentage(75.0)
        viewModel.setDailyConsumption(120.0)
        viewModel.setUserMode(UserMode.COMMERCIAL)

        // Simulate activity recreation retrieving the existing ViewModel instance
        assertEquals(selectedZone, viewModel.selectedZone.value)
        assertEquals(75.0, viewModel.selectedFillPercentage.value, 0.001)
        assertEquals(120.0, viewModel.dailyConsumption.value, 0.001)
        assertEquals(UserMode.COMMERCIAL, viewModel.userMode.value)
    }

    @Test
    fun testCustomInitialValues() {
        val zone = MainViewModel.DEFAULT_ZONES[1]
        val viewModel = MainViewModel(
            initialZone = zone,
            initialFillPercentage = 50.0,
            initialDailyConsumption = 80.0,
            initialUserMode = UserMode.COMMERCIAL
        )

        assertEquals(zone, viewModel.selectedZone.value)
        assertEquals(50.0, viewModel.selectedFillPercentage.value, 0.001)
        assertEquals(80.0, viewModel.dailyConsumption.value, 0.001)
        assertEquals(UserMode.COMMERCIAL, viewModel.userMode.value)
    }
}
