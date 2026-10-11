package com.evsct.app.ui.sessions

import com.evsct.app.data.entity.ChargingSession
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OdometerNeighboursTest {

    private fun session(id: Long, t: Long, odo: Double?, vehicleId: Long? = 1) =
        ChargingSession(id = id, sessionStart = t, odometerKm = odo, vehicleId = vehicleId)

    @Test
    fun `a session in the middle of the log gets both neighbours`() {
        // The backfill case: the previous-only check never saw the next one.
        val log = listOf(session(1, 100, 10_000.0), session(2, 300, 10_500.0))

        val (previous, next) = odometerNeighbours(log, vehicleId = 1, editingId = 0, sessionStart = 200)

        assertEquals(1L, previous?.id)
        assertEquals(2L, next?.id)
    }

    @Test
    fun `sessions without an odometer are skipped, not taken as the neighbour`() {
        val log = listOf(
            session(1, 100, 10_000.0),
            session(2, 150, null),
            session(3, 250, null),
            session(4, 300, 10_500.0),
        )

        val (previous, next) = odometerNeighbours(log, vehicleId = 1, editingId = 0, sessionStart = 200)

        assertEquals(1L, previous?.id)
        assertEquals(4L, next?.id)
    }

    @Test
    fun `other cars and the session being edited are ignored`() {
        val log = listOf(
            session(1, 100, 10_000.0),
            session(2, 150, 45_000.0, vehicleId = 2),
            session(5, 200, 10_200.0),
            session(3, 300, 10_500.0),
        )

        val (previous, next) = odometerNeighbours(log, vehicleId = 1, editingId = 5, sessionStart = 200)

        assertEquals(1L, previous?.id)
        assertEquals(3L, next?.id)
    }

    @Test
    fun `no vehicle means nothing to compare against`() {
        val log = listOf(session(1, 100, 10_000.0, vehicleId = null))

        val (previous, next) = odometerNeighbours(log, vehicleId = null, editingId = 0, sessionStart = 200)

        assertNull(previous)
        assertNull(next)
    }
}
