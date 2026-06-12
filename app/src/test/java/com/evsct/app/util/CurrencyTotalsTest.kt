package com.evsct.app.util

import com.evsct.app.data.entity.ChargingSession
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CurrencyTotalsTest {

    @Test
    fun `empty input is empty`() {
        val totals = CurrencyTotals.from(emptyList())
        assertTrue(totals.isEmpty)
        assertFalse(totals.isMixed)
        assertNull(totals.singleCurrency)
        assertNull(totals.singleTotal)
    }

    @Test
    fun `null cost is skipped`() {
        val totals = CurrencyTotals.from(listOf(session(cost = null, currency = "CAD")))
        assertTrue(totals.isEmpty)
    }

    @Test
    fun `zero cost (free session) is skipped`() {
        // 0.0 means "free" per ChargingSession.totalCost — the doc contract
        // is "empty when all null/zero costs".
        val totals = CurrencyTotals.from(listOf(session(cost = 0.0, currency = "CAD")))
        assertTrue(totals.isEmpty)
    }

    @Test
    fun `free session in another currency does not flip isMixed`() {
        // A $0.00 USD row alongside paid CAD rows must not open a USD
        // bucket — that would null singleTotal and kill $-per-kWh and
        // $-per-km everywhere downstream.
        val totals = CurrencyTotals.from(
            listOf(
                session(cost = 10.0, currency = "CAD"),
                session(cost = 0.0, currency = "USD"),
            ),
        )
        assertFalse(totals.isMixed)
        assertEquals("CAD", totals.singleCurrency)
        assertEquals(10.0, totals.singleTotal)
    }

    @Test
    fun `single currency aggregates and exposes singleTotal`() {
        val totals = CurrencyTotals.from(
            listOf(
                session(cost = 10.0, currency = "CAD"),
                session(cost = 25.50, currency = "CAD"),
            ),
        )
        assertFalse(totals.isMixed)
        assertEquals("CAD", totals.singleCurrency)
        assertEquals(35.50, totals.singleTotal)
    }

    @Test
    fun `mixed currencies separate buckets`() {
        val totals = CurrencyTotals.from(
            listOf(
                session(cost = 10.0, currency = "CAD"),
                session(cost = 20.0, currency = "USD"),
                session(cost = 5.0, currency = "CAD"),
            ),
        )
        assertTrue(totals.isMixed)
        assertNull(totals.singleCurrency)
        assertNull(totals.singleTotal)
        assertEquals(15.0, totals.byCurrency["CAD"])
        assertEquals(20.0, totals.byCurrency["USD"])
    }

    @Test
    fun `Money format renders single currency without separator`() {
        val totals = CurrencyTotals(mapOf("CAD" to 12.34))
        // Single-currency display matches the existing per-session Format.money
        // output exactly so users see one consistent number.
        assertEquals(Format.money(12.34, "CAD"), Money.format(totals))
    }

    @Test
    fun `Money format joins mixed currencies with separator`() {
        val totals = CurrencyTotals(mapOf("CAD" to 245.0, "USD" to 89.5))
        // Largest first; both currencies present.
        val rendered = Money.format(totals)
        assertTrue(rendered.startsWith(Format.money(245.0, "CAD")))
        assertTrue(rendered.contains(" · "))
        assertTrue(rendered.endsWith(Format.money(89.5, "USD")))
    }

    @Test
    fun `Money format empty totals renders dash`() {
        assertEquals("—", Money.format(CurrencyTotals(emptyMap())))
    }

    private fun session(cost: Double?, currency: String): ChargingSession =
        ChargingSession(
            id = 0,
            sessionStart = 0L,
            totalCost = cost,
            currency = currency,
        )
}
