package com.evsct.app.util

import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.data.entity.Trip
import com.evsct.app.data.entity.TripWithStats
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LongestTripTest {

    // Timestamps stand in for years directly: 2025_000 is in 2025.
    private val yearOf: (Long) -> Int = { (it / 1_000).toInt() }

    @Test
    fun `a trip counts in a year it charged in, with its whole distance`() {
        val trip = stats(id = 1, km = 600.0, sessions = 3)
        val sessions = listOf(session(tripId = 1, t = 2025_100))

        assertEquals(trip, LongestTrip.inYear(listOf(trip), sessions, 2025, yearOf))
        assertNull(LongestTrip.inYear(listOf(trip), sessions, 2024, yearOf))
    }

    @Test
    fun `a trip with no charging counts in the year of its own date`() {
        val noCharging = stats(id = 2, km = 900.0, sessions = 0, startDate = 2025_050)
        val charged = stats(id = 1, km = 600.0, sessions = 1)
        val sessions = listOf(session(tripId = 1, t = 2025_100))

        assertEquals(noCharging, LongestTrip.inYear(listOf(charged, noCharging), sessions, 2025, yearOf))
        assertEquals(null, LongestTrip.inYear(listOf(noCharging), emptyList(), 2024, yearOf))
    }

    @Test
    fun `without dates a trip with no charging falls back to when it was created`() {
        val undated = stats(id = 2, km = 900.0, sessions = 0, createdAt = 2024_300)

        assertEquals(undated, LongestTrip.inYear(listOf(undated), emptyList(), 2024, yearOf))
        assertNull(LongestTrip.inYear(listOf(undated), emptyList(), 2025, yearOf))
    }

    @Test
    fun `a trip with no distance never wins`() {
        val noDistance = stats(id = 1, km = 0.0, sessions = 0, startDate = 2025_050)

        assertNull(LongestTrip.inYear(listOf(noDistance), emptyList(), 2025, yearOf))
    }

    private fun stats(
        id: Long,
        km: Double,
        sessions: Int,
        startDate: Long? = null,
        createdAt: Long = 0,
    ) = TripWithStats(
        trip = Trip(id = id, name = "Trip $id", startDate = startDate, createdAt = createdAt),
        sessionCount = sessions,
        totalCostByCurrency = CurrencyTotals(emptyMap()),
        totalEnergyKwh = 0.0,
        totalDistanceKm = km,
    )

    private fun session(tripId: Long, t: Long) = ChargingSession(
        id = t,
        sessionStart = t,
        tripId = tripId,
    )
}
