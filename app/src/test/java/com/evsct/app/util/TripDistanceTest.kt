package com.evsct.app.util

import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.data.entity.Trip
import org.junit.Test
import kotlin.test.assertEquals

class TripDistanceTest {

    @Test
    fun `trip odometer readings win over session readings`() {
        val trip = trip(startOdo = 10_000.0, endOdo = 10_650.0)
        val sessions = listOf(
            session(id = 1, odo = 10_100.0, vehicleId = 1),
            session(id = 2, odo = 10_400.0, vehicleId = 1),
        )
        assertEquals(650.0, TripDistance.km(trip, sessions), 1e-9)
    }

    @Test
    fun `without trip readings the session spread is used`() {
        val sessions = listOf(
            session(id = 1, odo = 10_100.0, vehicleId = 1),
            session(id = 2, odo = 10_250.0, vehicleId = 1),
            session(id = 3, odo = 10_400.0, vehicleId = 1),
        )
        assertEquals(300.0, TripDistance.km(trip(), sessions), 1e-9)
    }

    @Test
    fun `a session from another car does not stretch the trip across both odometers`() {
        // The bug: max − min over every session read 45,300 − 12,000 as
        // a 33,300 km trip. The stray session has no partner on its car,
        // so it adds nothing.
        val sessions = listOf(
            session(id = 1, odo = 45_000.0, vehicleId = 1),
            session(id = 2, odo = 45_300.0, vehicleId = 1),
            session(id = 3, odo = 12_000.0, vehicleId = 2),
        )
        assertEquals(300.0, TripDistance.km(trip(), sessions), 1e-9)
    }

    @Test
    fun `each car contributes its own spread`() {
        val sessions = listOf(
            session(id = 1, odo = 45_000.0, vehicleId = 1),
            session(id = 2, odo = 45_300.0, vehicleId = 1),
            session(id = 3, odo = 12_000.0, vehicleId = 2),
            session(id = 4, odo = 12_120.0, vehicleId = 2),
        )
        assertEquals(420.0, TripDistance.km(trip(), sessions), 1e-9)
    }

    @Test
    fun `reversed trip readings fall back to the sessions`() {
        // The edit dialog blocks start > end, but a restored or imported
        // trip can still carry one; a negative distance is never right.
        val trip = trip(startOdo = 10_650.0, endOdo = 10_000.0)
        val sessions = listOf(
            session(id = 1, odo = 10_100.0, vehicleId = 1),
            session(id = 2, odo = 10_400.0, vehicleId = 1),
        )
        assertEquals(300.0, TripDistance.km(trip, sessions), 1e-9)
    }

    @Test
    fun `no readings at all is zero`() {
        assertEquals(0.0, TripDistance.km(trip(), emptyList()), 1e-9)
        assertEquals(
            0.0,
            TripDistance.km(trip(), listOf(session(id = 1, odo = 10_000.0, vehicleId = 1))),
            1e-9,
        )
    }

    private fun trip(startOdo: Double? = null, endOdo: Double? = null) = Trip(
        id = 7,
        name = "Trip",
        startOdometerKm = startOdo,
        endOdometerKm = endOdo,
    )

    private fun session(id: Long, odo: Double?, vehicleId: Long?) = ChargingSession(
        id = id,
        sessionStart = id,
        odometerKm = odo,
        tripId = 7,
        vehicleId = vehicleId,
    )
}
