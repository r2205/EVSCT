package com.evsct.app.ui.sessions

import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.data.entity.Vehicle
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Log's vehicle tab buckets. [VehicleScope.Unassigned] exists because a
 * session's vehicle is nullable — CSV imports and anything logged before a
 * vehicle was set up carry none — and those sessions used to be reachable only
 * under All, mixed into the whole log.
 */
class VehicleScopeTest {

    private fun session(vehicleId: Long?) =
        ChargingSession(sessionStart = 1_752_000_000_000L, vehicleId = vehicleId)

    private fun vehicles(vararg ids: Long) = ids.map { Vehicle(id = it, name = "V$it") }

    /* ------------------------------ matches ------------------------------ */

    @Test
    fun `All takes everything, assigned or not`() {
        assertTrue(VehicleScope.All.matches(session(7L)))
        assertTrue(VehicleScope.All.matches(session(null)))
    }

    @Test
    fun `Unassigned takes only sessions with no vehicle`() {
        assertTrue(VehicleScope.Unassigned.matches(session(null)))
        assertFalse(VehicleScope.Unassigned.matches(session(7L)))
    }

    @Test
    fun `One takes only its own vehicle`() {
        val scope = VehicleScope.One(7L)
        assertTrue(scope.matches(session(7L)))
        assertFalse(scope.matches(session(8L)))
        // The bug this guards: an unassigned session must not fall into a
        // vehicle's bucket just because both are "not vehicle 8".
        assertFalse(scope.matches(session(null)))
    }

    /* --------------------------- orAllIfEmpty ---------------------------- */

    @Test
    fun `a live vehicle scope survives`() {
        assertEquals(
            VehicleScope.One(7L),
            VehicleScope.One(7L).orAllIfEmpty(vehicles(7L, 8L), hasUnassigned = false),
        )
    }

    @Test
    fun `a deleted vehicle falls back to All`() {
        // Otherwise the log sits empty under a tab that no longer exists.
        assertEquals(
            VehicleScope.All,
            VehicleScope.One(7L).orAllIfEmpty(vehicles(8L), hasUnassigned = false),
        )
    }

    @Test
    fun `Unassigned survives while such sessions exist`() {
        assertEquals(
            VehicleScope.Unassigned,
            VehicleScope.Unassigned.orAllIfEmpty(vehicles(7L), hasUnassigned = true),
        )
    }

    @Test
    fun `Unassigned falls back once the last one is assigned`() {
        // Assigning the final orphan removes its tab, so the scope behind it
        // has to let go too.
        assertEquals(
            VehicleScope.All,
            VehicleScope.Unassigned.orAllIfEmpty(vehicles(7L), hasUnassigned = false),
        )
    }

    @Test
    fun `All is always itself`() {
        assertEquals(
            VehicleScope.All,
            VehicleScope.All.orAllIfEmpty(emptyList(), hasUnassigned = false),
        )
        assertEquals(
            VehicleScope.All,
            VehicleScope.All.orAllIfEmpty(vehicles(7L), hasUnassigned = true),
        )
    }
}
