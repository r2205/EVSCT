package com.evsct.app.util

import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.data.entity.PaymentMethod

/** One way the user has paid before: a method, optionally narrowed to a
 *  specific card, app or account. */
data class PaymentUse(val method: PaymentMethod, val detail: String?)

/**
 * Payment reuse for the session form. People pay with the same two or
 * three cards and apps over and over, so the form offers what was used
 * before as one-tap chips instead of making them retype "TD Visa" — and,
 * like tags, reuse keeps one card from splintering into several spellings
 * that a search would have to chase separately.
 */
object PaymentHistory {

    /** How many whole-payment shortcuts the form offers before a method is
     *  picked — one scrollable row. */
    const val MAX_RECENT = 6

    /**
     * Every distinct way [sessions] were paid, most recently used first
     * (ties broken by how often, then A–Z). The same detail under two
     * methods — "TD Visa" tapped as a card and through a phone wallet — is
     * two entries, because it is two different answers to "how did I pay".
     *
     * Details merge case-insensitively and surface in the casing used most
     * often, ties going to the most recent — the rule [TagSuggestions]
     * uses for tags. Sessions without a method are skipped.
     */
    fun uses(sessions: List<ChargingSession>): List<PaymentUse> =
        sessions.asSequence()
            .mapNotNull { s ->
                val method = s.paymentMethod ?: return@mapNotNull null
                Use(method, s.paymentDetail?.trim()?.takeIf { it.isNotEmpty() }, s.sessionStart)
            }
            .groupBy { it.method to it.detail?.lowercase() }
            .map { (key, uses) ->
                Ranked(
                    use = PaymentUse(key.first, displayDetail(uses)),
                    count = uses.size,
                    lastUsedAt = uses.maxOf { it.at },
                )
            }
            .sortedWith(
                compareByDescending<Ranked> { it.lastUsedAt }
                    .thenByDescending { it.count }
                    .thenBy { it.use.detail?.lowercase().orEmpty() },
            )
            .map { it.use }

    /** The details used with [method], most recent first — what the detail
     *  field offers once a method is picked. Order is [uses]' order, which
     *  filtering preserves. */
    fun details(uses: List<PaymentUse>, method: PaymentMethod): List<String> =
        uses.filter { it.method == method }.mapNotNull { it.detail }

    /** The casing to label a merged detail with: most frequent, then most
     *  recent. Null when the group is the method used without a detail. */
    private fun displayDetail(uses: List<Use>): String? =
        uses.filter { it.detail != null }
            .groupBy { it.detail }
            .entries
            .maxWithOrNull(
                compareBy(
                    { it.value.size },
                    { it.value.maxOf { use -> use.at } },
                ),
            )
            ?.key

    private data class Use(val method: PaymentMethod, val detail: String?, val at: Long)

    private data class Ranked(val use: PaymentUse, val count: Int, val lastUsedAt: Long)
}
