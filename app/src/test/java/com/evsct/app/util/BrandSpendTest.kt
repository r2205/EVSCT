package com.evsct.app.util

import com.evsct.app.data.entity.ChargingSession
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BrandSpendTest {

    @Test
    fun `case variants merge into one brand`() {
        val top = BrandSpend.top(
            listOf(
                session(brand = "FLO", cost = 10.0),
                session(brand = "Flo", cost = 5.0),
                session(brand = " flo ", cost = 1.0),
            ),
        )
        assertEquals(1, top.size)
        assertEquals(16.0, top.first().second, 1e-9)
    }

    @Test
    fun `merged group is labeled with its most frequent casing`() {
        val top = BrandSpend.top(
            listOf(
                session(brand = "FLO", cost = 1.0, t = 1),
                session(brand = "FLO", cost = 1.0, t = 2),
                session(brand = "Flo", cost = 50.0, t = 3),
            ),
        )
        assertEquals("FLO", top.first().first)
    }

    @Test
    fun `frequency tie goes to the most recently used casing`() {
        val top = BrandSpend.top(
            listOf(
                session(brand = "FLO", cost = 1.0, t = 1),
                session(brand = "Flo", cost = 1.0, t = 2),
            ),
        )
        assertEquals("Flo", top.first().first)
    }

    @Test
    fun `blank brands and non-positive costs are excluded`() {
        val top = BrandSpend.top(
            listOf(
                session(brand = null, cost = 10.0),
                session(brand = "  ", cost = 10.0),
                session(brand = "FLO", cost = 0.0),
                session(brand = "FLO", cost = -5.0),
                session(brand = "FLO", cost = null),
            ),
        )
        assertTrue(top.isEmpty())
    }

    @Test
    fun `sorted by spend descending and limited`() {
        val sessions = ('a'..'z').mapIndexed { i, c ->
            session(brand = c.toString(), cost = (i + 1).toDouble())
        }
        val top = BrandSpend.top(sessions, limit = 3)
        assertEquals(listOf("z", "y", "x"), top.map { it.first })
        assertEquals(listOf(26.0, 25.0, 24.0), top.map { it.second })
    }

    private fun session(
        brand: String?,
        cost: Double?,
        t: Long = 0,
    ): ChargingSession = ChargingSession(
        sessionStart = t,
        brand = brand,
        totalCost = cost,
    )
}
