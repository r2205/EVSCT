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
 */
object TripDistance {

    fun km(trip: Trip, sessions: List<ChargingSession>): Double {
        val start = trip.startOdometerKm
        val end = trip.endOdometerKm
        if (start != null && end != null && end >= start) return end - start

        val perCar = if (trip.vehicleId != null) {
            listOf(sessions.filter { TripReport.isOnTripCar(trip, it) })
        } else {
            sessions.groupBy { it.vehicleId }.values
        }
        return perCar.sumOf { carSessions ->
            val odometers = carSessions.mapNotNull { it.odometerKm }
            if (odometers.size < 2) 0.0 else odometers.max() - odometers.min()
        }
    }
}
