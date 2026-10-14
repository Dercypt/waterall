package com.waterall.domain.model

enum class TapFlowStatus(val label: String, val subtitle: String) {
    RUNNING("Water Running", "Strong Flow"),
    LOW_PRESSURE("Low Pressure", "Trickle Flow"),
    DRY("Completely Dry", "No Supply")
}

data class GroundTruthReport(
    val id: String,
    val zoneId: String,
    val zoneName: String,
    val status: TapFlowStatus,
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    init {
        require(timestamp <= System.currentTimeMillis() + CLOCK_SKEW_TOLERANCE_MS) {
            "Law 4 violation: Report timestamp cannot be in the future."
        }
        require(zoneId.isNotBlank()) {
            "Law 4 violation: Zone ID must not be blank."
        }
    }

    /**
     * Formats timestamp into a human-readable relative time string enforcing Law 4.
     */
    fun formatRelativeTime(now: Long = System.currentTimeMillis()): String =
        formatRelativeTimestamp(timestamp, now)

    companion object {
        const val CLOCK_SKEW_TOLERANCE_MS = 1000L

        /**
         * Formats epoch timestamp relative to [now] enforcing Law 4 invariant (no future timestamps).
         * Format examples: "Reported just now", "Reported 12m ago", "Reported 2h ago", "Reported 1d ago".
         */
        fun formatRelativeTimestamp(timestamp: Long, now: Long = System.currentTimeMillis()): String {
            require(timestamp <= now + CLOCK_SKEW_TOLERANCE_MS) {
                "Law 4 violation: Report timestamp cannot be in the future (timestamp=$timestamp, now=$now)."
            }
            val diffMillis = maxOf(0L, now - timestamp)
            val diffSeconds = diffMillis / 1000
            val diffMinutes = diffSeconds / 60
            val diffHours = diffMinutes / 60
            val diffDays = diffHours / 24

            return when {
                diffMinutes < 1 -> "Reported just now"
                diffHours < 1 -> "Reported ${diffMinutes}m ago"
                diffDays < 1 -> "Reported ${diffHours}h ago"
                else -> "Reported ${diffDays}d ago"
            }
        }
    }
}
