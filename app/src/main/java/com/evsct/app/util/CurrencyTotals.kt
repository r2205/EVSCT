package com.evsct.app.util

import com.evsct.app.data.entity.ChargingSession

/**
 * Costs aggregated across charging sessions, partitioned by their per-session
 * [ChargingSession.currency]. Lets the UI render a multi-currency total
 * faithfully ("$245.00 CAD · $89.50 USD") and decide whether derived rates
 * like cost-per-kWh make sense (they don't when currencies are mixed — the
 * sum has no single unit).
 */
data class CurrencyTotals(val byCurrency: Map<String, Double>) {

    /** Empty when no session contributed (all null/zero costs). */
    val isEmpty: Boolean get() = byCurrency.isEmpty()

    /** True when more than one distinct currency is represented. */
    val isMixed: Boolean get() = byCurrency.size > 1

    /** The single currency code when there's exactly one, else null. */
    val singleCurrency: String? get() =
        if (byCurrency.size == 1) byCurrency.keys.first() else null

    /** The single total when there's exactly one currency, else null. */
    val singleTotal: Double? get() =
        if (byCurrency.size == 1) byCurrency.values.first() else null

    companion object {
        fun from(
            sessions: List<ChargingSession>,
            valueOf: (ChargingSession) -> Double? = { it.totalCost },
        ): CurrencyTotals {
            val m = mutableMapOf<String, Double>()
            sessions.forEach { s ->
                // 0.0 means "free" (see ChargingSession.totalCost). A free
                // session contributes nothing and must not open a currency
                // bucket: one free USD-tagged row would otherwise flip
                // isMixed and suppress every derived rate ($/kWh, $/km)
                // for an all-CAD log.
                val v = valueOf(s) ?: return@forEach
                if (v == 0.0) return@forEach
                m.merge(s.currency, v) { a, b -> a + b }
            }
            return CurrencyTotals(m)
        }
    }
}

object Money {

    /** Multi-currency display: "$245.00 CAD · $89.50 USD" (largest first).
     *  Falls back to "—" when empty. */
    fun format(totals: CurrencyTotals): String =
        if (totals.isEmpty) "—"
        else totals.byCurrency.entries
            .sortedByDescending { it.value }
            .joinToString(" · ") { (c, v) -> Format.money(v, c) }
}
