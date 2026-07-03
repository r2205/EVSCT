package com.evsct.app.util

import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.data.entity.Vehicle
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EfficiencyAnalysisTest {

    private val vehicle = Vehicle(id = 1, name = "Test", batteryCapacityKwh = 100.0)

    @Test
    fun `empty list returns empty report`() {
        val report = EfficiencyAnalysis.analyze(emptyList(), vehicle)
        assertTrue(report.legs.isEmpty())
        assertNull(report.avgKmPerKwh)
    }

    @Test
    fun `single session has no legs`() {
        val report = EfficiencyAnalysis.analyze(listOf(session(id = 1)), vehicle)
        assertTrue(report.legs.isEmpty())
    }

    @Test
    fun `same trip pair with full data produces a leg`() {
        // Vehicle: 100 kWh battery.
        // Stop A: end at 80%. Stop B: start at 30%. Drive uses (80-30)% × 100 = 50 kWh.
        // Distance: 280 km. → 5.6 km/kWh.
        val report = EfficiencyAnalysis.analyze(
            listOf(
                session(id = 1, t = 0, odo = 1000.0, battEnd = 80, tripId = 7),
                session(id = 2, t = 1, odo = 1280.0, battStart = 30, tripId = 7),
            ),
            vehicle,
        )
        assertEquals(1, report.legs.size)
        val leg = report.legs.first()
        assertEquals(280.0, leg.distanceKm)
        assertEquals(50.0, leg.energyUsedKwh)
        assertEquals(5.6, leg.kmPerKwh, 0.0001)
        assertEquals(5.6, report.avgKmPerKwh!!, 0.0001)
    }

    @Test
    fun `cross-trip without continuesPrevious does not produce a leg`() {
        val report = EfficiencyAnalysis.analyze(
            listOf(
                session(id = 1, t = 0, odo = 1000.0, battEnd = 80, tripId = 1),
                session(id = 2, t = 1, odo = 1280.0, battStart = 30, tripId = 2),
            ),
            vehicle,
        )
        assertTrue(report.legs.isEmpty())
    }

    @Test
    fun `cross-trip with continuesPrevious flag produces a leg`() {
        val report = EfficiencyAnalysis.analyze(
            listOf(
                session(id = 1, t = 0, odo = 1000.0, battEnd = 80, tripId = 1),
                session(
                    id = 2, t = 1, odo = 1280.0, battStart = 30, tripId = 2,
                    continuesPrevious = true,
                ),
            ),
            vehicle,
        )
        assertEquals(1, report.legs.size)
    }

    @Test
    fun `null vehicle capacity excludes the leg`() {
        val report = EfficiencyAnalysis.analyze(
            listOf(
                session(id = 1, t = 0, odo = 1000.0, battEnd = 80, tripId = 7),
                session(id = 2, t = 1, odo = 1280.0, battStart = 30, tripId = 7),
            ),
            vehicle.copy(batteryCapacityKwh = null),
        )
        assertTrue(report.legs.isEmpty())
    }

    @Test
    fun `missing battery on either side excludes the leg`() {
        val noEnd = EfficiencyAnalysis.analyze(
            listOf(
                session(id = 1, t = 0, odo = 1000.0, battEnd = null, tripId = 7),
                session(id = 2, t = 1, odo = 1280.0, battStart = 30, tripId = 7),
            ),
            vehicle,
        )
        assertTrue(noEnd.legs.isEmpty())

        val noStart = EfficiencyAnalysis.analyze(
            listOf(
                session(id = 1, t = 0, odo = 1000.0, battEnd = 80, tripId = 7),
                session(id = 2, t = 1, odo = 1280.0, battStart = null, tripId = 7),
            ),
            vehicle,
        )
        assertTrue(noStart.legs.isEmpty())
    }

    @Test
    fun `battery going up between stops excludes the leg`() {
        // End A 50%, start B 60% — implies untracked charging happened. Drop.
        val report = EfficiencyAnalysis.analyze(
            listOf(
                session(id = 1, t = 0, odo = 1000.0, battEnd = 50, tripId = 7),
                session(id = 2, t = 1, odo = 1280.0, battStart = 60, tripId = 7),
            ),
            vehicle,
        )
        assertTrue(report.legs.isEmpty())
    }

    @Test
    fun `odometer going down excludes the leg`() {
        val report = EfficiencyAnalysis.analyze(
            listOf(
                session(id = 1, t = 0, odo = 1280.0, battEnd = 80, tripId = 7),
                session(id = 2, t = 1, odo = 1000.0, battStart = 30, tripId = 7),
            ),
            vehicle,
        )
        assertTrue(report.legs.isEmpty())
    }

    @Test
    fun `missing odometer on either side excludes the leg`() {
        val report = EfficiencyAnalysis.analyze(
            listOf(
                session(id = 1, t = 0, odo = null, battEnd = 80, tripId = 7),
                session(id = 2, t = 1, odo = 1280.0, battStart = 30, tripId = 7),
            ),
            vehicle,
        )
        assertTrue(report.legs.isEmpty())
    }

    @Test
    fun `three sessions in one trip produce two legs and a weighted average`() {
        val report = EfficiencyAnalysis.analyze(
            listOf(
                session(id = 1, t = 0, odo = 1000.0, battEnd = 80, tripId = 7),
                // First leg: end 80, start 30 → 50 kWh, 280 km → 5.6 km/kWh.
                session(id = 2, t = 1, odo = 1280.0, battStart = 30, battEnd = 80, tripId = 7),
                // Second leg: end 80, start 40 → 40 kWh, 200 km → 5.0 km/kWh.
                session(id = 3, t = 2, odo = 1480.0, battStart = 40, tripId = 7),
            ),
            vehicle,
        )
        assertEquals(2, report.legs.size)
        // Weighted avg = (280 + 200) / (50 + 40) = 480 / 90 ≈ 5.333.
        assertEquals(480.0 / 90.0, report.avgKmPerKwh!!, 0.0001)
    }

    @Test
    fun `interleaved out-of-scope charge excludes the same-trip pair`() {
        // Trip sessions at t=0 and t=2; an untripped home charge at t=1 for
        // the same vehicle. Trip-scoped analysis (sessions = trip only,
        // allSessions = full timeline) must NOT pair the trip sessions —
        // the home charge distorted the battery delta.
        val tripA = session(id = 1, t = 0, odo = 1000.0, battEnd = 80, tripId = 7)
        val homeCharge = session(id = 2, t = 1, odo = 1100.0, battStart = 40, battEnd = 90)
        val tripB = session(id = 3, t = 2, odo = 1280.0, battStart = 30, tripId = 7)

        val report = EfficiencyAnalysis.analyze(
            sessions = listOf(tripA, tripB),
            vehicle = vehicle,
            allSessions = listOf(tripA, homeCharge, tripB),
        )
        assertTrue(report.legs.isEmpty())
        assertEquals(1, report.excluded.size)
        assertEquals(1L, report.excluded.first().from.id)
        assertEquals(3L, report.excluded.first().to.id)
    }

    @Test
    fun `interleaved out-of-scope charge excludes a flagged pair too`() {
        // continuesPrevious attests "nothing charged in between" — an
        // out-of-scope charge in the gap contradicts it.
        val a = session(id = 1, t = 0, odo = 1000.0, battEnd = 80, tripId = 1)
        val between = session(id = 2, t = 1)
        val b = session(
            id = 3, t = 2, odo = 1280.0, battStart = 30, tripId = 2,
            continuesPrevious = true,
        )
        val report = EfficiencyAnalysis.analyze(
            sessions = listOf(a, b),
            vehicle = vehicle,
            allSessions = listOf(a, between, b),
        )
        assertTrue(report.legs.isEmpty())
        assertEquals(1, report.excluded.size)
    }

    @Test
    fun `full-timeline analysis is unchanged by the default allSessions`() {
        // VehicleDetail-style call: sessions == allSessions. The untripped
        // middle session splits the trip pair naturally (no flag set), and
        // no interleave exclusion fires for adjacent pairs.
        val report = EfficiencyAnalysis.analyze(
            listOf(
                session(id = 1, t = 0, odo = 1000.0, battEnd = 80, tripId = 7),
                session(id = 2, t = 1, odo = 1100.0, battStart = 40, battEnd = 90),
                session(id = 3, t = 2, odo = 1280.0, battStart = 30, tripId = 7),
            ),
            vehicle,
        )
        assertTrue(report.legs.isEmpty())
        assertTrue(report.excluded.isEmpty())
    }

    @Test
    fun `out-of-order input is sorted by time before pairing`() {
        // Pass in reverse chronological order. Pairing should still match
        // session 1 → session 2 chronologically.
        val report = EfficiencyAnalysis.analyze(
            listOf(
                session(id = 2, t = 1, odo = 1280.0, battStart = 30, tripId = 7),
                session(id = 1, t = 0, odo = 1000.0, battEnd = 80, tripId = 7),
            ),
            vehicle,
        )
        assertEquals(1, report.legs.size)
        assertEquals(1L, report.legs.first().from.id)
        assertEquals(2L, report.legs.first().to.id)
    }

    // --- trip boundary anchors ---

    @Test
    fun `start anchor measures the drive to the first charging stop`() {
        // Left home at 100%, odo 1000; first charge starts at 30%, odo 1280.
        // 280 km on 70 kWh → 4.0 km/kWh.
        val report = EfficiencyAnalysis.analyze(
            sessions = listOf(session(id = 1, t = 100, odo = 1280.0, battStart = 30, tripId = 7)),
            vehicle = vehicle,
            tripStart = TripAnchor(odometerKm = 1000.0, batteryPct = 100, atMillis = 50),
        )
        assertEquals(1, report.legs.size)
        val leg = report.legs.first()
        assertEquals(EfficiencyAnalysis.TRIP_START_ANCHOR_ID, leg.from.id)
        assertEquals(280.0, leg.distanceKm)
        assertEquals(70.0, leg.energyUsedKwh)
        assertEquals(4.0, leg.kmPerKwh, 0.0001)
    }

    @Test
    fun `end anchor measures the drive home from the last stop`() {
        // Last charge ended at 80%, odo 1280; arrived home at 40%, odo 1480.
        val report = EfficiencyAnalysis.analyze(
            sessions = listOf(session(id = 1, t = 100, odo = 1280.0, battEnd = 80, tripId = 7)),
            vehicle = vehicle,
            tripEnd = TripAnchor(odometerKm = 1480.0, batteryPct = 40, atMillis = 200),
        )
        assertEquals(1, report.legs.size)
        val leg = report.legs.first()
        assertEquals(EfficiencyAnalysis.TRIP_END_ANCHOR_ID, leg.to.id)
        assertEquals(200.0, leg.distanceKm)
        assertEquals(40.0, leg.energyUsedKwh)
    }

    @Test
    fun `both anchors plus a mid-trip stop produce all the legs`() {
        val report = EfficiencyAnalysis.analyze(
            sessions = listOf(
                session(id = 1, t = 100, odo = 1280.0, battStart = 30, battEnd = 80, tripId = 7),
            ),
            vehicle = vehicle,
            tripStart = TripAnchor(odometerKm = 1000.0, batteryPct = 100, atMillis = 50),
            tripEnd = TripAnchor(odometerKm = 1480.0, batteryPct = 40, atMillis = 200),
        )
        assertEquals(2, report.legs.size)
        assertTrue(report.excluded.isEmpty())
    }

    @Test
    fun `anchor missing its odometer reading is excluded with a reason`() {
        val report = EfficiencyAnalysis.analyze(
            sessions = listOf(session(id = 1, t = 100, odo = 1280.0, battStart = 30, tripId = 7)),
            vehicle = vehicle,
            tripStart = TripAnchor(odometerKm = null, batteryPct = 100, atMillis = null),
        )
        assertTrue(report.legs.isEmpty())
        assertEquals(1, report.excluded.size)
        assertEquals(EfficiencyAnalysis.TRIP_START_ANCHOR_ID, report.excluded.first().from.id)
    }

    @Test
    fun `anchor with no data at all is ignored`() {
        val report = EfficiencyAnalysis.analyze(
            sessions = listOf(session(id = 1, t = 100, odo = 1280.0, battStart = 30, tripId = 7)),
            vehicle = vehicle,
            tripStart = TripAnchor(odometerKm = null, batteryPct = null, atMillis = 50),
        )
        assertTrue(report.legs.isEmpty())
        assertTrue(report.excluded.isEmpty())
    }

    @Test
    fun `charge between trip start and first session excludes the anchor leg`() {
        // Home top-up at t=75 that isn't part of the trip: the 100%-at-start
        // attestation no longer covers the gap.
        val tripSession = session(id = 1, t = 100, odo = 1280.0, battStart = 30, tripId = 7)
        val homeTopUp = session(id = 2, t = 75)
        val report = EfficiencyAnalysis.analyze(
            sessions = listOf(tripSession),
            vehicle = vehicle,
            allSessions = listOf(tripSession, homeTopUp),
            tripStart = TripAnchor(odometerKm = 1000.0, batteryPct = 100, atMillis = 50),
        )
        assertTrue(report.legs.isEmpty())
        assertEquals(1, report.excluded.size)
    }

    @Test
    fun `zero-session trip with both anchors is a single whole-trip leg`() {
        val report = EfficiencyAnalysis.analyze(
            sessions = emptyList(),
            vehicle = vehicle,
            tripStart = TripAnchor(odometerKm = 1000.0, batteryPct = 90, atMillis = 50),
            tripEnd = TripAnchor(odometerKm = 1200.0, batteryPct = 40, atMillis = 200),
        )
        assertEquals(1, report.legs.size)
        val leg = report.legs.first()
        assertEquals(200.0, leg.distanceKm)
        assertEquals(50.0, leg.energyUsedKwh)
    }

    @Test
    fun `zero-session trip with a charge inside its dates is excluded`() {
        val strayCharge = session(id = 9, t = 120)
        val report = EfficiencyAnalysis.analyze(
            sessions = emptyList(),
            vehicle = vehicle,
            allSessions = listOf(strayCharge),
            tripStart = TripAnchor(odometerKm = 1000.0, batteryPct = 90, atMillis = 50),
            tripEnd = TripAnchor(odometerKm = 1200.0, batteryPct = 40, atMillis = 200),
        )
        assertTrue(report.legs.isEmpty())
        assertEquals(1, report.excluded.size)
    }

    private fun session(
        id: Long,
        t: Long = 0,
        odo: Double? = null,
        battStart: Int? = null,
        battEnd: Int? = null,
        tripId: Long? = null,
        continuesPrevious: Boolean = false,
    ): ChargingSession = ChargingSession(
        id = id,
        sessionStart = t,
        odometerKm = odo,
        batteryStartPct = battStart,
        batteryEndPct = battEnd,
        tripId = tripId,
        continuesPrevious = continuesPrevious,
    )
}
