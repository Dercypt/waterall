package com.waterall.ui

import com.waterall.domain.model.GroundTruthReport
import com.waterall.domain.model.TapFlowStatus
import com.waterall.domain.repository.InMemoryReportRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test verifying [MainViewModel] preserves [com.waterall.domain.repository.ReportRepository] state
 * across ViewModel lifecycle and consumer usage.
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
}
