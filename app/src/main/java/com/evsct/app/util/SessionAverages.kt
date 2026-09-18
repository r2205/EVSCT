package com.evsct.app.util

import com.evsct.app.data.entity.ChargingSession

/**
 * Fleet-wide averages that only make sense as a ratio of two sums taken
 * over the SAME sessions. Shared by the Stats headline and the vehicle
 * detail card so the two screens can't disagree.
 *
 * Summing the numerator over every session but the denominator over only
 * the sessions that recorded it inflates the ratio arbitrarily: ten 50 kWh
 * charges with a single recorded hour read as 500 kW, and a $40 charge
 * whose kWh was never logged pushes the average price up with no energy
 * to spread it over. Imports routinely lack durations, and quick logs
 * routinely lack one of cost or kWh, so this isn't a corner case.
 *
 * Each average therefore pairs per session: a session contributes to the
 * ratio only when it carries BOTH sides, and contributes nothing otherwise.
 */
object SessionAverages {

    /** Energy delivered per hour of charging, in kW, across the sessions
     *  that recorded both a kWh figure and a positive duration. Null when
     *  no session qualifies. */
    fun avgPowerKw(sessions: List<ChargingSession>): Double? {
        var kwh = 0.0
        var hours = 0.0
        for (s in sessions) {
            val energy = s.energyKwh ?: continue
            val secs = s.durationSeconds ?: continue
            if (secs <= 0L) continue
            kwh += energy
            hours += secs / 3600.0
        }
        return if (hours > 0) kwh / hours else null
    }

    /** Money paid per kWh delivered, across the sessions that recorded both
     *  a cost and a positive kWh figure. A free session (cost 0.0) with
     *  energy logged legitimately pulls the average down — the user did
     *  get those kWh for nothing — while a session with a cost but no kWh
     *  is left out rather than inflating the price of everyone else's
     *  energy. The caller is expected to pass a single-currency list; the
     *  ratio has no unit otherwise. Null when no session qualifies. */
    fun avgEffectivePricePerKwh(sessions: List<ChargingSession>): Double? {
        var cost = 0.0
        var kwh = 0.0
        for (s in sessions) {
            val c = s.totalCost ?: continue
            val e = s.energyKwh ?: continue
            if (e <= 0.0) continue
            cost += c
            kwh += e
        }
        return if (kwh > 0) cost / kwh else null
    }
}
