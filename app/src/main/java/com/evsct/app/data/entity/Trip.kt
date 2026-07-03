package com.evsct.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.evsct.app.util.CurrencyTotals

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
    /**
     * Optional battery % when the trip began (e.g. 100 after a full home
     * charge) and when it ended. Together with the start/end odometer these
     * anchor the trip's first and last efficiency legs — the drive to the
     * first charging stop and the drive home from the last one, which no
     * session pair can measure. Only used when every session in the trip
     * belongs to one vehicle (a trip-level battery % is ambiguous across
     * two cars).
     */
    val startBatteryPct: Int? = null,
    val endBatteryPct: Int? = null,
    val notes: String? = null,
    /** Palette key used to color this trip's pins on the map. Null = unset
     *  (auto-assigned on insert by [com.evsct.app.data.repository.TripRepository]). */
    val pinColor: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

data class TripWithStats(
    val trip: Trip,
    val sessionCount: Int,
    /** Costs grouped by per-session currency. Renderers should show the
     *  multi-currency breakdown when mixed and suppress derived rates. */
    val totalCostByCurrency: CurrencyTotals,
    val totalEnergyKwh: Double,
    val totalDistanceKm: Double,
) {
    /** Cost per km. Only meaningful when sessions share a single currency;
     *  null when mixed (or when distance is zero). */
    val costPerKm: Double? get() {
        val total = totalCostByCurrency.singleTotal ?: return null
        return if (totalDistanceKm > 0) total / totalDistanceKm else null
    }

    /** Cost per kWh. Same rule as costPerKm — single-currency only. */
    val costPerKwh: Double? get() {
        val total = totalCostByCurrency.singleTotal ?: return null
        return if (totalEnergyKwh > 0) total / totalEnergyKwh else null
    }
}
