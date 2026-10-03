package com.evsct.app.util

import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.data.entity.Trip
import com.evsct.app.data.entity.Vehicle

/**
 * A car's lifetime driving efficiency, from every drive the app can measure
 * on it. Most come from its charging sessions (see
 * [EfficiencyAnalysis.analyze] and [carTimeline]). The rest only its trips'
 * start and end readings can measure: from home to a trip's first charge,
 * from its last charge home, and the whole of a trip with no charging.
 * Those are taken from [TripReport], exactly as the trip itself measures
 * them.
 */
object VehicleEfficiency {

    /**
     * [allSessions] is the whole log and [trips] every trip; only
     * [vehicle]'s own sessions and trips count. A trip drive over road a
     * session drive already covers is left out, so no kilometre counts
     * twice: a `continuesPrevious` flag can stretch a session pair across
     * a trip's start or end, or across a whole trip.
     *
     * [EfficiencyReport.excluded] holds the session pairs that couldn't be
     * measured; a trip's own unmeasured drives are its detail screen's to
     * explain.
     */
    fun of(
        vehicle: Vehicle,
        allSessions: List<ChargingSession>,
        trips: List<Trip>,
    ): EfficiencyReport {
        val carTrips = trips.filter { it.vehicleId == vehicle.id }.sortedBy { it.id }
        val carTripIds = carTrips.mapTo(HashSet()) { it.id }
        val sessionDrives = EfficiencyAnalysis.analyze(carTimeline(vehicle, allSessions, carTripIds), vehicle)
        val legs = sessionDrives.legs.toMutableList()
        val sessionsByTrip = allSessions.groupBy { it.tripId }
        for (trip in carTrips) {
            val report = TripReport.of(trip, sessionsByTrip[trip.id].orEmpty(), allSessions, listOf(vehicle))
            for (leg in report.legs) {
                if (leg.isTripStartOrEnd() && legs.none { it.overlaps(leg) }) legs += leg
            }
        }
        return EfficiencyReport(legs, sessionDrives.excluded)
    }

    /**
     * The charges the car's session drives run between: its own sessions,
     * plus any session with no vehicle tagged to one of its trips. A trip
     * is one car, so such a session can only have been this one (see
     * [TripReport.isOnTripCar]). Left out, the car's sessions on either
     * side of it would pair as one drive with no charge in between, and
     * the battery it added would read as energy the drive never used.
     *
     * Such a session's own `continuesPrevious` flag is dropped: it was set
     * against the previous session with no vehicle, not this car's.
     */
    private fun carTimeline(
        vehicle: Vehicle,
        allSessions: List<ChargingSession>,
        carTripIds: Set<Long>,
    ): List<ChargingSession> = allSessions.mapNotNull { session ->
        when {
            session.vehicleId == vehicle.id -> session
            session.vehicleId == null && session.tripId in carTripIds -> session.copy(continuesPrevious = false)
            else -> null
        }
    }

    /** A drive from a trip's start reading or to its end reading — the
     *  ones no session pair can measure. */
    private fun DrivingLeg.isTripStartOrEnd(): Boolean =
        from.id == EfficiencyAnalysis.TRIP_START_ANCHOR_ID || to.id == EfficiencyAnalysis.TRIP_END_ANCHOR_ID

    /** Two drives of one car cover the same road when their odometer
     *  ranges overlap; a measured leg always has both readings. */
    private fun DrivingLeg.overlaps(other: DrivingLeg): Boolean {
        val start = from.odometerKm ?: return false
        val end = to.odometerKm ?: return false
        val otherStart = other.from.odometerKm ?: return false
        val otherEnd = other.to.odometerKm ?: return false
        return start < otherEnd && otherStart < end
    }
}
