package com.evsct.app.util

import org.junit.Test
import kotlin.test.assertEquals

class GasComparisonTest {

    @Test
    fun `defaults are the old hard-coded figures`() {
        val gas = GasComparison()
        assertEquals(2.15, gas.pricePerLitre, 1e-12)
        assertEquals(12.0, gas.litresPer100Km, 1e-12)
        // 500 km at 12 L/100 km is 60 L, at $2.15 is $129.
        assertEquals(129.0, gas.costForKm(500.0), 1e-9)
    }

    @Test
    fun `kilometre users enter the stored units unchanged`() {
        assertEquals(1.89, GasUnits.priceToDisplay(1.89, useMiles = false), 1e-12)
        assertEquals(7.5, GasUnits.economyFromDisplay(7.5, useMiles = false), 1e-12)
    }

    @Test
    fun `miles users enter dollars per gallon and mpg`() {
        // $3.785411784/gal is exactly $1/L.
        assertEquals(1.0, GasUnits.priceFromDisplay(3.785411784, useMiles = true), 1e-12)
        // 10 L/100 km is about 23.5 mpg (US).
        assertEquals(23.5215, GasUnits.economyToDisplay(10.0, useMiles = true), 1e-4)
    }

    @Test
    fun `economy shows one decimal, dropped when whole`() {
        assertEquals("12", GasUnits.economyNumber(12.0))
        assertEquals("19.6", GasUnits.economyNumber(19.6015))
        assertEquals("7.5", GasUnits.economyNumber(7.46))
    }

    @Test
    fun `conversions round-trip`() {
        for (useMiles in listOf(false, true)) {
            assertEquals(2.15, GasUnits.priceFromDisplay(GasUnits.priceToDisplay(2.15, useMiles), useMiles), 1e-12)
            assertEquals(12.0, GasUnits.economyFromDisplay(GasUnits.economyToDisplay(12.0, useMiles), useMiles), 1e-12)
        }
    }
}
