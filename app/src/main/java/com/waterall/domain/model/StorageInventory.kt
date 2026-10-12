package com.waterall.domain.model

enum class VesselType(val displayName: String, val unitCapacityLiters: Double) {
    BLUE_DRUM_200L("Standard Blue Drum (200L)", 200.0),
    OVERHEAD_TANK_1000L("Overhead Stainless/Poly Tank (1,000L)", 1000.0),
    GALLON_CONTAINER_20L("Slim / Round Gallon (20L)", 20.0)
}

data class StorageInventory(
    val blueDrums200L: Int = 0,
    val overheadTanks1000L: Int = 0,
    val gallons20L: Int = 0,
    val fillPercentage: Double = 100.0 // 0.0 to 100.0%
) {
    init {
        require(blueDrums200L >= 0 && overheadTanks1000L >= 0 && gallons20L >= 0) {
            "Law 3 violation: Container counts must be non-negative integers."
        }
        require(fillPercentage in 0.0..100.0) {
            "Law 1 violation: Fill percentage must be between 0% and 100%."
        }
    }

    val totalCapacityLiters: Double
        get() = (blueDrums200L * VesselType.BLUE_DRUM_200L.unitCapacityLiters) +
                (overheadTanks1000L * VesselType.OVERHEAD_TANK_1000L.unitCapacityLiters) +
                (gallons20L * VesselType.GALLON_CONTAINER_20L.unitCapacityLiters)

    val currentStoredVolumeLiters: Double
        get() = (totalCapacityLiters * (fillPercentage / 100.0)).coerceIn(0.0, totalCapacityLiters)
}
