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
 * Otherwise the distance comes from the session odometers, one car at a
 * time. Two cars' odometers have nothing to do with each other, so taking
 * the highest minus the lowest across every session let one session logged
 * on the other car stretch the trip across the whole gap between them — a
 * 45,000 km car and a 12,000 km car read as a 33,000 km trip. Each car now
 * contributes the spread of its own readings, and a lone stray session
 * contributes nothing.
 */
object TripDistance {

    fun km(trip: Trip, sessions: List<ChargingSession>): Double {
        val start = trip.startOdometerKm
        val end = trip.endOdometerKm
        if (start != null && end != null && end >= start) return end - start

        return sessions.groupBy { it.vehicleId }.values.sumOf { carSessions ->
            val odometers = carSessions.mapNotNull { it.odometerKm }
            if (odometers.size < 2) 0.0 else odometers.max() - odometers.min()
        }
    }
}
