package com.evsct.app.data.csv

import com.evsct.app.data.entity.ChargingSession
import kotlin.math.roundToInt

/**
 * Range gate for sessions arriving from files (CSV / XLSX). The edit screen
 * shows a human advisory hints and lets them decide; an import has nobody
 * watching, so physically impossible values are dropped (nulled) instead of
 * silently poisoning stats, efficiency legs, and the recap downstream.
 *
 * Only the impossible is dropped — merely unusual values are kept. Notably
 * a negative totalCost survives: the edit screen deliberately accepts it
 * (refund/credit), so an export → import round-trip must not destroy it.
 */
object ImportSanitizer {

    fun sanitize(session: ChargingSession): ChargingSession = session.copy(
        durationSeconds = session.durationSeconds?.takeIf { it >= 0 },
        waitTimeMinutes = session.waitTimeMinutes?.takeIf { it >= 0 },
        odometerKm = session.odometerKm?.takeIf { it.isFinite() && it >= 0 },
        energyKwh = session.energyKwh?.takeIf { it.isFinite() && it >= 0 },
        totalCost = session.totalCost?.takeIf { it.isFinite() },
        postedEnergyPricePerKwh = session.postedEnergyPricePerKwh
            ?.takeIf { it.isFinite() && it >= 0 },
        postedTimeRatePerMin = session.postedTimeRatePerMin
            ?.takeIf { it.isFinite() && it >= 0 },
        postedMaxPowerKw = session.postedMaxPowerKw?.takeIf { it.isFinite() && it >= 0 },
        batteryStartPct = session.batteryStartPct?.takeIf { it in 0..100 },
        batteryEndPct = session.batteryEndPct?.takeIf { it in 0..100 },
        latitude = session.latitude?.takeIf { it.isFinite() && it in -90.0..90.0 },
        longitude = session.longitude?.takeIf { it.isFinite() && it in -180.0..180.0 },
    )

    /**
     * Interpret a numeric spreadsheet cell as a battery percent.
     *
     * %-formatted cells store 85% as 0.85, so those always scale by 100.
     * Unformatted cells are ambiguous: strictly-below-1 reads as a fraction
     * (0.85 → 85%), 1 and above as an already-scaled percent (85 → 85%).
     * The old unconditional ×100 turned a plain 85 into 8500%; exactly 1 in
     * an unformatted cell is far more plausibly a genuine 1% reading than a
     * fractional 100%, so the fraction branch's upper bound is exclusive.
     * Results outside 0–100 are dropped.
     */
    fun cellToPercent(raw: Double, isPercentFormatted: Boolean): Int? {
        if (!raw.isFinite()) return null
        val pct = when {
            isPercentFormatted -> raw * 100
            raw >= 0.0 && raw < 1.0 -> raw * 100
            else -> raw
        }
        // Reject negatives before rounding — a -0.05 must not round to 0%
        // and sneak through as a real reading.
        if (pct < 0) return null
        return pct.roundToInt().takeIf { it <= 100 }
    }

    /**
     * Interpret a numeric spreadsheet cell as a time of day: seconds since
     * midnight. Excel stores time as a 0–1 fraction of a day, but a cell
     * accidentally holding a full datetime serial (45123.5 instead of 0.5)
     * still carries the time in its fractional part — and multiplying the
     * whole serial by 86 400 used to overflow Int and shift the imported
     * session backward by decades. Negatives and non-finite values return
     * null (caller keeps midnight).
     */
    fun cellToTimeOfDaySeconds(raw: Double): Int? {
        if (!raw.isFinite() || raw < 0) return null
        return ((raw % 1.0) * 24 * 3600).toInt()
    }

    /**
     * Interpret a numeric duration cell (fraction of a day) as seconds.
     * Negatives, non-finite values, and a day or more are malformed — no
     * real charging session runs 24 h, and a full datetime serial here
     * would otherwise import as a ~45,000-day duration.
     */
    fun cellToDurationSeconds(raw: Double): Long? {
        if (!raw.isFinite() || raw < 0 || raw >= 1.0) return null
        return (raw * 24 * 3600).toLong()
    }
}
