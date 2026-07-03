package com.evsct.app.util

import com.evsct.app.data.entity.ChargingSession
import org.junit.Test
import kotlin.test.assertEquals

class OdometerDistanceTest {

    // A 100-unit-wide window keeps the fractions readable.
    private val windowStart = 1_000L
    private val windowEnd = 1_100L

    @Test
    fun `interval fully inside the window counts in full`() {
        val d = OdometerDistance.inWindow(
            listOf(
                session(t = 1_010, odo = 100.0),
                session(t = 1_050, odo = 380.0),
            ),
            windowStart, windowEnd,
        )
        assertEquals(280.0, d, 1e-9)
    }

    @Test
    fun `boundary interval is prorated by time overlap`() {
        // Charge at t=950, next at t=1050: half of the interval is inside
        // the window, so half the 100 km is credited here.
        val d = OdometerDistance.inWindow(
            listOf(
                session(t = 950, odo = 1_000.0),
                session(t = 1_050, odo = 1_100.0),
            ),
            windowStart, windowEnd,
        )
        assertEquals(50.0, d, 1e-9)
    }

    @Test
    fun `adjacent windows tile - shares sum to the full delta`() {
        val sessions = listOf(
            session(t = 950, odo = 1_000.0),
            session(t = 1_050, odo = 1_100.0),
        )
        val before = OdometerDistance.inWindow(sessions, 900, 1_000)
        val inside = OdometerDistance.inWindow(sessions, 1_000, 1_100)
        assertEquals(100.0, before + inside, 1e-9)
    }

    @Test
    fun `long logging gap credits the window only its share`() {
        // 1000-unit gap ending at t=1050: 50 units of it fall inside the
        // window → 5% of the 10,000 km. The old end-bucket rule dumped
        // all 10,000 km into this window.
        val d = OdometerDistance.inWindow(
            listOf(
                session(t = 50, odo = 0.0),
                session(t = 1_050, odo = 10_000.0),
            ),
            windowStart, windowEnd,
        )
        assertEquals(500.0, d, 1e-9)
    }

    @Test
    fun `interval spanning the whole window credits the window's slice`() {
        // No charges inside the window at all — but driving passed through
        // it. The old rule credited zero.
        val d = OdometerDistance.inWindow(
            listOf(
                session(t = 800, odo = 0.0),
                session(t = 1_200, odo = 400.0),
            ),
            windowStart, windowEnd,
        )
        assertEquals(100.0, d, 1e-9)
    }

    @Test
    fun `interval entirely outside the window counts nothing`() {
        val d = OdometerDistance.inWindow(
            listOf(
                session(t = 500, odo = 100.0),
                session(t = 900, odo = 300.0),
            ),
            windowStart, windowEnd,
        )
        assertEquals(0.0, d, 1e-9)
    }

    @Test
    fun `negative delta and missing readings are skipped`() {
        val d = OdometerDistance.inWindow(
            listOf(
                session(t = 1_010, odo = 500.0),
                session(t = 1_020, odo = 400.0),   // rollback — skipped
                session(t = 1_030, odo = null),    // missing — both pairs skipped
                session(t = 1_040, odo = 450.0),
            ),
            windowStart, windowEnd,
        )
        assertEquals(0.0, d, 1e-9)
    }

    @Test
    fun `vehicles are walked independently`() {
        // Interleaved in time, but each vehicle's own odometer chain is
        // consistent. A cross-vehicle pairing would produce nonsense.
        val d = OdometerDistance.inWindow(
            listOf(
                session(t = 1_010, odo = 1_000.0, vehicleId = 1),
                session(t = 1_020, odo = 50_000.0, vehicleId = 2),
                session(t = 1_030, odo = 1_100.0, vehicleId = 1),
                session(t = 1_040, odo = 50_200.0, vehicleId = 2),
            ),
            windowStart, windowEnd,
        )
        assertEquals(100.0 + 200.0, d, 1e-9)
    }

    @Test
    fun `same-timestamp pair inside the window counts in full`() {
        val d = OdometerDistance.inWindow(
            listOf(
                session(t = 1_050, odo = 100.0),
                session(t = 1_050, odo = 150.0),
            ),
            windowStart, windowEnd,
        )
        assertEquals(50.0, d, 1e-9)
    }

    private fun session(
        t: Long,
        odo: Double?,
        vehicleId: Long = 1,
    ): ChargingSession = ChargingSession(
        sessionStart = t,
        odometerKm = odo,
        vehicleId = vehicleId,
    )
}
