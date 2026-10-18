package com.waterall.ui

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit test verifying lightweight preference persistence and Law 3 quantization invariants.
 */
class UserPreferencesTest {

    private class InMemoryPreferenceStorage : PreferenceStorage {
        private val stringMap = mutableMapOf<String, String>()
        private val intMap = mutableMapOf<String, Int>()

        override fun getString(key: String, defaultValue: String): String =
            stringMap[key] ?: defaultValue

        override fun putString(key: String, value: String) {
            stringMap[key] = value
        }

        override fun getInt(key: String, defaultValue: Int): Int =
            intMap[key] ?: defaultValue

        override fun putInt(key: String, value: Int) {
            intMap[key] = value
        }
    }

    private lateinit var storage: InMemoryPreferenceStorage
    private lateinit var preferences: UserPreferences

    @Before
    fun setUp() {
        storage = InMemoryPreferenceStorage()
        preferences = UserPreferences(storage)
    }

    @Test
    fun testDefaultValuesMatchSpecification() {
        assertEquals(UserPreferences.DEFAULT_ZONE_ID, preferences.selectedZoneId)
        assertEquals(0, preferences.drums200LCount)
        assertEquals(0, preferences.tanks1000LCount)
        assertEquals(0, preferences.gallons20LCount)
        assertEquals(0.0, preferences.fillPercentage, 0.001)
        assertEquals(0.0, preferences.dailyConsumption, 0.001)
        assertEquals(0, preferences.getReports().size)
    }

    @Test
    fun testPersistAndRetrieveZone() {
        preferences.selectedZoneId = "brgy7"
        assertEquals("brgy7", preferences.selectedZoneId)
    }

    @Test
    fun testPersistAndRetrieveContainerCounts() {
        preferences.saveContainerCounts(drums = 5, tanks = 2, gallons = 10)
        assertEquals(5, preferences.drums200LCount)
        assertEquals(2, preferences.tanks1000LCount)
        assertEquals(10, preferences.gallons20LCount)
    }

    @Test
    fun testNonNegativeContainerClampingPerLaw3() {
        preferences.drums200LCount = -3
        preferences.tanks1000LCount = -1
        preferences.gallons20LCount = -10

        assertEquals(0, preferences.drums200LCount)
        assertEquals(0, preferences.tanks1000LCount)
        assertEquals(0, preferences.gallons20LCount)
    }

    @Test
    fun testPersistAndRetrieveFillPercentage() {
        preferences.fillPercentage = 75.0
        assertEquals(75.0, preferences.fillPercentage, 0.001)

        preferences.fillPercentage = 150.0
        assertEquals(100.0, preferences.fillPercentage, 0.001)

        preferences.fillPercentage = -20.0
        assertEquals(0.0, preferences.fillPercentage, 0.001)
    }

    @Test
    fun testPersistAndRetrieveDailyConsumption() {
        preferences.dailyConsumption = 120.5
        assertEquals(120.5, preferences.dailyConsumption, 0.001)

        preferences.dailyConsumption = -50.0
        assertEquals(0.0, preferences.dailyConsumption, 0.001)
    }

    @Test
    fun testPersistAndRetrieveReports() {
        val now = 1_700_000_000_000L
        val reports = listOf(
            com.waterall.domain.model.GroundTruthReport(
                id = "rep-1",
                zoneId = "brgy4",
                zoneName = "Zone 4",
                status = com.waterall.domain.model.TapFlowStatus.RUNNING,
                notes = "Good flow",
                timestamp = now - 5000L
            ),
            com.waterall.domain.model.GroundTruthReport(
                id = "rep-2",
                zoneId = "brgy1",
                zoneName = "Zone 1",
                status = com.waterall.domain.model.TapFlowStatus.DRY,
                notes = "No water\nMain pipe shut",
                timestamp = now - 2000L
            )
        )

        preferences.saveReports(reports)
        val loaded = preferences.getReports()

        assertEquals(2, loaded.size)
        assertEquals("rep-1", loaded[0].id)
        assertEquals("brgy4", loaded[0].zoneId)
        assertEquals(com.waterall.domain.model.TapFlowStatus.RUNNING, loaded[0].status)
        assertEquals("Good flow", loaded[0].notes)
        assertEquals(now - 5000L, loaded[0].timestamp)

        assertEquals("rep-2", loaded[1].id)
        assertEquals("brgy1", loaded[1].zoneId)
        assertEquals(com.waterall.domain.model.TapFlowStatus.DRY, loaded[1].status)
        assertEquals("No water Main pipe shut", loaded[1].notes)
        assertEquals(now - 2000L, loaded[1].timestamp)
    }
}
