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

/**
 * Battery/odometer state at a trip boundary (the trip's start or end),
 * taken from the trip-level fields the user filled in. Acts as a virtual
 * session endpoint so the drive to the first charging stop and the drive
 * home from the last one can produce legs — no session pair covers those.
 * [atMillis] is the trip's start/end date when set; it bounds the
 * interleave check (a charge logged between the anchor and its session
 * invalidates the pairing). Null means no time bound — entering the
 * trip-level readings is itself the attestation.
 */
data class TripAnchor(
    val odometerKm: Double?,
    val batteryPct: Int?,
    val atMillis: Long?,
) {
    /** An anchor with neither reading has nothing to measure or report. */
    val hasData: Boolean get() = odometerKm != null || batteryPct != null
}

object EfficiencyAnalysis {

    /** Synthetic ids for the virtual sessions that carry trip anchors into
     *  [DrivingLeg]/[ExcludedPair]. Never present in the database; the UI
     *  maps them to "Trip start" / "Trip end" labels. */
    const val TRIP_START_ANCHOR_ID = -100L
    const val TRIP_END_ANCHOR_ID = -101L

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
     *
     * [tripStart]/[tripEnd] are the trip-level boundary readings (see
     * [TripAnchor]): when present they add a leg from the trip's start to
     * the first session and from the last session to the trip's end. With
     * both anchors and zero sessions the whole trip is a single leg (a
     * drive with no charging stops at all). The caller is responsible for
     * only passing anchors when the trip's sessions belong to one vehicle.
     */
    fun analyze(
        sessions: List<ChargingSession>,
        vehicle: Vehicle?,
        allSessions: List<ChargingSession> = sessions,
        tripStart: TripAnchor? = null,
        tripEnd: TripAnchor? = null,
    ): EfficiencyReport {
        val sorted = sessions.sortedWith(TIMELINE_ORDER)
        val inScopeIds = sorted.mapTo(mutableSetOf()) { it.id }

        val legs = mutableListOf<DrivingLeg>()
        val excluded = mutableListOf<ExcludedPair>()

        fun record(prev: ChargingSession, curr: ChargingSession, result: PairResult) {
            when (result) {
                is PairResult.Leg -> legs += result.leg
                is PairResult.Excluded -> excluded += ExcludedPair(prev, curr, result.reason)
            }
        }

        for (i in 1 until sorted.size) {
            val prev = sorted[i - 1]
            val curr = sorted[i]
            if (!isContinuous(prev, curr)) continue

            // The same-trip rule (and the continuesPrevious flag) assume
            // nothing charged this vehicle between the two sessions. An
            // out-of-scope charge in the gap breaks that — the battery
            // delta would be distorted by however much it added.
            if (hasInterleavedCharge(allSessions, inScopeIds, prev, curr)) {
                excluded += ExcludedPair(
                    prev, curr,
                    "Another charge happened between these sessions — add it to the trip to measure this drive",
                )
                continue
            }

            record(prev, curr, measurePair(prev, curr, vehicle))
        }

        // Virtual anchor legs from the trip-level boundary readings. The
        // start anchor acts like a session that "ended" at the trip's
        // start state; the end anchor like one that "started" at the
        // trip's end state. measurePair applies the same odometer /
        // battery / capacity rules as real pairs.
        val first = sorted.firstOrNull()
        val last = sorted.lastOrNull()
        val startPseudo = tripStart?.takeIf { it.hasData }?.let { anchor ->
            ChargingSession(
                id = TRIP_START_ANCHOR_ID,
                // Sorts strictly before the first session even when the
                // trip's dates were filled in loosely.
                sessionStart = minOf(
                    anchor.atMillis ?: Long.MAX_VALUE,
                    first?.sessionStart?.minus(1) ?: (anchor.atMillis ?: 0L),
                ),
                odometerKm = anchor.odometerKm,
                batteryEndPct = anchor.batteryPct,
                vehicleId = first?.vehicleId,
            )
        }
        val endPseudo = tripEnd?.takeIf { it.hasData }?.let { anchor ->
            ChargingSession(
                id = TRIP_END_ANCHOR_ID,
                sessionStart = maxOf(
                    anchor.atMillis ?: Long.MIN_VALUE,
                    last?.sessionStart?.plus(1) ?: (anchor.atMillis ?: 0L),
                ),
                odometerKm = anchor.odometerKm,
                batteryStartPct = anchor.batteryPct,
                vehicleId = last?.vehicleId,
            )
        }

        if (first != null && startPseudo != null) {
            val bound = tripStart?.atMillis?.takeIf { it < first.sessionStart }
            if (bound != null && hasInterleavedCharge(allSessions, inScopeIds, bound, first.sessionStart)) {
                excluded += ExcludedPair(
                    startPseudo, first,
                    "Another charge happened between the trip start and this session — add it to the trip to measure this drive",
                )
            } else {
                record(startPseudo, first, measurePair(startPseudo, first, vehicle))
            }
        }
        if (last != null && endPseudo != null) {
            val bound = tripEnd?.atMillis?.takeIf { it > last.sessionStart }
            if (bound != null && hasInterleavedCharge(allSessions, inScopeIds, last.sessionStart, bound)) {
                excluded += ExcludedPair(
                    last, endPseudo,
                    "Another charge happened between this session and the trip end — add it to the trip to measure this drive",
                )
            } else {
                record(last, endPseudo, measurePair(last, endPseudo, vehicle))
            }
        }
        if (first == null && startPseudo != null && endPseudo != null) {
            // A trip with boundary readings but no charging stops at all:
            // the whole trip is one leg. When both trip dates are known,
            // a charge logged inside that window (necessarily out of
            // scope — the trip has no sessions) invalidates it.
            val lo = tripStart?.atMillis
            val hi = tripEnd?.atMillis
            if (lo != null && hi != null && lo < hi &&
                hasInterleavedCharge(allSessions, inScopeIds, lo, hi)
            ) {
                excluded += ExcludedPair(
                    startPseudo, endPseudo,
                    "Another charge happened during the trip — add it to the trip to measure this drive",
                )
            } else {
                record(startPseudo, endPseudo, measurePair(startPseudo, endPseudo, vehicle))
            }
        }

        return EfficiencyReport(legs, excluded)
    }

    private sealed interface PairResult {
        data class Leg(val leg: DrivingLeg) : PairResult
        data class Excluded(val reason: String) : PairResult
    }

    /** The measurement rules shared by real session pairs and anchor legs:
     *  odometer on both ends and increasing, battery on both ends and
     *  dropping, capacity set. Continuity/interleaving is the caller's
     *  responsibility. */
    private fun measurePair(
        prev: ChargingSession,
        curr: ChargingSession,
        vehicle: Vehicle?,
    ): PairResult {
        val prevOdo = prev.odometerKm
        val currOdo = curr.odometerKm
        if (prevOdo == null || currOdo == null) {
            return PairResult.Excluded("Add odometer on both sessions")
        }
        val distance = currOdo - prevOdo
        if (distance <= 0) {
            return PairResult.Excluded("Odometer didn't increase")
        }

        val capacity = vehicle?.batteryCapacityKwh
        if (capacity == null || capacity <= 0) {
            return PairResult.Excluded("Set the vehicle's battery capacity to compute")
        }
        val endPct = prev.batteryEndPct
        val startPct = curr.batteryStartPct
        if (endPct == null || startPct == null) {
            return PairResult.Excluded("Need end battery % on the prior session and start battery % on this one")
        }
        val delta = endPct - startPct
        if (delta <= 0) {
            return PairResult.Excluded("Battery didn't drop between sessions")
        }
        val energy = delta * capacity / 100.0

        return PairResult.Leg(
            DrivingLeg(
                from = prev,
                to = curr,
                distanceKm = distance,
                energyUsedKwh = energy,
                kmPerKwh = distance / energy,
            ),
        )
    }

    /** Deterministic timeline order: sessionStart, then id. Date-only
     *  imports stamp every row on a day with the same midnight timestamp;
     *  a bare sessionStart sort is stable, so those rows would keep the
     *  caller's (newest-first) query order and pair differently from the
     *  rest of the app's id-tie-broken timeline. */
    private val TIMELINE_ORDER = compareBy<ChargingSession>({ it.sessionStart }, { it.id })

    /** An out-of-scope charge falling between [prev] and [curr] in timeline
     *  order. Compared with [TIMELINE_ORDER] rather than raw timestamps so
     *  a same-timestamp charge (date-only imports again) still registers
     *  instead of slipping through strict time comparisons. */
    private fun hasInterleavedCharge(
        allSessions: List<ChargingSession>,
        inScopeIds: Set<Long>,
        prev: ChargingSession,
        curr: ChargingSession,
    ): Boolean = allSessions.any {
        it.id !in inScopeIds &&
            TIMELINE_ORDER.compare(prev, it) < 0 &&
            TIMELINE_ORDER.compare(it, curr) < 0
    }

    /** Time-window variant for the trip-anchor checks, whose bounds are
     *  trip dates rather than sessions. */
    private fun hasInterleavedCharge(
        allSessions: List<ChargingSession>,
        inScopeIds: Set<Long>,
        afterMillis: Long,
        beforeMillis: Long,
    ): Boolean = allSessions.any {
        it.id !in inScopeIds &&
            it.sessionStart > afterMillis &&
            it.sessionStart < beforeMillis
    }

    private fun isContinuous(prev: ChargingSession, curr: ChargingSession): Boolean {
        val sameTrip = prev.tripId != null && prev.tripId == curr.tripId
        return sameTrip || curr.continuesPrevious
    }
}
