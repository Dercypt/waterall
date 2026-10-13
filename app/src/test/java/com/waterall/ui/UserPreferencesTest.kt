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
        assertEquals(UserPreferences.DEFAULT_DRUMS, preferences.drums200LCount)
        assertEquals(UserPreferences.DEFAULT_TANKS, preferences.tanks1000LCount)
        assertEquals(UserPreferences.DEFAULT_GALLONS, preferences.gallons20LCount)
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
}
