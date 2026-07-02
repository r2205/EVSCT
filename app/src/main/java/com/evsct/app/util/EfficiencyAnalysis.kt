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

/** Pair that's continuous (same trip or `continuesPrevious=true`) but is
 *  missing data needed to compute drive efficiency. The reason text is
 *  user-facing so it tells the user exactly what to fill in. */
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
     *   - both are for [vehicle] (caller groups by vehicle),
     *   - either share a non-null trip OR `curr.continuesPrevious` is true,
     *   - no other same-vehicle charge in [allSessions] happened between them,
     *   - both have an odometer reading,
     *   - prev has an end battery %, curr has a start battery %,
     *   - the vehicle has a battery capacity,
     *   - and the battery actually dropped (positive energy used).
     *
     * Continuous pairs that fail one of those checks land in [EfficiencyReport.excluded]
     * with a short reason so the UI can tell the user exactly what's missing.
     *
     * [allSessions] is the vehicle's complete session list, used to detect
     * charges that happened between two in-scope sessions. A trip-scoped
     * caller must pass it: two adjacent-in-the-trip sessions with an
     * untripped charge in between (e.g. a home top-up mid-trip) are NOT
     * physically consecutive — pairing them anyway silently distorts both
     * the distance and the battery delta. Defaults to [sessions] for
     * callers already analyzing the full list, where adjacent pairs can't
     * have anything in between.
     */
    fun analyze(
        sessions: List<ChargingSession>,
        vehicle: Vehicle?,
        allSessions: List<ChargingSession> = sessions,
    ): EfficiencyReport {
        if (sessions.size < 2) return EfficiencyReport(emptyList(), emptyList())
        val sorted = sessions.sortedBy { it.sessionStart }
        val inScopeIds = sorted.mapTo(mutableSetOf()) { it.id }

        val legs = mutableListOf<DrivingLeg>()
        val excluded = mutableListOf<ExcludedPair>()

        for (i in 1 until sorted.size) {
            val prev = sorted[i - 1]
            val curr = sorted[i]
            if (!isContinuous(prev, curr)) continue

            // The same-trip rule (and the continuesPrevious flag) assume
            // nothing charged this vehicle between the two sessions. An
            // out-of-scope charge in the gap breaks that — the battery
            // delta would be distorted by however much it added.
            val interleaved = allSessions.any {
                it.id !in inScopeIds &&
                    it.sessionStart > prev.sessionStart &&
                    it.sessionStart < curr.sessionStart
            }
            if (interleaved) {
                excluded += ExcludedPair(
                    prev, curr,
                    "Another charge happened between these sessions — add it to the trip to measure this drive",
                )
                continue
            }

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

            val capacity = vehicle?.batteryCapacityKwh
            if (capacity == null || capacity <= 0) {
                excluded += ExcludedPair(prev, curr, "Set the vehicle's battery capacity to compute")
                continue
            }
            val endPct = prev.batteryEndPct
            val startPct = curr.batteryStartPct
            if (endPct == null || startPct == null) {
                excluded += ExcludedPair(prev, curr, "Need end battery % on the prior session and start battery % on this one")
                continue
            }
            val delta = endPct - startPct
            if (delta <= 0) {
                excluded += ExcludedPair(prev, curr, "Battery didn't drop between sessions")
                continue
            }
            val energy = delta * capacity / 100.0

            legs += DrivingLeg(
                from = prev,
                to = curr,
                distanceKm = distance,
                energyUsedKwh = energy,
                kmPerKwh = distance / energy,
            )
        }
        return EfficiencyReport(legs, excluded)
    }

    private fun isContinuous(prev: ChargingSession, curr: ChargingSession): Boolean {
        val sameTrip = prev.tripId != null && prev.tripId == curr.tripId
        return sameTrip || curr.continuesPrevious
    }
}
