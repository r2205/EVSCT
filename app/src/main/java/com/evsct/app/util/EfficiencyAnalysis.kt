package com.evsct.app.util

import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.data.entity.Vehicle

/**
 * A drive between two consecutive charging sessions for the same vehicle.
 *
 * "Consecutive" means the user can attest that no untracked charging happened
 * between [from] and [to] — either because both share a trip, or because [to]
 * has its [ChargingSession.continuesPrevious] flag set.
 */
data class DrivingLeg(
    val from: ChargingSession,
    val to: ChargingSession,
    val distanceKm: Double,
    val energyUsedKwh: Double,
    val kmPerKwh: Double,
    val mode: LegMode,
)

enum class LegMode {
    /** Energy was derived from the SoC delta and the vehicle's battery capacity. */
    PRECISE_SOC,

    /** Energy was approximated as kWh delivered at the prior session, assuming
     *  similar arrival vs. departure SoC at each end. */
    ENERGY_PROXY,
}

/** Excluded pair plus a short reason — useful for "Add odometer to compute" hints. */
data class ExcludedPair(
    val from: ChargingSession,
    val to: ChargingSession,
    val reason: String,
)

data class EfficiencyReport(
    val legs: List<DrivingLeg>,
    val excluded: List<ExcludedPair>,
) {
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
     *   - both are for [vehicle] (already filtered by caller),
     *   - both have an odometer reading,
     *   - and either share a non-null trip OR `curr.continuesPrevious` is true.
     */
    fun analyze(
        sessions: List<ChargingSession>,
        vehicle: Vehicle?,
    ): EfficiencyReport {
        if (sessions.size < 2) return EfficiencyReport(emptyList(), emptyList())
        val sorted = sessions.sortedBy { it.sessionStart }

        val legs = mutableListOf<DrivingLeg>()
        val excluded = mutableListOf<ExcludedPair>()

        for (i in 1 until sorted.size) {
            val prev = sorted[i - 1]
            val curr = sorted[i]
            if (!isContinuous(prev, curr)) continue

            val prevOdo = prev.odometerKm
            val currOdo = curr.odometerKm
            if (prevOdo == null || currOdo == null) {
                excluded += ExcludedPair(prev, curr, "Add odometer on both sessions")
                continue
            }
            val distance = currOdo - prevOdo
            if (distance <= 0) {
                excluded += ExcludedPair(prev, curr, "Odometer didn't increase")
                continue
            }

            val precise = preciseEnergyUsedKwh(prev, curr, vehicle)
            val (energy, mode) = when {
                precise != null && precise > 0 ->
                    precise to LegMode.PRECISE_SOC
                prev.energyKwh != null && prev.energyKwh > 0 ->
                    prev.energyKwh to LegMode.ENERGY_PROXY
                else -> {
                    excluded += ExcludedPair(prev, curr, "Need battery % on both, or kWh on the prior session")
                    continue
                }
            }

            legs += DrivingLeg(
                from = prev,
                to = curr,
                distanceKm = distance,
                energyUsedKwh = energy,
                kmPerKwh = distance / energy,
                mode = mode,
            )
        }
        return EfficiencyReport(legs, excluded)
    }

    private fun isContinuous(prev: ChargingSession, curr: ChargingSession): Boolean {
        val sameTrip = prev.tripId != null && prev.tripId == curr.tripId
        return sameTrip || curr.continuesPrevious
    }

    /** Energy depleted between [prev] (end SoC) and [curr] (start SoC). Null
     *  when battery readings or capacity aren't available. */
    private fun preciseEnergyUsedKwh(
        prev: ChargingSession,
        curr: ChargingSession,
        vehicle: Vehicle?,
    ): Double? {
        val capacity = vehicle?.batteryCapacityKwh ?: return null
        val endPct = prev.batteryEndPct ?: return null
        val startPct = curr.batteryStartPct ?: return null
        val delta = endPct - startPct
        if (delta <= 0) return null
        return delta * capacity / 100.0
    }
}
