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
        require(timestamp <= System.currentTimeMillis() + 1000) {
            "Law 4 violation: Report timestamp cannot be in the future."
        }
        require(zoneId.isNotBlank()) {
            "Law 4 violation: Zone ID must not be blank."
        }
    }
}
