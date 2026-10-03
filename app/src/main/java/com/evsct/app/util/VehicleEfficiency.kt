package com.evsct.app.util

import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.data.entity.Trip
import com.evsct.app.data.entity.Vehicle

/**
 * A car's lifetime driving efficiency, from every drive the app can measure
 * on it. Most come from its own charging sessions (see
 * [EfficiencyAnalysis.analyze]). The rest only its trips' start and end
 * readings can measure: from home to a trip's first charge, from its last
 * charge home, and the whole of a trip with no charging. Those are taken
 * from [TripReport], exactly as the trip itself measures them.
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
        val sessionDrives = EfficiencyAnalysis.analyze(allSessions.filter { it.vehicleId == vehicle.id }, vehicle)
        val legs = sessionDrives.legs.toMutableList()
        val sessionsByTrip = allSessions.groupBy { it.tripId }
        for (trip in trips.filter { it.vehicleId == vehicle.id }.sortedBy { it.id }) {
            val report = TripReport.of(trip, sessionsByTrip[trip.id].orEmpty(), allSessions, listOf(vehicle))
            for (leg in report.legs) {
                if (leg.isTripStartOrEnd() && legs.none { it.overlaps(leg) }) legs += leg
            }
        }
        return EfficiencyReport(legs, sessionDrives.excluded)
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
