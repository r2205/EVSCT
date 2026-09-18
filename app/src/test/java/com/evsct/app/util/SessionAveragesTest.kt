package com.evsct.app.util

import com.evsct.app.data.entity.ChargingSession
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SessionAveragesTest {

    /* --- avgPowerKw --- */

    @Test
    fun `power ignores energy from sessions with no duration`() {
        // The bug this guards: ten 50 kWh charges with one recorded hour
        // used to read as 500 kW. Only the paired session counts.
        val sessions = List(9) { session(kwh = 50.0, secs = null) } +
            session(kwh = 50.0, secs = 3600)
        assertEquals(50.0, SessionAverages.avgPowerKw(sessions)!!, 1e-9)
    }

    @Test
    fun `power ignores duration from sessions with no energy`() {
        val sessions = listOf(
            session(kwh = null, secs = 7200),
            session(kwh = 30.0, secs = 3600),
        )
        assertEquals(30.0, SessionAverages.avgPowerKw(sessions)!!, 1e-9)
    }

    @Test
    fun `power is energy-weighted across paired sessions`() {
        val sessions = listOf(
            session(kwh = 60.0, secs = 3600),   // 60 kW for 1 h
            session(kwh = 10.0, secs = 3600),   // 10 kW for 1 h
        )
        assertEquals(35.0, SessionAverages.avgPowerKw(sessions)!!, 1e-9)
    }

    @Test
    fun `power treats a zero duration as unrecorded`() {
        assertNull(SessionAverages.avgPowerKw(listOf(session(kwh = 20.0, secs = 0))))
    }

    @Test
    fun `power is null when nothing pairs`() {
        assertNull(SessionAverages.avgPowerKw(emptyList()))
        assertNull(SessionAverages.avgPowerKw(listOf(session(kwh = 20.0, secs = null))))
    }

    /* --- avgEffectivePricePerKwh --- */

    @Test
    fun `price ignores cost from sessions with no energy`() {
        val sessions = listOf(
            session(cost = 40.0, kwh = null),
            session(cost = 10.0, kwh = 20.0),
        )
        assertEquals(0.5, SessionAverages.avgEffectivePricePerKwh(sessions)!!, 1e-9)
    }

    @Test
    fun `price ignores energy from sessions with no cost`() {
        val sessions = listOf(
            session(cost = null, kwh = 100.0),
            session(cost = 10.0, kwh = 20.0),
        )
        assertEquals(0.5, SessionAverages.avgEffectivePricePerKwh(sessions)!!, 1e-9)
    }

    @Test
    fun `free sessions with energy pull the price down`() {
        val sessions = listOf(
            session(cost = 0.0, kwh = 20.0),
            session(cost = 10.0, kwh = 20.0),
        )
        assertEquals(0.25, SessionAverages.avgEffectivePricePerKwh(sessions)!!, 1e-9)
    }

    @Test
    fun `price is null when nothing pairs`() {
        assertNull(SessionAverages.avgEffectivePricePerKwh(emptyList()))
        assertNull(SessionAverages.avgEffectivePricePerKwh(listOf(session(cost = 5.0, kwh = 0.0))))
    }

    private fun session(
        kwh: Double? = null,
        secs: Long? = null,
        cost: Double? = null,
    ) = ChargingSession(
        sessionStart = 0L,
        durationSeconds = secs,
        energyKwh = kwh,
        totalCost = cost,
    )
}
