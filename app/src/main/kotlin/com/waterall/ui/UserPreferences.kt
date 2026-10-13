package com.waterall.ui

import android.content.Context
import android.content.SharedPreferences

/**
 * Abstraction for preference storage allowing pure JVM testing without Android SDK mocks.
 */
interface PreferenceStorage {
    fun getString(key: String, defaultValue: String): String
    fun putString(key: String, value: String)
    fun getInt(key: String, defaultValue: Int): Int
    fun putInt(key: String, value: Int)
}

/**
 * Android SharedPreferences implementation of [PreferenceStorage].
 */
class SharedPreferencesStorage(private val sharedPreferences: SharedPreferences) : PreferenceStorage {
    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    override fun getString(key: String, defaultValue: String): String =
        sharedPreferences.getString(key, defaultValue) ?: defaultValue

    override fun putString(key: String, value: String) {
        sharedPreferences.edit().putString(key, value).apply()
    }

    override fun getInt(key: String, defaultValue: Int): Int =
        sharedPreferences.getInt(key, defaultValue)

    override fun putInt(key: String, value: Int) {
        sharedPreferences.edit().putInt(key, value).apply()
    }

    companion object {
        const val PREFS_NAME = "waterall_user_preferences"
    }
}

/**
 * Lightweight persistence for user selections across app launches using SharedPreferences.
 * Retains container counts (enforcing Law 3 non-negative quantization) and selected Barangay zone.
 */
class UserPreferences(private val storage: PreferenceStorage) {

    constructor(context: Context) : this(SharedPreferencesStorage(context))

    var selectedZoneId: String
        get() = storage.getString(KEY_SELECTED_ZONE_ID, DEFAULT_ZONE_ID)
        set(value) = storage.putString(KEY_SELECTED_ZONE_ID, value)

    var drums200LCount: Int
        get() = storage.getInt(KEY_DRUMS_200L, DEFAULT_DRUMS)
        set(value) = storage.putInt(KEY_DRUMS_200L, maxOf(0, value))

    var tanks1000LCount: Int
        get() = storage.getInt(KEY_TANKS_1000L, DEFAULT_TANKS)
        set(value) = storage.putInt(KEY_TANKS_1000L, maxOf(0, value))

    var gallons20LCount: Int
        get() = storage.getInt(KEY_GALLONS_20L, DEFAULT_GALLONS)
        set(value) = storage.putInt(KEY_GALLONS_20L, maxOf(0, value))

    /**
     * Persists all discrete container counts simultaneously enforcing Law 3 invariants.
     */
    fun saveContainerCounts(drums: Int, tanks: Int, gallons: Int) {
        drums200LCount = drums
        tanks1000LCount = tanks
        gallons20LCount = gallons
    }

    companion object {
        const val KEY_SELECTED_ZONE_ID = "selected_zone_id"
        const val KEY_DRUMS_200L = "drums_200l_count"
        const val KEY_TANKS_1000L = "tanks_1000l_count"
        const val KEY_GALLONS_20L = "gallons_20l_count"

        const val DEFAULT_ZONE_ID = "brgy4"
        const val DEFAULT_DRUMS = 2
        const val DEFAULT_TANKS = 0
        const val DEFAULT_GALLONS = 2
    }
}
