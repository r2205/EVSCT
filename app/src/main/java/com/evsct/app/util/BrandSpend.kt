package com.evsct.app.util

import com.evsct.app.data.entity.ChargingSession

/**
 * "Top brands by spend" series shared by the Stats screen and the Year
 * Recap. Brands group case-insensitively (trimmed) — matching how the
 * brand picker (`observeBrands` is COLLATE NOCASE) and the list filter
 * (`equals(ignoreCase = true)`) already treat names — so sessions tagged
 * "FLO" and "Flo" make one bar, not two. Only sessions with a positive
 * cost participate.
 */
object BrandSpend {

    fun top(sessions: List<ChargingSession>, limit: Int = 8): List<Pair<String, Double>> =
        sessions
            .filter { !it.brand.isNullOrBlank() && (it.totalCost ?: 0.0) > 0 }
            .groupBy { it.brand!!.trim().lowercase() }
            .map { (_, group) -> displayName(group) to group.sumOf { it.totalCost ?: 0.0 } }
            .sortedByDescending { it.second }
            .take(limit)

    /** Label for a merged group: its most frequent original casing, ties
     *  broken by which variant was used most recently. */
    private fun displayName(group: List<ChargingSession>): String =
        group.groupBy { it.brand!!.trim() }
            .entries
            .maxWith(
                compareBy(
                    { it.value.size },
                    { it.value.maxOf { s -> s.sessionStart } },
                ),
            )
            .key
}
