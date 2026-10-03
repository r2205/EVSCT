package com.evsct.app.util

import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.data.entity.Vehicle
import java.util.Calendar

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
 * [atMillis] is the trip's start/end date when set: the local midnight
 * starting that day. It bounds the interleave check by whole days (a charge
 * logged between the anchor and its session invalidates the pairing), and
 * [odometerKm] places a charge on the boundary day itself before or after
 * the trip. Null means no time bound — entering the trip-level readings is
 * itself the attestation.
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
     * drive with no charging stops at all). An untagged charge on one of
     * those legs blocks it like one between two sessions; on the trip's
     * start or end day, its odometer says which side of the trip it fell
     * (see strayCharges). The caller is responsible for only passing
     * anchors along with sessions on the car they describe — [TripReport]
     * passes the trip car's own.
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
        val start = tripStart?.takeIf { it.hasData }
        val end = tripEnd?.takeIf { it.hasData }
        val startPseudo = start?.let { anchor ->
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
        val endPseudo = end?.let { anchor ->
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

        // A trip-boundary leg is only as good as the claim that nothing
        // charged the car on that stretch, and the trip dates bounding it
        // are whole days. See strayCharges for how a charge on the start or
        // end day is placed.
        if (first != null && start != null && startPseudo != null) {
            val strays = strayCharges(allSessions, inScopeIds, start, null, after = null, before = first)
            if (strays.isNotEmpty()) {
                excluded += ExcludedPair(
                    startPseudo, first,
                    strayReason(
                        strays, start,
                        "Another charge happened between the trip start and this session — add it to the trip to measure this drive",
                    ),
                )
            } else {
                record(startPseudo, first, measurePair(startPseudo, first, vehicle))
            }
        }
        if (last != null && end != null && endPseudo != null) {
            val strays = strayCharges(allSessions, inScopeIds, null, end, after = last, before = null)
            if (strays.isNotEmpty()) {
                excluded += ExcludedPair(
                    last, endPseudo,
                    "Another charge happened between this session and the trip end — add it to the trip to measure this drive",
                )
            } else {
                record(last, endPseudo, measurePair(last, endPseudo, vehicle))
            }
        }
        if (first == null && start != null && end != null && startPseudo != null && endPseudo != null) {
            // A trip with boundary readings but no charging stops at all:
            // the whole trip is one leg, and any charge inside it
            // (necessarily out of scope — the trip has no sessions)
            // invalidates it.
            val strays = strayCharges(allSessions, inScopeIds, start, end, after = null, before = null)
            if (strays.isNotEmpty()) {
                excluded += ExcludedPair(
                    startPseudo, endPseudo,
                    strayReason(
                        strays, start,
                        "Another charge happened during the trip — add it to the trip to measure this drive",
                    ),
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
            return PairResult.Excluded(
                missingReason(prev, curr, prevOdo == null, currOdo == null, Reading.ODOMETER),
            )
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
            return PairResult.Excluded(
                missingReason(prev, curr, endPct == null, startPct == null, Reading.BATTERY),
            )
        }
        val delta = endPct - startPct
        if (delta <= 0) {
            return PairResult.Excluded(
                if (isAnchor(prev) || isAnchor(curr)) "Battery didn't drop over this drive"
                else "Battery didn't drop between sessions",
            )
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

    private fun isAnchor(s: ChargingSession): Boolean =
        s.id == TRIP_START_ANCHOR_ID || s.id == TRIP_END_ANCHOR_ID

    /** A reading a leg needs at each end: what it's called on the trip, on
     *  the session a leg leaves from (its end reading), on the session it
     *  arrives at (its start reading), and the long-standing wording for a
     *  pair of sessions. */
    private enum class Reading(
        val onTrip: String,
        val leavingSession: String,
        val arrivingSession: String,
        val betweenSessions: String,
    ) {
        ODOMETER("odometer", "odometer", "odometer", "Add odometer on both sessions"),
        BATTERY(
            "battery %", "end battery %", "start battery %",
            "Need end battery % on the prior session and start battery % on this one",
        ),
    }

    /** What to fill in for a leg missing [reading] at one or both ends.
     *  Between two sessions the wording is unchanged; a trip start or end
     *  names the trip's own field — the one to fill in with Edit trip —
     *  instead of a session that doesn't exist. */
    private fun missingReason(
        prev: ChargingSession,
        curr: ChargingSession,
        prevMissing: Boolean,
        currMissing: Boolean,
        reading: Reading,
    ): String {
        val fromTripStart = prev.id == TRIP_START_ANCHOR_ID
        val toTripEnd = curr.id == TRIP_END_ANCHOR_ID
        if (!fromTripStart && !toTripEnd) return reading.betweenSessions
        if (fromTripStart && toTripEnd) {
            val ends = listOfNotNull("start".takeIf { prevMissing }, "end".takeIf { currMissing })
            return "Add the trip's ${ends.joinToString(" and ")} ${reading.onTrip}"
        }
        val needed = listOfNotNull(
            when {
                !prevMissing -> null
                fromTripStart -> "the trip's start ${reading.onTrip}"
                else -> "this session's ${reading.leavingSession}"
            },
            when {
                !currMissing -> null
                toTripEnd -> "the trip's end ${reading.onTrip}"
                else -> "this session's ${reading.arrivingSession}"
            },
        )
        return "Add ${needed.joinToString(" and ")}"
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

    /**
     * Out-of-scope charges that may have happened on a trip-boundary leg,
     * which runs from the trip's [start] (or the session [after]) to the
     * session [before] (or the trip's [end]). Session bounds compare in
     * [TIMELINE_ORDER], so a same-timestamp charge (date-only imports)
     * still registers, exactly as in the session-pair check.
     *
     * Trip dates are whole days stored at local midnight: they say which day
     * the trip started or ended, not when. So every charge on a boundary day
     * is a candidate — including one stamped at that very midnight, as a
     * CSV row without a time is — and the trip's own odometer readings
     * place it. At or
     * below the start odometer, it was charged before the trip left (the
     * top-up at home that morning used to wipe out the first drive's
     * measurement). At or above the end odometer, it came after the trip
     * ended (plugging in at home on arrival). Below the end odometer on the
     * end day, it was on the drive home, which the end date at midnight used
     * to miss. A charge with no odometer reading, or a trip without one, is
     * placed by date as before: a start-day charge counts as part of the
     * trip, and an end-day one as after it.
     *
     * A boundary with no session and no date is unbounded, so nothing counts:
     * entering the trip-level readings is then itself the attestation.
     */
    private fun strayCharges(
        allSessions: List<ChargingSession>,
        inScopeIds: Set<Long>,
        start: TripAnchor?,
        end: TripAnchor?,
        after: ChargingSession?,
        before: ChargingSession?,
    ): List<ChargingSession> {
        val startDate = start?.atMillis
        val endDate = end?.atMillis
        val pastLowerBound: (ChargingSession) -> Boolean = when {
            after != null -> { c -> TIMELINE_ORDER.compare(c, after) > 0 }
            startDate != null -> { c -> c.sessionStart >= startDate }
            else -> return emptyList()
        }
        val beforeUpperBound: (ChargingSession) -> Boolean = when {
            before != null -> { c -> TIMELINE_ORDER.compare(c, before) < 0 }
            endDate != null -> dayAfter(endDate).let { endDayOver -> { c -> c.sessionStart < endDayOver } }
            else -> return emptyList()
        }
        return allSessions.filter { c ->
            c.id !in inScopeIds &&
                pastLowerBound(c) &&
                beforeUpperBound(c) &&
                !(start != null && chargedBeforeTripStart(c, start)) &&
                !(end != null && chargedAfterTripEnd(c, end))
        }
    }

    /** [charge] happened before the trip left: its odometer is at or below
     *  the trip's start reading. Unknowable without both readings. */
    private fun chargedBeforeTripStart(charge: ChargingSession, start: TripAnchor): Boolean {
        val odometer = charge.odometerKm ?: return false
        val startOdometer = start.odometerKm ?: return false
        return odometer <= startOdometer
    }

    /** [charge] happened after the trip ended: its odometer is at or above
     *  the trip's end reading, or, without both readings, it's on the end
     *  day or later. */
    private fun chargedAfterTripEnd(charge: ChargingSession, end: TripAnchor): Boolean {
        val odometer = charge.odometerKm
        val endOdometer = end.odometerKm
        if (odometer != null && endOdometer != null) return odometer >= endOdometer
        val endDate = end.atMillis ?: return false
        return charge.sessionStart >= endDate
    }

    /** Why a leg from the trip's [start] can't be measured past [strays].
     *  When the only doubt is start-day charges with no odometer reading,
     *  most likely the top-up at home before leaving, say what settles it
     *  rather than asking for the charge to be added to the trip. */
    private fun strayReason(strays: List<ChargingSession>, start: TripAnchor, otherwise: String): String {
        val startDate = start.atMillis ?: return otherwise
        if (start.odometerKm == null) return otherwise
        val startDayOver = dayAfter(startDate)
        val onlyUnplacedStartDay = strays.all { it.odometerKm == null && it.sessionStart < startDayOver }
        return if (onlyUnplacedStartDay) {
            "A charge on the trip's start day has no odometer reading — add one to show whether it was before you left"
        } else {
            otherwise
        }
    }

    /** The local midnight after [dayStart], when a trip date's day is over.
     *  Calendar arithmetic, so a DST day's 23 or 25 hours come out right. */
    private fun dayAfter(dayStart: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = dayStart
            add(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis

    private fun isContinuous(prev: ChargingSession, curr: ChargingSession): Boolean {
        val sameTrip = prev.tripId != null && prev.tripId == curr.tripId
        return sameTrip || curr.continuesPrevious
    }
}
