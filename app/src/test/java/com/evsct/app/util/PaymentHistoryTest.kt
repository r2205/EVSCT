package com.evsct.app.util

import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.data.entity.PaymentMethod
import com.evsct.app.data.entity.PaymentMethod.APP
import com.evsct.app.data.entity.PaymentMethod.CREDIT_CARD
import com.evsct.app.data.entity.PaymentMethod.MOBILE_WALLET
import com.evsct.app.data.entity.PaymentMethod.PLUG_AND_CHARGE
import org.junit.Test
import kotlin.test.assertEquals

/**
 * The payment-reuse helper behind the session form's Payment chips. What
 * matters is the order — the card from the last charge first — and that
 * one card doesn't show up twice under two spellings.
 */
class PaymentHistoryTest {

    @Test
    fun `uses are most recently used first`() {
        val uses = PaymentHistory.uses(
            listOf(
                session(t = 1, CREDIT_CARD, "Amex"),
                session(t = 2, APP, "FLO"),
                session(t = 3, CREDIT_CARD, "TD Visa"),
            ),
        )
        assertEquals(
            listOf(
                PaymentUse(CREDIT_CARD, "TD Visa"),
                PaymentUse(APP, "FLO"),
                PaymentUse(CREDIT_CARD, "Amex"),
            ),
            uses,
        )
    }

    @Test
    fun `a detail merges case-insensitively under its most frequent casing`() {
        val uses = PaymentHistory.uses(
            listOf(
                session(t = 1, CREDIT_CARD, "TD Visa"),
                session(t = 2, CREDIT_CARD, "TD Visa"),
                // Most recent, but outvoted.
                session(t = 3, CREDIT_CARD, "td visa "),
            ),
        )
        assertEquals(listOf(PaymentUse(CREDIT_CARD, "TD Visa")), uses)
    }

    @Test
    fun `the same detail under two methods stays two answers`() {
        // Tapping the card and tapping the phone with it in the wallet are
        // different ways of paying — one chip for both would refill the
        // wrong method half the time.
        val uses = PaymentHistory.uses(
            listOf(
                session(t = 1, CREDIT_CARD, "TD Visa"),
                session(t = 2, MOBILE_WALLET, "TD Visa"),
            ),
        )
        assertEquals(
            listOf(PaymentUse(MOBILE_WALLET, "TD Visa"), PaymentUse(CREDIT_CARD, "TD Visa")),
            uses,
        )
    }

    @Test
    fun `a method used without a detail is its own entry`() {
        val uses = PaymentHistory.uses(
            listOf(
                session(t = 1, PLUG_AND_CHARGE, null),
                session(t = 2, PLUG_AND_CHARGE, "  "),
                session(t = 3, PLUG_AND_CHARGE, "Tesla"),
            ),
        )
        assertEquals(
            listOf(PaymentUse(PLUG_AND_CHARGE, "Tesla"), PaymentUse(PLUG_AND_CHARGE, null)),
            uses,
        )
    }

    @Test
    fun `sessions without a method are skipped`() {
        val uses = PaymentHistory.uses(
            listOf(
                session(t = 1, null, null),
                // A stray detail with no method isn't a way of paying.
                session(t = 2, null, "TD Visa"),
            ),
        )
        assertEquals(emptyList(), uses)
    }

    @Test
    fun `recency ties go to the more used, then A to Z`() {
        val uses = PaymentHistory.uses(
            listOf(
                session(t = 1, CREDIT_CARD, "Visa"),
                session(t = 5, CREDIT_CARD, "Visa"),
                session(t = 5, CREDIT_CARD, "Amex"),
                session(t = 5, APP, "FLO"),
            ),
        )
        assertEquals(listOf("Visa", "Amex", "FLO"), uses.map { it.detail })
    }

    @Test
    fun `details are one method's, in recency order`() {
        val uses = PaymentHistory.uses(
            listOf(
                session(t = 1, CREDIT_CARD, "Amex"),
                session(t = 2, APP, "FLO"),
                session(t = 3, CREDIT_CARD, "TD Visa"),
                session(t = 4, CREDIT_CARD, null),
            ),
        )
        assertEquals(listOf("TD Visa", "Amex"), PaymentHistory.details(uses, CREDIT_CARD))
        assertEquals(listOf("FLO"), PaymentHistory.details(uses, APP))
        assertEquals(emptyList(), PaymentHistory.details(uses, MOBILE_WALLET))
    }

    private fun session(t: Long, method: PaymentMethod?, detail: String?) = ChargingSession(
        sessionStart = t,
        paymentMethod = method,
        paymentDetail = detail,
    )
}
