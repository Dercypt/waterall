package com.waterall.domain.model

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
        val startTotalMinutes = startHour * 60 + startMinute
        val endTotalMinutes = endHour * 60 + endMinute
        require(endTotalMinutes > startTotalMinutes) {
            "Law 2 violation: SupplyWindow must have positive duration (end > start)."
        }
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
    val commercialSchedules: List<SupplyWindow>
)
