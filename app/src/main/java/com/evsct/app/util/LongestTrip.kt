package com.evsct.app.util

import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.data.entity.TripWithStats

/** The year recap's "Longest trip" pick. */
object LongestTrip {

    /**
     * The trip with the greatest distance among [trips] that belong to
     * [year]. A trip belongs to a year it has a charging session in — the
     * whole trip's distance counts, not just the in-year part — or, having no
     * sessions at all, the year of its own date: the start date, else the
     * end date, else when it was created (the same stand-in the Trips list
     * sorts by). Before trips could be measured without sessions, a trip
     * with no charging stops could never win, however far it went.
     *
     * [sessions] should be the whole log: a trip already scoped to one car
     * still counts a session of its own that names no vehicle.
     */
    fun inYear(
        trips: List<TripWithStats>,
        sessions: List<ChargingSession>,
        year: Int,
        yearOf: (Long) -> Int,
    ): TripWithStats? {
        val tripsWithSessionInYear = sessions
            .filter { yearOf(it.sessionStart) == year }
            .mapNotNullTo(mutableSetOf()) { it.tripId }
        return trips
            .filter { tws ->
                val trip = tws.trip
                tws.totalDistanceKm > 0 && (
                    trip.id in tripsWithSessionInYear ||
                        (tws.sessionCount == 0 &&
                            yearOf(trip.startDate ?: trip.endDate ?: trip.createdAt) == year)
                    )
            }
            .maxByOrNull { it.totalDistanceKm }
    }
}
