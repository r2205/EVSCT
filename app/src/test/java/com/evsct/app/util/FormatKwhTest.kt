package com.evsct.app.util

import org.junit.Test
import kotlin.test.assertEquals

/**
 * kWh displays round to one decimal for readability. Only the display
 * string is shortened — the stored Double and the edit-screen seed
 * (energyKwh.toString()) keep every decimal the user entered.
 */
class FormatKwhTest {

    @Test
    fun `display rounds to one decimal`() {
        assertEquals("48.1 kWh", Format.kwh(48.123))
        assertEquals("1,234.6 kWh", Format.kwh(1234.567))
    }

    @Test
    fun `a two-decimal half rounds up`() {
        // 48.25 is exact in binary, so this is a true tie on every platform.
        assertEquals("48.3 kWh", Format.kwh(48.25))
    }

    @Test
    fun `whole values drop the trailing zero`() {
        assertEquals("42 kWh", Format.kwh(42.0))
        assertEquals("42 kWh", Format.kwh(41.96))
    }

    @Test
    fun `missing energy renders a dash`() {
        assertEquals("—", Format.kwh(null))
    }
}
