package com.evsct.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trips")
data class Trip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startDate: Long? = null,
    val endDate: Long? = null,
    /**
     * Optional odometer reading at trip start. When both [startOdometerKm]
     * and [endOdometerKm] are filled, the trip's total distance uses
     * `end - start` instead of inferring from session odometer readings.
     */
    val startOdometerKm: Double? = null,
    val endOdometerKm: Double? = null,
    val notes: String? = null,
    /** Palette key used to color this trip's pins on the map. Null = unset
     *  (auto-assigned on insert by [com.evsct.app.data.repository.TripRepository]). */
    val pinColor: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

data class TripWithStats(
    val trip: Trip,
    val sessionCount: Int,
    val totalCost: Double,
    val totalEnergyKwh: Double,
    val totalDistanceKm: Double,
) {
    val costPerKm: Double? get() = if (totalDistanceKm > 0) totalCost / totalDistanceKm else null
    val costPerKwh: Double? get() = if (totalEnergyKwh > 0) totalCost / totalEnergyKwh else null
}
