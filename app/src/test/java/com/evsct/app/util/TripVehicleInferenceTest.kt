package com.evsct.app.util

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Mirrors the cases the DB v15 migration's backfill is checked against. */
class TripVehicleInferenceTest {

    private val twoCars = listOf(1L, 2L)

    @Test
    fun `sessions all on one car give that car`() {
        assertEquals(1L, TripVehicleInference.infer(listOf(1L, 1L), twoCars))
    }

    @Test
    fun `unassigned sessions go along with the one named car`() {
        assertEquals(1L, TripVehicleInference.infer(listOf(1L, null), twoCars))
    }

    @Test
    fun `mixed cars are left for the user`() {
        assertNull(TripVehicleInference.infer(listOf(1L, 2L), twoCars))
    }

    @Test
    fun `nothing to go on with several cars is left for the user`() {
        assertNull(TripVehicleInference.infer(emptyList(), twoCars))
        assertNull(TripVehicleInference.infer(listOf(null, null), twoCars))
    }

    @Test
    fun `with exactly one car an unsettled trip gets it`() {
        assertEquals(5L, TripVehicleInference.infer(emptyList(), listOf(5L)))
        assertEquals(5L, TripVehicleInference.infer(listOf(null), listOf(5L)))
    }

    @Test
    fun `ids of unknown cars are ignored`() {
        assertEquals(2L, TripVehicleInference.infer(listOf(99L, 2L), twoCars))
        assertNull(TripVehicleInference.infer(listOf(99L), twoCars))
    }

    @Test
    fun `no cars at all names none`() {
        assertNull(TripVehicleInference.infer(listOf(null), emptyList()))
    }
}
