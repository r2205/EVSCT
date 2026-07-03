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
     * Unformatted cells are ambiguous: 0–1 reads as a fraction (0.85 → 85%),
     * anything larger as an already-scaled percent (85 → 85%). The old
     * unconditional ×100 turned a plain 85 into 8500%. Results outside
     * 0–100 are dropped.
     */
    fun cellToPercent(raw: Double, isPercentFormatted: Boolean): Int? {
        if (!raw.isFinite()) return null
        val pct = when {
            isPercentFormatted -> raw * 100
            raw in 0.0..1.0 -> raw * 100
            else -> raw
        }
        // Reject negatives before rounding — a -0.05 must not round to 0%
        // and sneak through as a real reading.
        if (pct < 0) return null
        return pct.roundToInt().takeIf { it <= 100 }
    }
}
