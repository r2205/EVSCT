package com.evsct.app.util

import com.evsct.app.data.entity.ChargingSession

/**
 * Distance driven inside a time window, derived from odometer readings.
 * Shared by the Stats month card and the Year Recap so the two screens
 * can't disagree.
 *
 * Adjacent same-vehicle sessions form intervals; each interval's odometer
 * delta is attributed to the window *prorated by time overlap* — the
 * fraction of [prev.sessionStart, curr.sessionStart] that falls inside
 * [windowStart, windowEnd). Assumes driving is spread evenly across the
 * interval, which is the only defensible guess without more data.
 *
 * Why prorate instead of crediting the whole delta to the bucket holding
 * the interval's end (the old behavior):
 *  - a delta ending on the 2nd after a charge on the 30th splits across
 *    both months instead of double-counting one and starving the other;
 *  - a months-long logging gap no longer dumps all of its distance into
 *    the single bucket where logging resumed;
 *  - an interval that spans a whole bucket (no charges that month at all)
 *    now credits that bucket its share instead of zero;
 *  - buckets tile: summing consecutive windows reproduces the exact total.
 *
 * Negative deltas (odometer rollback/typo) and pairs missing a reading
 * are skipped, as before.
 */
object OdometerDistance {

    fun inWindow(
        sessions: List<ChargingSession>,
        windowStart: Long,
        windowEnd: Long,
    ): Double {
        var total = 0.0
        sessions.groupBy { it.vehicleId }
            .values
            .map { group -> group.sortedBy { it.sessionStart } }
            .forEach { sorted ->
                for (i in 1 until sorted.size) {
                    val prev = sorted[i - 1]
                    val curr = sorted[i]
                    val prevOdo = prev.odometerKm ?: continue
                    val currOdo = curr.odometerKm ?: continue
                    val delta = currOdo - prevOdo
                    if (delta <= 0) continue
                    total += delta * overlapFraction(
                        intervalStart = prev.sessionStart,
                        intervalEnd = curr.sessionStart,
                        windowStart = windowStart,
                        windowEnd = windowEnd,
                    )
                }
            }
        return total
    }

    private fun overlapFraction(
        intervalStart: Long,
        intervalEnd: Long,
        windowStart: Long,
        windowEnd: Long,
    ): Double {
        // Degenerate interval (same timestamp): all-or-nothing on whether
        // it sits inside the window.
        if (intervalEnd <= intervalStart) {
            return if (intervalStart in windowStart until windowEnd) 1.0 else 0.0
        }
        val overlapStart = maxOf(intervalStart, windowStart)
        val overlapEnd = minOf(intervalEnd, windowEnd)
        if (overlapEnd <= overlapStart) return 0.0
        return (overlapEnd - overlapStart).toDouble() / (intervalEnd - intervalStart).toDouble()
    }
}
