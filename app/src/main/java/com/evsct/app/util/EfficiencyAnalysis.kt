package com.evsct.app.util

import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.data.entity.Vehicle

/**
 * A drive between two consecutive charging sessions for the same vehicle.
 *
 * "Consecutive" means the user can attest that no untracked charging happened
 * between [from] and [to] — either because both share a trip, or because [to]
 * has its [ChargingSession.continuesPrevious] flag set.
 *
 * Drive energy is always computed from the SoC delta and the vehicle's battery
 * capacity:  energy_used = (batteryEndPct[from] − batteryStartPct[to]) × capacity / 100.
 * That's the only way to get a real number; charging targets vary trip-to-trip
 * so kWh delivered isn't a reliable proxy.
 */
data class DrivingLeg(
    val from: ChargingSession,
    val to: ChargingSession,
    val distanceKm: Double,
    val energyUsedKwh: Double,
    val kmPerKwh: Double,
)

data class EfficiencyReport(val legs: List<DrivingLeg>) {
    val avgKmPerKwh: Double? = run {
        val totalKm = legs.sumOf { it.distanceKm }
        val totalKwh = legs.sumOf { it.energyUsedKwh }
        if (totalKm > 0 && totalKwh > 0) totalKm / totalKwh else null
    }
}

object EfficiencyAnalysis {

    /**
     * Pair adjacent sessions (sorted by sessionStart) into legs. A pair
     * (prev, curr) becomes a leg when:
     *   - both are for [vehicle] (caller groups by vehicle),
     *   - either share a non-null trip OR `curr.continuesPrevious` is true,
     *   - both have an odometer reading,
     *   - prev has an end battery %, curr has a start battery %,
     *   - the vehicle has a battery capacity,
     *   - and the battery actually dropped (positive energy used).
     */
    fun analyze(
        sessions: List<ChargingSession>,
        vehicle: Vehicle?,
    ): EfficiencyReport {
        if (sessions.size < 2) return EfficiencyReport(emptyList())
        val capacity = vehicle?.batteryCapacityKwh
        if (capacity == null || capacity <= 0) return EfficiencyReport(emptyList())

        val sorted = sessions.sortedBy { it.sessionStart }
        val legs = mutableListOf<DrivingLeg>()
        for (i in 1 until sorted.size) {
            val prev = sorted[i - 1]
            val curr = sorted[i]
            if (!isContinuous(prev, curr)) continue

            val prevOdo = prev.odometerKm ?: continue
            val currOdo = curr.odometerKm ?: continue
            val distance = currOdo - prevOdo
            if (distance <= 0) continue

            val endPct = prev.batteryEndPct ?: continue
            val startPct = curr.batteryStartPct ?: continue
            val delta = endPct - startPct
            if (delta <= 0) continue

            val energy = delta * capacity / 100.0
            legs += DrivingLeg(
                from = prev,
                to = curr,
                distanceKm = distance,
                energyUsedKwh = energy,
                kmPerKwh = distance / energy,
            )
        }
        return EfficiencyReport(legs)
    }

    private fun isContinuous(prev: ChargingSession, curr: ChargingSession): Boolean {
        val sameTrip = prev.tripId != null && prev.tripId == curr.tripId
        return sameTrip || curr.continuesPrevious
    }
}
