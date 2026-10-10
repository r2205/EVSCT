package com.evsct.app.data.csv

import com.evsct.app.data.entity.ChargingSession

/**
 * Range gate for sessions arriving from a CSV file. The edit screen shows a
 * human advisory hints and lets them decide; an import has nobody watching,
 * so physically impossible values are dropped (nulled) instead of silently
 * poisoning stats, efficiency legs, and the recap downstream.
 *
 * Only the impossible is dropped — merely unusual values are kept. Notably
 * a negative totalCost survives: the edit screen deliberately accepts it
 * (refund/credit), so an export → import round-trip must not destroy it.
 */
object ImportSanitizer {

    fun sanitize(session: ChargingSession): ChargingSession = session.copy(
        durationSeconds = session.durationSeconds?.takeIf { it >= 0 },
        waitTimeSeconds = session.waitTimeSeconds?.takeIf { it >= 0 },
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
}
