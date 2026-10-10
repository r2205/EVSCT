package com.evsct.app.util

import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.data.entity.Trip
import com.evsct.app.data.entity.Vehicle
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TripReportTest {

    // 80 kWh battery: every 10% of battery is 8 kWh.
    private val car = Vehicle(id = 1, name = "EV6", batteryCapacityKwh = 80.0)
    private val otherCar = Vehicle(id = 2, name = "Mach-E", batteryCapacityKwh = 90.0)
    private val garage = listOf(car, otherCar)

    @Test
    fun `a trip with no charging stops is one leg from its own readings`() {
        // 100% → 62% on 80 kWh is 30.4 kWh over 180 km.
        val trip = trip(vehicleId = 1, startOdo = 10_000.0, endOdo = 10_180.0, startPct = 100, endPct = 62)

        val report = TripReport.of(trip, emptyList(), emptyList(), garage)

        assertEquals(1, report.legs.size)
        val leg = report.legs.single()
        assertEquals(180.0, leg.distanceKm, 1e-9)
        assertEquals(30.4, leg.energyUsedKwh, 1e-9)
        assertEquals(180.0 / 30.4, report.avgKmPerKwh!!, 1e-9)
        assertEquals(180.0, report.distanceKm, 1e-9)
    }

    @Test
    fun `the trip's car is used even when several cars are set up`() {
        // Before trips had a car, a trip with no sessions measured nothing
        // unless the garage held exactly one vehicle.
        val trip = trip(vehicleId = 2, startOdo = 5_000.0, endOdo = 5_090.0, startPct = 90, endPct = 70)

        val report = TripReport.of(trip, emptyList(), emptyList(), garage)

        assertEquals(otherCar, report.vehicle)
        assertEquals(18.0, report.legs.single().energyUsedKwh, 1e-9)  // 20% of 90 kWh
    }

    @Test
    fun `a session from another car is set aside without losing the trip readings`() {
        val trip = trip(vehicleId = 1, startOdo = 10_000.0, endOdo = 10_600.0, startPct = 100, endPct = 50)
        val sessions = listOf(
            session(id = 1, t = 10, odo = 10_200.0, battStart = 60, battEnd = 90, vehicleId = 1),
            session(id = 2, t = 20, odo = 30_000.0, battStart = 20, battEnd = 80, vehicleId = 2),
            session(id = 3, t = 30, odo = 10_400.0, battStart = 55, battEnd = 85, vehicleId = 1),
        )

        val report = TripReport.of(trip, sessions, sessions, garage)

        assertEquals(listOf(2L), report.otherCarSessions.map { it.id })
        // start → 1, 1 → 3, 3 → end: all three legs, on the trip's car only.
        assertEquals(3, report.legs.size)
        assertTrue(report.excluded.isEmpty())
        assertEquals(listOf(200.0, 200.0, 200.0), report.legs.map { it.distanceKm })
        assertEquals(600.0, report.distanceKm, 1e-9)
    }

    @Test
    fun `a session with no vehicle is counted as the trip's car`() {
        val trip = trip(vehicleId = 1)
        val sessions = listOf(
            session(id = 1, t = 10, odo = 10_000.0, battEnd = 90, vehicleId = 1),
            session(id = 2, t = 20, odo = 10_250.0, battStart = 40, vehicleId = null),
        )

        val report = TripReport.of(trip, sessions, sessions, garage)

        assertEquals(listOf(2L), report.unassignedSessions.map { it.id })
        assertTrue(report.otherCarSessions.isEmpty())
        assertEquals(40.0, report.legs.single().energyUsedKwh, 1e-9)  // 50% of 80 kWh
    }

    @Test
    fun `a trip with no car measures its sessions per car but not its own readings`() {
        val trip = trip(vehicleId = null, startOdo = 9_900.0, endOdo = 10_400.0, startPct = 100, endPct = 50)
        val sessions = listOf(
            session(id = 1, t = 10, odo = 10_000.0, battEnd = 90, vehicleId = 1),
            session(id = 2, t = 20, odo = 10_250.0, battStart = 40, vehicleId = 1),
        )

        val report = TripReport.of(trip, sessions, sessions, garage)

        assertNull(report.vehicle)
        // Only the session pair — no trip start/end legs.
        assertEquals(1, report.legs.size)
        assertEquals(1L, report.legs.single().from.id)
    }

    @Test
    fun `energy used adds up every measured drive`() {
        // start → 1: 200 km, 100% → 60% = 32 kWh; 1 → end: 250 km, 90% → 40% = 40 kWh.
        val trip = trip(vehicleId = 1, startOdo = 10_000.0, endOdo = 10_450.0, startPct = 100, endPct = 40)
        val sessions = listOf(session(id = 1, t = 10, odo = 10_200.0, battStart = 60, battEnd = 90, vehicleId = 1))

        val report = TripReport.of(trip, sessions, sessions, garage)

        assertEquals(72.0, report.energyUsedKwh!!, 1e-9)
        assertEquals(450.0, report.measuredDistanceKm, 1e-9)
        assertEquals(false, report.energyUsedIsPartial)
    }

    @Test
    fun `energy used is partial when a drive between readings can't be measured`() {
        // The drive home has no end battery %, so only the first 200 of the
        // trip's 450 km are measured.
        val trip = trip(vehicleId = 1, startOdo = 10_000.0, endOdo = 10_450.0, startPct = 100, endPct = null)
        val sessions = listOf(session(id = 1, t = 10, odo = 10_200.0, battStart = 60, battEnd = 90, vehicleId = 1))

        val report = TripReport.of(trip, sessions, sessions, garage)

        assertEquals(32.0, report.energyUsedKwh!!, 1e-9)
        assertEquals(200.0, report.measuredDistanceKm, 1e-9)
        assertTrue(report.energyUsedIsPartial)
        assertEquals(200.0 / 32.0, report.avgKmPerKwh!!, 1e-9)
    }

    @Test
    fun `a lone start reading counts toward the distance its leg measures`() {
        // start → 1 → 2 measures 500 km; the distance used to read 250 (the
        // sessions' spread alone), less than the legs it was made of.
        val trip = trip(vehicleId = 1, startOdo = 10_000.0, startPct = 100)
        val sessions = listOf(
            session(id = 1, t = 10, odo = 10_250.0, battStart = 20, battEnd = 80, vehicleId = 1),
            session(id = 2, t = 20, odo = 10_500.0, battStart = 25, battEnd = 80, vehicleId = 1),
        )

        val report = TripReport.of(trip, sessions, sessions, garage)

        assertEquals(500.0, report.distanceKm, 1e-9)
        assertEquals(500.0, report.measuredDistanceKm, 1e-9)
        assertEquals(false, report.energyUsedIsPartial)
    }

    @Test
    fun `nothing measurable means no energy figure at all`() {
        val trip = trip(vehicleId = 1, startOdo = 10_000.0, endOdo = 10_180.0)

        val report = TripReport.of(trip, emptyList(), emptyList(), garage)

        assertNull(report.energyUsedKwh)
        assertEquals(false, report.energyUsedIsPartial)
        // The reason names the trip's own missing fields.
        assertEquals("Add the trip's start and end battery %", report.excluded.single().reason)
    }

    @Test
    fun `an untagged charge during the trip still blocks the leg it falls in`() {
        val trip = trip(vehicleId = 1)
        val tagged = listOf(
            session(id = 1, t = 10, odo = 10_000.0, battEnd = 90, vehicleId = 1),
            session(id = 3, t = 30, odo = 10_250.0, battStart = 40, vehicleId = 1),
        )
        val midTripTopUp = session(id = 2, t = 20, odo = 10_100.0, battStart = 70, battEnd = 95, vehicleId = 1, tripId = null)

        val report = TripReport.of(trip, tagged, tagged + midTripTopUp, garage)

        assertTrue(report.legs.isEmpty())
        assertEquals(1, report.excluded.size)
    }

    private fun trip(
        vehicleId: Long?,
        startOdo: Double? = null,
        endOdo: Double? = null,
        startPct: Int? = null,
        endPct: Int? = null,
    ) = Trip(
        id = 7,
        name = "Trip",
        startOdometerKm = startOdo,
        endOdometerKm = endOdo,
        startBatteryPct = startPct,
        endBatteryPct = endPct,
        vehicleId = vehicleId,
    )

    private fun session(
        id: Long,
        t: Long,
        odo: Double?,
        battStart: Int? = null,
        battEnd: Int? = null,
        vehicleId: Long?,
        tripId: Long? = 7,
    ) = ChargingSession(
        id = id,
        sessionStart = t,
        odometerKm = odo,
        batteryStartPct = battStart,
        batteryEndPct = battEnd,
        tripId = tripId,
        vehicleId = vehicleId,
    )
}
