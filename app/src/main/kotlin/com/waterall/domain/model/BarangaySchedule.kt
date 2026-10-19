package com.waterall.domain.model

import java.time.DayOfWeek
import java.util.Locale

enum class UserMode {
    DOMESTIC,
    COMMERCIAL
}

data class SupplyWindow(
    val dayOfWeek: String,
    val dateLabel: String,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int
) {
    init {
        require(startHour in 0..23 && startMinute in 0..59) {
            "Invalid start time: $startHour:$startMinute"
        }
        require(endHour in 0..23 && endMinute in 0..59) {
            "Invalid end time: $endHour:$endMinute"
        }
        val startTotalMinutes = startHour * 60 + startMinute
        val endTotalMinutes = endHour * 60 + endMinute
        require(endTotalMinutes > startTotalMinutes) {
            "Law 2 violation: SupplyWindow must have positive duration (end > start)."
        }
        dayOfWeekEnum
    }

    val dayOfWeekEnum: DayOfWeek
        get() = when (dayOfWeek.trim().take(3).lowercase(Locale.US)) {
            "mon" -> DayOfWeek.MONDAY
            "tue" -> DayOfWeek.TUESDAY
            "wed" -> DayOfWeek.WEDNESDAY
            "thu" -> DayOfWeek.THURSDAY
            "fri" -> DayOfWeek.FRIDAY
            "sat" -> DayOfWeek.SATURDAY
            "sun" -> DayOfWeek.SUNDAY
            else -> throw IllegalArgumentException("Law 2 violation: Invalid dayOfWeek '$dayOfWeek'.")
        }

    val durationMinutes: Int
        get() = (endHour * 60 + endMinute) - (startHour * 60 + startMinute)

    val formattedRange: String
        get() {
            val startAmPm = if (startHour >= 12) "PM" else "AM"
            val displayStartH = when {
                startHour == 0 -> 12
                startHour > 12 -> startHour - 12
                else -> startHour
            }
            val endAmPm = if (endHour >= 12) "PM" else "AM"
            val displayEndH = when {
                endHour == 0 -> 12
                endHour > 12 -> endHour - 12
                else -> endHour
            }
            return String.format("%d:%02d %s – %d:%02d %s", displayStartH, startMinute, startAmPm, displayEndH, endMinute, endAmPm)
        }
}

data class BarangayZone(
    val id: String,
    val name: String,
    val domesticSchedules: List<SupplyWindow>,
    val commercialSchedules: List<SupplyWindow> = emptyList()
) {
    val schedules: List<SupplyWindow>
        get() = domesticSchedules

    fun getSchedules(mode: UserMode = UserMode.DOMESTIC): List<SupplyWindow> = when (mode) {
        UserMode.DOMESTIC -> domesticSchedules
        UserMode.COMMERCIAL -> commercialSchedules
    }
}

