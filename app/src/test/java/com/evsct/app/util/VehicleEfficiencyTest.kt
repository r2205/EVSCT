package com.evsct.app.util

import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.data.entity.Trip
import com.evsct.app.data.entity.Vehicle
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VehicleEfficiencyTest {

    // 80 kWh battery: every 10% of battery is 8 kWh.
    private val car = Vehicle(id = 1, name = "EV6", batteryCapacityKwh = 80.0)
    private val otherCar = Vehicle(id = 2, name = "Mach-E", batteryCapacityKwh = 90.0)

    @Test
    fun `a trip's start and end drives count toward its car`() {
        // One charging stop, so no session pair: the car had no lifetime
        // efficiency at all before. start → 1: 200 km on 40% = 32 kWh;
        // 1 → end: 250 km on 50% = 40 kWh.
        val trip = trip(vehicleId = 1, startOdo = 10_000.0, endOdo = 10_450.0, startPct = 100, endPct = 40)
        val sessions = listOf(session(id = 1, t = 10, odo = 10_200.0, battStart = 60, battEnd = 90, tripId = 7))

        val report = VehicleEfficiency.of(car, sessions, listOf(trip))

        assertEquals(2, report.legs.size)
        assertEquals(450.0 / 72.0, report.avgKmPerKwh!!, 1e-9)
    }

    @Test
    fun `a trip with no charging counts toward its car`() {
        // 180 km on 38% = 30.4 kWh, alongside 300 km on 50% = 40 kWh between
        // two of the car's own sessions.
        val trip = trip(vehicleId = 1, startOdo = 10_000.0, endOdo = 10_180.0, startPct = 100, endPct = 62)
        val sessions = listOf(
            session(id = 1, t = 100, odo = 11_000.0, battEnd = 90),
            session(id = 2, t = 200, odo = 11_300.0, battStart = 40, continuesPrevious = true),
        )

        val report = VehicleEfficiency.of(car, sessions, listOf(trip))

        assertEquals(2, report.legs.size)
        assertEquals(480.0 / 70.4, report.avgKmPerKwh!!, 1e-9)
    }

    @Test
    fun `a trip's stops and its start and end drives each count once`() {
        // start → 1 → 2 → end, 200 km apiece: the 1 → 2 pair was already
        // counted, and the trip adds the drives on either side of it.
        val trip = trip(vehicleId = 1, startOdo = 10_000.0, endOdo = 10_600.0, startPct = 100, endPct = 50)
        val sessions = listOf(
            session(id = 1, t = 10, odo = 10_200.0, battStart = 60, battEnd = 90, tripId = 7),
            session(id = 2, t = 20, odo = 10_400.0, battStart = 55, battEnd = 85, tripId = 7),
        )

        val report = VehicleEfficiency.of(car, sessions, listOf(trip))

        assertEquals(listOf(200.0, 200.0, 200.0), report.legs.map { it.distanceKm }.sorted())
        assertEquals(600.0 / 88.0, report.avgKmPerKwh!!, 1e-9)
    }

    @Test
    fun `another car's trips don't count`() {
        val trip = trip(vehicleId = 2, startOdo = 5_000.0, endOdo = 5_090.0, startPct = 90, endPct = 70)

        assertNull(VehicleEfficiency.of(car, emptyList(), listOf(trip)).avgKmPerKwh)
        // 90 km on 20% of 90 kWh = 18 kWh.
        assertEquals(5.0, VehicleEfficiency.of(otherCar, emptyList(), listOf(trip)).avgKmPerKwh!!, 1e-9)
    }

    @Test
    fun `a trip's first drive isn't counted again inside a flagged session pair`() {
        // The first stop says it continues from the charge before the trip,
        // so that pair already covers the drive from the trip's start.
        val trip = trip(vehicleId = 1, startOdo = 10_000.0, startPct = 100)
        val sessions = listOf(
            session(id = 1, t = 0, odo = 9_900.0, battEnd = 100),
            session(id = 2, t = 100, odo = 10_200.0, battStart = 60, tripId = 7, continuesPrevious = true),
        )

        val report = VehicleEfficiency.of(car, sessions, listOf(trip))

        assertEquals(listOf(1L to 2L), report.legs.map { it.from.id to it.to.id })
    }

    @Test
    fun `a trip with no charging isn't counted again inside a flagged session pair`() {
        val trip = trip(vehicleId = 1, startOdo = 10_000.0, endOdo = 10_200.0, startPct = 95, endPct = 60)
        val sessions = listOf(
            session(id = 1, t = 0, odo = 9_900.0, battEnd = 100),
            session(id = 2, t = 100, odo = 10_300.0, battStart = 40, continuesPrevious = true),
        )

        val report = VehicleEfficiency.of(car, sessions, listOf(trip))

        // Only the pair: 400 km on 60% = 48 kWh.
        assertEquals(1, report.legs.size)
        assertEquals(400.0 / 48.0, report.avgKmPerKwh!!, 1e-9)
    }

    @Test
    fun `trips without their own readings change nothing`() {
        val sessions = listOf(
            session(id = 1, t = 10, odo = 10_200.0, battEnd = 90, tripId = 7),
            session(id = 2, t = 20, odo = 10_400.0, battStart = 55, tripId = 7),
        )

        val withTrip = VehicleEfficiency.of(car, sessions, listOf(trip(vehicleId = 1)))

        assertEquals(EfficiencyAnalysis.analyze(sessions, car).legs, withTrip.legs)
    }

    @Test
    fun `a stop with no vehicle on the car's trip still ends its first drive`() {
        // The trip says which car it was, so its first stop is taken to be
        // that car's, as on the trip's own screen.
        val trip = trip(vehicleId = 1, startOdo = 10_000.0, startPct = 100)
        val sessions = listOf(session(id = 1, t = 10, odo = 10_200.0, battStart = 60, tripId = 7, vehicleId = null))

        val report = VehicleEfficiency.of(car, sessions, listOf(trip))

        assertEquals(200.0 / 32.0, report.avgKmPerKwh!!, 1e-9)
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
        tripId: Long? = null,
        vehicleId: Long? = 1,
        continuesPrevious: Boolean = false,
    ) = ChargingSession(
        id = id,
        sessionStart = t,
        odometerKm = odo,
        batteryStartPct = battStart,
        batteryEndPct = battEnd,
        tripId = tripId,
        vehicleId = vehicleId,
        continuesPrevious = continuesPrevious,
    )
}
