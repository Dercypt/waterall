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

    var fillPercentage: Double
        get() = storage.getString(KEY_FILL_PERCENTAGE, DEFAULT_FILL_PERCENTAGE.toString())
            .toDoubleOrNull()?.coerceIn(0.0, 100.0) ?: DEFAULT_FILL_PERCENTAGE
        set(value) = storage.putString(KEY_FILL_PERCENTAGE, value.coerceIn(0.0, 100.0).toString())

    var dailyConsumption: Double
        get() = storage.getString(KEY_DAILY_CONSUMPTION, DEFAULT_DAILY_CONSUMPTION.toString())
            .toDoubleOrNull()?.coerceAtLeast(0.0) ?: DEFAULT_DAILY_CONSUMPTION
        set(value) = storage.putString(KEY_DAILY_CONSUMPTION, maxOf(0.0, value).toString())

    /**
     * Persists all discrete container counts simultaneously enforcing Law 3 invariants.
     */
    fun saveContainerCounts(drums: Int, tanks: Int, gallons: Int) {
        drums200LCount = drums
        tanks1000LCount = tanks
        gallons20LCount = gallons
    }

    /**
     * Serializes submitted ground truth reports into preference storage.
     */
    fun saveReports(reports: List<com.waterall.domain.model.GroundTruthReport>) {
        val serialized = reports.joinToString("\n") { report ->
            "${report.id}\t${report.zoneId}\t${report.zoneName}\t${report.status.name}\t${report.timestamp}\t${report.notes.replace("\t", " ").replace("\n", " ")}"
        }
        storage.putString(KEY_SUBMITTED_REPORTS, serialized)
    }

    /**
     * Deserializes submitted ground truth reports from preference storage.
     */
    fun getReports(): List<com.waterall.domain.model.GroundTruthReport> {
        val raw = storage.getString(KEY_SUBMITTED_REPORTS, "")
        if (raw.isBlank()) return emptyList()
        return raw.lines().mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            val parts = line.split("\t")
            if (parts.size >= 5) {
                val id = parts[0]
                val zoneId = parts[1]
                val zoneName = parts[2]
                val status = runCatching { com.waterall.domain.model.TapFlowStatus.valueOf(parts[3]) }.getOrNull()
                    ?: return@mapNotNull null
                val timestamp = parts[4].toLongOrNull() ?: return@mapNotNull null
                val notes = if (parts.size >= 6) parts.subList(5, parts.size).joinToString("\t") else ""
                runCatching {
                    com.waterall.domain.model.GroundTruthReport(
                        id = id,
                        zoneId = zoneId,
                        zoneName = zoneName,
                        status = status,
                        notes = notes,
                        timestamp = timestamp
                    )
                }.getOrNull()
            } else {
                null
            }
        }
    }

    companion object {
        const val KEY_SELECTED_ZONE_ID = "selected_zone_id"
        const val KEY_DRUMS_200L = "drums_200l_count"
        const val KEY_TANKS_1000L = "tanks_1000l_count"
        const val KEY_GALLONS_20L = "gallons_20l_count"
        const val KEY_FILL_PERCENTAGE = "fill_percentage"
        const val KEY_DAILY_CONSUMPTION = "daily_consumption"
        const val KEY_SUBMITTED_REPORTS = "submitted_reports"

        const val DEFAULT_ZONE_ID = "brgy4"
        const val DEFAULT_DRUMS = 0
        const val DEFAULT_TANKS = 0
        const val DEFAULT_GALLONS = 0
        const val DEFAULT_FILL_PERCENTAGE = 0.0
        const val DEFAULT_DAILY_CONSUMPTION = 0.0
    }
}
