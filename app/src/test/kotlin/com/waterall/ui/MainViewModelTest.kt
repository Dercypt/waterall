package com.waterall.ui

import com.waterall.domain.model.GroundTruthReport
import com.waterall.domain.model.TapFlowStatus
import com.waterall.domain.model.UserMode
import com.waterall.domain.repository.InMemoryReportRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
        assertEquals(Tab.CALENDAR, viewModel.currentTab.value)
        assertEquals(0, viewModel.drums200L.value)
        assertEquals(0, viewModel.tanks1000L.value)
        assertEquals(0, viewModel.gallons20L.value)
        assertEquals(null, viewModel.customSupplyHours.value)
    }

    @Test
    fun testSetContainerCountsUpdatesStateFlows() {
        val viewModel = MainViewModel()
        viewModel.setDrums200L(3)
        assertEquals(3, viewModel.drums200L.value)

        viewModel.setTanks1000L(2)
        assertEquals(2, viewModel.tanks1000L.value)

        viewModel.setGallons20L(8)
        assertEquals(8, viewModel.gallons20L.value)

        // Non-negative clamping (Law 3 invariant)
        viewModel.setDrums200L(-4)
        assertEquals(0, viewModel.drums200L.value)

        viewModel.setTanks1000L(-1)
        assertEquals(0, viewModel.tanks1000L.value)

        viewModel.setGallons20L(-10)
        assertEquals(0, viewModel.gallons20L.value)

        viewModel.setContainerCounts(drums = 4, tanks = 1, gallons = 12)
        assertEquals(4, viewModel.drums200L.value)
        assertEquals(1, viewModel.tanks1000L.value)
        assertEquals(12, viewModel.gallons20L.value)
    }

    @Test
    fun testSetCustomSupplyHoursUpdatesStateFlow() {
        val viewModel = MainViewModel()
        assertEquals(null, viewModel.customSupplyHours.value)

        viewModel.setCustomSupplyHours(14.5)
        assertEquals(14.5, viewModel.customSupplyHours.value!!, 0.001)

        // Reset to null when user resyncs with schedule
        viewModel.setCustomSupplyHours(null)
        assertEquals(null, viewModel.customSupplyHours.value)

        // Coerce negative input to 0.0
        viewModel.setCustomSupplyHours(-3.0)
        assertEquals(0.0, viewModel.customSupplyHours.value!!, 0.001)
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
    fun testSetCurrentTabUpdatesStateFlow() {
        val viewModel = MainViewModel()
        assertEquals(Tab.CALENDAR, viewModel.currentTab.value)

        viewModel.setCurrentTab(Tab.RESERVES)
        assertEquals(Tab.RESERVES, viewModel.currentTab.value)

        viewModel.setCurrentTab(Tab.REPORT)
        assertEquals(Tab.REPORT, viewModel.currentTab.value)

        viewModel.setCurrentTab(Tab.CALENDAR)
        assertEquals(Tab.CALENDAR, viewModel.currentTab.value)
    }

    @Test
    fun testUiStatePreservationAcrossSimulatedConfigurationChange() {
        val viewModel = MainViewModel()
        val selectedZone = MainViewModel.DEFAULT_ZONES[2]
        viewModel.setSelectedZone(selectedZone)
        viewModel.setSelectedFillPercentage(75.0)
        viewModel.setDailyConsumption(120.0)
        viewModel.setUserMode(UserMode.COMMERCIAL)
        viewModel.setCurrentTab(Tab.RESERVES)
        viewModel.setDrums200L(3)
        viewModel.setTanks1000L(2)
        viewModel.setGallons20L(10)
        viewModel.setCustomSupplyHours(18.5)

        // Simulate activity recreation retrieving the existing ViewModel instance
        assertEquals(selectedZone, viewModel.selectedZone.value)
        assertEquals(75.0, viewModel.selectedFillPercentage.value, 0.001)
        assertEquals(120.0, viewModel.dailyConsumption.value, 0.001)
        assertEquals(UserMode.COMMERCIAL, viewModel.userMode.value)
        assertEquals(Tab.RESERVES, viewModel.currentTab.value)
        assertEquals(3, viewModel.drums200L.value)
        assertEquals(2, viewModel.tanks1000L.value)
        assertEquals(10, viewModel.gallons20L.value)
        assertEquals(18.5, viewModel.customSupplyHours.value!!, 0.001)
    }

    @Test
    fun testCustomInitialValues() {
        val zone = MainViewModel.DEFAULT_ZONES[1]
        val viewModel = MainViewModel(
            initialZone = zone,
            initialFillPercentage = 50.0,
            initialDailyConsumption = 80.0,
            initialUserMode = UserMode.COMMERCIAL,
            initialTab = Tab.REPORT,
            initialDrums200L = 2,
            initialTanks1000L = 1,
            initialGallons20L = 5,
            initialCustomSupplyHours = 12.0
        )

        assertEquals(zone, viewModel.selectedZone.value)
        assertEquals(50.0, viewModel.selectedFillPercentage.value, 0.001)
        assertEquals(80.0, viewModel.dailyConsumption.value, 0.001)
        assertEquals(UserMode.COMMERCIAL, viewModel.userMode.value)
        assertEquals(Tab.REPORT, viewModel.currentTab.value)
        assertEquals(2, viewModel.drums200L.value)
        assertEquals(1, viewModel.tanks1000L.value)
        assertEquals(5, viewModel.gallons20L.value)
        assertEquals(12.0, viewModel.customSupplyHours.value!!, 0.001)
    }

    @Test
    fun testRestorationWithPersistedValuesAndReports() {
        val repo = InMemoryReportRepository()
        val report = GroundTruthReport(
            id = "persisted-rep-1",
            zoneId = "brgy7",
            zoneName = "Zone 7",
            status = TapFlowStatus.DRY,
            timestamp = System.currentTimeMillis() - 5000L
        )
        repo.addReport(report)

        val viewModel = MainViewModel(
            reportRepository = repo,
            initialZone = MainViewModel.DEFAULT_ZONES[2],
            initialFillPercentage = 75.0,
            initialDailyConsumption = 150.0,
            initialUserMode = UserMode.COMMERCIAL
        )

        assertEquals(MainViewModel.DEFAULT_ZONES[2], viewModel.selectedZone.value)
        assertEquals(75.0, viewModel.selectedFillPercentage.value, 0.001)
        assertEquals(150.0, viewModel.dailyConsumption.value, 0.001)
        assertEquals(UserMode.COMMERCIAL, viewModel.userMode.value)
        assertEquals(1, viewModel.reportRepository.getAllReports().size)
        assertEquals(report, viewModel.reportRepository.getAllReports().first())
    }

    @Test
    fun testUserModePreferencesRestorationWorkflow() {
        val viewModel = MainViewModel()
        // Simulate startup with saved commercial user mode
        val savedMode = UserMode.COMMERCIAL
        viewModel.setUserMode(savedMode)
        assertEquals(UserMode.COMMERCIAL, viewModel.userMode.value)

        // Switching back to domestic
        viewModel.setUserMode(UserMode.DOMESTIC)
        assertEquals(UserMode.DOMESTIC, viewModel.userMode.value)
    }

    @Test
    fun testConstructorClampingForNegativeInitialContainerCounts() {
        val viewModel = MainViewModel(
            initialDrums200L = -5,
            initialTanks1000L = -2,
            initialGallons20L = -10,
            initialCustomSupplyHours = -4.5
        )

        assertEquals(0, viewModel.drums200L.value)
        assertEquals(0, viewModel.tanks1000L.value)
        assertEquals(0, viewModel.gallons20L.value)
        assertEquals(0.0, viewModel.customSupplyHours.value!!, 0.001)
    }

    @Test
    fun testContainerCountsBatchClampingEdgeCases() {
        val viewModel = MainViewModel()
        viewModel.setContainerCounts(drums = -10, tanks = 0, gallons = -99)
        assertEquals(0, viewModel.drums200L.value)
        assertEquals(0, viewModel.tanks1000L.value)
        assertEquals(0, viewModel.gallons20L.value)

        viewModel.setContainerCounts(drums = 100, tanks = 50, gallons = 200)
        assertEquals(100, viewModel.drums200L.value)
        assertEquals(50, viewModel.tanks1000L.value)
        assertEquals(200, viewModel.gallons20L.value)
    }

    @Test
    fun testCustomSupplyHoursResetAndEdgeCases() {
        val viewModel = MainViewModel()
        // Default is null (meaning un-overridden, following scheduled countdown)
        assertEquals(null, viewModel.customSupplyHours.value)

        // User enters custom supply hours (e.g. 6.5 hrs)
        viewModel.setCustomSupplyHours(6.5)
        assertEquals(6.5, viewModel.customSupplyHours.value!!, 0.001)

        // Immediate supply window opening (0.0 hrs)
        viewModel.setCustomSupplyHours(0.0)
        assertEquals(0.0, viewModel.customSupplyHours.value!!, 0.001)

        // Resyncing clears override back to null
        viewModel.setCustomSupplyHours(null)
        assertEquals(null, viewModel.customSupplyHours.value)
    }

    @Test
    fun testEmptyScheduleZoneShowsNeutralCopyRatherThanActiveSupply() {
        val emptyZone = com.waterall.domain.model.BarangayZone(
            id = "empty_brgy",
            name = "Zone Empty — Barangay Without Rotation",
            domesticSchedules = emptyList(),
            commercialSchedules = emptyList()
        )
        val viewModel = MainViewModel(initialZone = emptyZone)
        assertEquals(emptyZone, viewModel.selectedZone.value)

        val countdown = com.waterall.domain.calculator.ScheduleCountdownCalculator.calculate(
            zone = viewModel.selectedZone.value,
            userMode = viewModel.userMode.value,
            currentTime = java.time.LocalDateTime.now()
        )
        // Empty schedule must be RATIONING_ACTIVE with 0.0 hours
        assertEquals(com.waterall.domain.calculator.ScheduleFlowState.RATIONING_ACTIVE, countdown.flowState)
        assertEquals(0.0, countdown.hoursUntilNextOpening, 0.001)

        val nextSupplyHours = (viewModel.customSupplyHours.value ?: countdown.hoursUntilNextOpening).coerceAtLeast(0.0)
        assertEquals(0.0, nextSupplyHours, 0.001)

        val inventory = com.waterall.domain.model.StorageInventory(blueDrums200L = 2, fillPercentage = 100.0)
        val result = com.waterall.domain.calculator.RunoutEstimator.calculate(
            inventory = inventory,
            dailyBurnRateLiters = 200.0,
            hoursUntilNextSupply = nextSupplyHours
        )

        val targetUi = TargetRecommendationFormatter.format(
            result = result,
            flowState = countdown.flowState,
            hoursUntilNextSupply = nextSupplyHours
        )

        assertEquals(TargetRecommendationFormatter.EMPTY_TARGET_TEXT, targetUi.targetValueText)
        assertEquals(TargetRecommendationFormatter.EMPTY_SUPPLY_HOURS_SUBTEXT, targetUi.subtext)
        org.junit.Assert.assertNotEquals(TargetRecommendationFormatter.ACTIVE_FLOW_SUBTEXT, targetUi.subtext)
    }

    @Test
    fun testReservesInputsCombineEmitsDeterministicallyAcrossAllNineStateFlows() {
        val viewModel = MainViewModel()
        var calculationCount = 0

        val combinedFlow = combine(
            combine(viewModel.selectedZone, viewModel.userMode) { zone, mode -> zone to mode },
            combine(viewModel.drums200L, viewModel.tanks1000L, viewModel.gallons20L) { d, t, g -> Triple(d, t, g) },
            combine(viewModel.selectedFillPercentage, viewModel.dailyConsumption, viewModel.customSupplyHours) { f, c, h -> Triple(f, c, h) }
        ) { _, _, _ ->
            calculationCount++
        }

        runBlocking {
            val job = launch {
                combinedFlow.collect {}
            }
            delay(50)
            // Initial emission
            assertEquals(1, calculationCount)

            // Modify drums
            viewModel.setDrums200L(5)
            delay(50)
            assertEquals(2, calculationCount)

            // Modify tanks
            viewModel.setTanks1000L(1)
            delay(50)
            assertEquals(3, calculationCount)

            // Modify gallons
            viewModel.setGallons20L(2)
            delay(50)
            assertEquals(4, calculationCount)

            // Modify fill percentage
            viewModel.setSelectedFillPercentage(50.0)
            delay(50)
            assertEquals(5, calculationCount)

            // Modify daily consumption
            viewModel.setDailyConsumption(120.0)
            delay(50)
            assertEquals(6, calculationCount)

            // Modify custom supply hours
            viewModel.setCustomSupplyHours(8.0)
            delay(50)
            assertEquals(7, calculationCount)

            // Modify user mode
            viewModel.setUserMode(UserMode.COMMERCIAL)
            delay(50)
            assertEquals(8, calculationCount)

            // Modify zone
            viewModel.setSelectedZone(MainViewModel.DEFAULT_ZONES[1])
            delay(50)
            assertEquals(9, calculationCount)

            // Duplicate values should NOT cause extra calculations in StateFlow
            viewModel.setDrums200L(5)
            delay(50)
            assertEquals(9, calculationCount)

            job.cancel()
        }
    }

    @Test
    fun testLoadSampleReportsPopulatesRepositoryAcrossZones() {
        val viewModel = MainViewModel()
        assertEquals(0, viewModel.reportRepository.getAllReports().size)

        val now = 1_700_000_000_000L
        viewModel.loadSampleReports(now)

        val reports = viewModel.reportRepository.getAllReports()
        assertEquals(8, reports.size)
        assertEquals(4, viewModel.reportRepository.getReportsForZone("brgy4").size)
        assertEquals(2, viewModel.reportRepository.getReportsForZone("brgy1").size)
        assertEquals(2, viewModel.reportRepository.getReportsForZone("brgy7").size)

        val consensusZone4 = viewModel.reportRepository.getConsensus("brgy4", "Zone 4", now)
        assertEquals(4, consensusZone4.totalReports)
        assertEquals(TapFlowStatus.RUNNING, consensusZone4.dominantStatus)
        assertEquals(75, consensusZone4.statusPercentages[TapFlowStatus.RUNNING])
    }

    @Test
    fun testRepeatedLoadSampleReportsIsIdempotentAndDoesNotDuplicateData() {
        val viewModel = MainViewModel()
        val now = 1_700_000_000_000L

        // First click
        viewModel.loadSampleReports(now)
        assertEquals(8, viewModel.reportRepository.getAllReports().size)
        assertEquals(4, viewModel.reportRepository.getReportsForZone("brgy4").size)

        // Repeated clicks simulating user tapping btnLoadSampleReports repeatedly
        viewModel.loadSampleReports(now + 60_000L)
        viewModel.loadSampleReports(now + 120_000L)

        val reports = viewModel.reportRepository.getAllReports()
        assertEquals(8, reports.size)
        assertEquals(4, viewModel.reportRepository.getReportsForZone("brgy4").size)
        assertEquals(2, viewModel.reportRepository.getReportsForZone("brgy1").size)
        assertEquals(2, viewModel.reportRepository.getReportsForZone("brgy7").size)

        val consensusZone4 = viewModel.reportRepository.getConsensus("brgy4", "Zone 4", now + 120_000L)
        assertEquals(4, consensusZone4.totalReports)
        assertEquals(TapFlowStatus.RUNNING, consensusZone4.dominantStatus)
        assertEquals(75, consensusZone4.statusPercentages[TapFlowStatus.RUNNING])
    }
}
