package com.waterall.domain.calculator

import com.waterall.domain.model.BarangayZone
import com.waterall.domain.model.SupplyWindow
import com.waterall.domain.model.UserMode
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale

/**
 * Closed enum representing physical supply window state.
 */
enum class ScheduleFlowState(val badgeLabel: String) {
    FLOW_ACTIVE("FLOW ACTIVE"),
    RATIONING_ACTIVE("RATIONING ACTIVE")
}

/**
 * Immutable result for deterministic schedule countdown calculation.
 * Satisfies Law 2 (Rationing Window Chronology, Disjoint Intervals, and Monotonic Countdown).
 *
 * @param flowState Current state: FLOW_ACTIVE or RATIONING_ACTIVE
 * @param remainingSeconds Non-negative remaining countdown time in seconds
 * @param targetDateTime The target timestamp counted down towards (closure or opening)
 * @param activeWindow The active supply window if flow is active, null otherwise
 * @param nextWindow The next supply window to open
 */
data class CountdownCalculationResult(
    val flowState: ScheduleFlowState,
    val remainingSeconds: Long,
    val targetDateTime: LocalDateTime?,
    val activeWindow: SupplyWindow?,
    val nextWindow: SupplyWindow?
) {
    init {
        require(remainingSeconds >= 0L) {
            "Law 2 violation: Countdown seconds must be strictly non-negative."
        }
    }

    val remainingHours: Double
        get() = remainingSeconds / 3600.0

    /**
     * Formats remaining countdown time into standard two-digit padded representation HH:MM:SS.
     * Guaranteed minimum 2 digits for hours (e.g., 02:47:13, 26:00:00).
     */
    val formattedCountdown: String
        get() {
            val hrs = remainingSeconds / 3600
            val mins = (remainingSeconds % 3600) / 60
            val secs = remainingSeconds % 60
            return String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
        }
}

/**
 * Pure domain calculator for supply schedule countdown and flow window detection.
 * Enforces Law 2 Invariants:
 * 1. Positive duration intervals
 * 2. Temporal non-overlap within a schedule
 * 3. Monotonic non-negative countdown toward window closure (active) or valve opening (dry rationing)
 */
object ScheduleCountdownCalculator {

    /**
     * Calculates countdown result for a given zone and user mode.
     */
    fun calculate(
        zone: BarangayZone,
        userMode: UserMode,
        currentTime: LocalDateTime = LocalDateTime.now()
    ): CountdownCalculationResult {
        return calculate(zone.getSchedules(userMode), currentTime)
    }

    /**
     * Calculates countdown result for a list of supply windows and reference time.
     */
    fun calculate(
        schedules: List<SupplyWindow>,
        currentTime: LocalDateTime = LocalDateTime.now()
    ): CountdownCalculationResult {
        if (schedules.isEmpty()) {
            return CountdownCalculationResult(
                flowState = ScheduleFlowState.RATIONING_ACTIVE,
                remainingSeconds = 0L,
                targetDateTime = null,
                activeWindow = null,
                nextWindow = null
            )
        }

        // Validate Law 2 Invariant 2 (temporal non-overlap)
        validateNonOverlapping(schedules)

        val currentDay = currentTime.dayOfWeek
        val currentTimeOfDay = currentTime.toLocalTime()

        // Check if currentTime falls within an active supply window
        val activeWindow = schedules.firstOrNull { window ->
            if (window.dayOfWeekEnum == currentDay) {
                val start = LocalTime.of(window.startHour, window.startMinute)
                val end = LocalTime.of(window.endHour, window.endMinute)
                !currentTimeOfDay.isBefore(start) && currentTimeOfDay.isBefore(end)
            } else {
                false
            }
        }

        if (activeWindow != null) {
            val targetDateTime = currentTime.toLocalDate()
                .atTime(activeWindow.endHour, activeWindow.endMinute)
            val diffSeconds = maxOf(0L, Duration.between(currentTime, targetDateTime).seconds)

            val nextOccurrence = findNearestFutureWindow(
                schedules = schedules,
                referenceTime = targetDateTime
            )

            return CountdownCalculationResult(
                flowState = ScheduleFlowState.FLOW_ACTIVE,
                remainingSeconds = diffSeconds,
                targetDateTime = targetDateTime,
                activeWindow = activeWindow,
                nextWindow = nextOccurrence?.first
            )
        }

        // Otherwise, during dry intervals: countdown to next valve opening
        val nearest = findNearestFutureWindow(
            schedules = schedules,
            referenceTime = currentTime
        )

        val (nextWindow, nextStartDateTime) = nearest
            ?: return CountdownCalculationResult(
                flowState = ScheduleFlowState.RATIONING_ACTIVE,
                remainingSeconds = 0L,
                targetDateTime = null,
                activeWindow = null,
                nextWindow = null
            )

        val diffSeconds = maxOf(0L, Duration.between(currentTime, nextStartDateTime).seconds)

        return CountdownCalculationResult(
            flowState = ScheduleFlowState.RATIONING_ACTIVE,
            remainingSeconds = diffSeconds,
            targetDateTime = nextStartDateTime,
            activeWindow = null,
            nextWindow = nextWindow
        )
    }

    /**
     * Enforces Law 2 Invariant 2: No two supply windows in the schedule may overlap in time.
     */
    fun validateNonOverlapping(windows: List<SupplyWindow>) {
        val grouped = windows.groupBy { it.dayOfWeekEnum }
        for ((day, dayWindows) in grouped) {
            val sorted = dayWindows.sortedBy { it.startHour * 60 + it.startMinute }
            for (i in 0 until sorted.size - 1) {
                val current = sorted[i]
                val next = sorted[i + 1]
                val currentEnd = current.endHour * 60 + current.endMinute
                val nextStart = next.startHour * 60 + next.startMinute
                require(nextStart >= currentEnd) {
                    "Law 2 violation: Supply windows overlap on $day (${current.formattedRange} vs ${next.formattedRange})."
                }
            }
        }
    }

    /**
     * Finds the nearest upcoming supply window start time on or after [referenceTime].
     */
    fun findNearestFutureWindow(
        schedules: List<SupplyWindow>,
        referenceTime: LocalDateTime
    ): Pair<SupplyWindow, LocalDateTime>? {
        if (schedules.isEmpty()) return null

        val currentDayVal = referenceTime.dayOfWeek.value
        val referenceLocalTime = referenceTime.toLocalTime()

        var nearestPair: Pair<SupplyWindow, LocalDateTime>? = null

        for (window in schedules) {
            val targetDayVal = window.dayOfWeekEnum.value
            val daysAhead = (targetDayVal - currentDayVal + 7) % 7
            val windowStartTime = LocalTime.of(window.startHour, window.startMinute)

            val candidateDateTime = when {
                daysAhead == 0 && referenceLocalTime.isBefore(windowStartTime) -> {
                    // Starts later today
                    referenceTime.toLocalDate().atTime(windowStartTime)
                }
                daysAhead == 0 -> {
                    // Start time was earlier today or currently running -> next occurrence is next week
                    referenceTime.toLocalDate().plusDays(7).atTime(windowStartTime)
                }
                else -> {
                    referenceTime.toLocalDate().plusDays(daysAhead.toLong()).atTime(windowStartTime)
                }
            }

            if (nearestPair == null || candidateDateTime.isBefore(nearestPair.second)) {
                nearestPair = Pair(window, candidateDateTime)
            }
        }

        return nearestPair
    }
}
