package com.evsct.app.util

import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.data.entity.Trip

/**
 * A trip's total distance, shared by the trip list, the trip detail header
 * and the year recap's longest trip so they can't disagree.
 *
 * Both trip-level odometer readings win when they're filled in: end − start
 * covers the drive to the first stop and home from the last, which no pair
 * of session readings can (and free home charging the log never sees).
 *
 * Otherwise the distance comes from the session odometers of the trip's own
 * car — see [TripReport.isOnTripCar]. Another car's odometer has nothing to
 * do with this trip: taking the highest minus the lowest across every
 * session let one session logged on the other car stretch the trip across
 * the whole gap between two odometers (a 45,000 km car and a 12,000 km car
 * read as a 33,000 km trip). A trip with no car yet has none to prefer, so
 * each car its sessions name contributes the spread of its own readings.
 *
 * A trip with a car and only one of its own readings still uses that one:
 * the start reading extends the spread back to where the trip left, the end
 * reading forward to where it finished. [EfficiencyAnalysis] measures the
 * drive from a lone start reading to the first stop (or from the last stop
 * to a lone end reading), so leaving it out here made the measured drives
 * longer than the trip, inflating $/km and hiding the "partial" footnote.
 * A lone reading on the wrong side of the sessions' readings is a typo and
 * is ignored, as is a reversed start/end pair.
 */
object TripDistance {

    fun km(trip: Trip, sessions: List<ChargingSession>): Double {
        val start = trip.startOdometerKm
        val end = trip.endOdometerKm
        if (start != null && end != null && end >= start) return end - start

        if (trip.vehicleId != null) {
            val odometers = sessions.filter { TripReport.isOnTripCar(trip, it) }.mapNotNull { it.odometerKm }
            // Both set (so, reaching here, reversed): neither can be trusted.
            val lone = start == null || end == null
            val readings = odometers +
                listOfNotNull(
                    start?.takeIf { lone && it <= (odometers.minOrNull() ?: it) },
                    end?.takeIf { lone && it >= (odometers.maxOrNull() ?: it) },
                )
            return if (readings.size < 2) 0.0 else readings.max() - readings.min()
        }

        return sessions.groupBy { it.vehicleId }.values.sumOf { carSessions ->
            val odometers = carSessions.mapNotNull { it.odometerKm }
            if (odometers.size < 2) 0.0 else odometers.max() - odometers.min()
        }
    }
}
