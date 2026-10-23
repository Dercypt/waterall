package com.waterall.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.waterall.domain.model.BarangayZone
import com.waterall.domain.model.SupplyWindow
import com.waterall.domain.model.UserMode
import com.waterall.domain.repository.InMemoryReportRepository
import com.waterall.domain.repository.ReportRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Top-level application navigation tabs.
 */
enum class Tab {
    CALENDAR,
    RESERVES,
    REPORT
}

/**
 * ViewModel managing UI state, container inventories, custom supply-hour overrides, and the crowdsourced
 * report repository across Activity configuration changes (e.g. screen rotation).
 */
class MainViewModel(
    val reportRepository: ReportRepository = InMemoryReportRepository.createDefault(),
    initialZone: BarangayZone = DEFAULT_ZONES[0],
    initialFillPercentage: Double = 0.0,
    initialDailyConsumption: Double = 0.0,
    initialUserMode: UserMode = UserMode.DOMESTIC,
    initialTab: Tab = Tab.CALENDAR,
    initialDrums200L: Int = 0,
    initialTanks1000L: Int = 0,
    initialGallons20L: Int = 0,
    initialCustomSupplyHours: Double? = null
) : ViewModel() {

    private val _selectedZone = MutableStateFlow(initialZone)
    val selectedZone: StateFlow<BarangayZone> = _selectedZone.asStateFlow()

    private val _selectedFillPercentage = MutableStateFlow(initialFillPercentage)
    val selectedFillPercentage: StateFlow<Double> = _selectedFillPercentage.asStateFlow()

    private val _dailyConsumption = MutableStateFlow(initialDailyConsumption)
    val dailyConsumption: StateFlow<Double> = _dailyConsumption.asStateFlow()

    private val _userMode = MutableStateFlow(initialUserMode)
    val userMode: StateFlow<UserMode> = _userMode.asStateFlow()

    private val _currentTab = MutableStateFlow(initialTab)
    val currentTab: StateFlow<Tab> = _currentTab.asStateFlow()

    private val _drums200L = MutableStateFlow(initialDrums200L.coerceAtLeast(0))
    val drums200L: StateFlow<Int> = _drums200L.asStateFlow()

    private val _tanks1000L = MutableStateFlow(initialTanks1000L.coerceAtLeast(0))
    val tanks1000L: StateFlow<Int> = _tanks1000L.asStateFlow()

    private val _gallons20L = MutableStateFlow(initialGallons20L.coerceAtLeast(0))
    val gallons20L: StateFlow<Int> = _gallons20L.asStateFlow()

    private val _customSupplyHours = MutableStateFlow(initialCustomSupplyHours?.coerceAtLeast(0.0))
    val customSupplyHours: StateFlow<Double?> = _customSupplyHours.asStateFlow()

    fun setSelectedZone(zone: BarangayZone) {
        _selectedZone.value = zone
    }

    fun setSelectedFillPercentage(percentage: Double) {
        _selectedFillPercentage.value = percentage.coerceIn(0.0, 100.0)
    }

    fun setDailyConsumption(consumption: Double) {
        _dailyConsumption.value = consumption.coerceAtLeast(0.0)
    }

    fun setUserMode(mode: UserMode) {
        _userMode.value = mode
    }

    fun setCurrentTab(tab: Tab) {
        _currentTab.value = tab
    }

    fun setDrums200L(count: Int) {
        _drums200L.value = maxOf(0, count)
    }

    fun setTanks1000L(count: Int) {
        _tanks1000L.value = maxOf(0, count)
    }

    fun setGallons20L(count: Int) {
        _gallons20L.value = maxOf(0, count)
    }

    fun setContainerCounts(drums: Int, tanks: Int, gallons: Int) {
        setDrums200L(drums)
        setTanks1000L(tanks)
        setGallons20L(gallons)
    }

    fun setCustomSupplyHours(hours: Double?) {
        _customSupplyHours.value = hours?.coerceAtLeast(0.0)
    }

    companion object {
        // Mock Baguio Water District Rotation Data
        val DEFAULT_ZONES = listOf(
            BarangayZone(
                id = "brgy4",
                name = "Zone 4 — Brgy. San Isidro",
                domesticSchedules = listOf(
                    SupplyWindow("Mon", "Mon", 6, 0, 10, 0),
                    SupplyWindow("Wed", "Wed", 5, 0, 9, 0),
                    SupplyWindow("Fri", "Fri", 6, 0, 11, 0)
                ),
                commercialSchedules = listOf(
                    SupplyWindow("Tue", "Tue", 8, 0, 14, 0),
                    SupplyWindow("Thu", "Thu", 8, 0, 14, 0)
                )
            ),
            BarangayZone(
                id = "brgy1",
                name = "Zone 1 — Brgy. Cabinet Hill",
                domesticSchedules = listOf(
                    SupplyWindow("Tue", "Tue", 5, 30, 9, 30),
                    SupplyWindow("Thu", "Thu", 5, 30, 9, 30),
                    SupplyWindow("Sat", "Sat", 6, 0, 12, 0)
                ),
                commercialSchedules = listOf(
                    SupplyWindow("Mon", "Mon", 7, 0, 13, 0),
                    SupplyWindow("Wed", "Wed", 7, 0, 13, 0)
                )
            ),
            BarangayZone(
                id = "brgy7",
                name = "Zone 7 — Brgy. Trancoville",
                domesticSchedules = listOf(
                    SupplyWindow("Wed", "Wed", 6, 0, 10, 30),
                    SupplyWindow("Sat", "Sat", 5, 0, 10, 0)
                ),
                commercialSchedules = listOf(
                    SupplyWindow("Fri", "Fri", 8, 0, 15, 0)
                )
            )
        )

        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                    return MainViewModel() as T
                }
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }
    }
}
