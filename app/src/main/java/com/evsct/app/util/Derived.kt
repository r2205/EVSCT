package com.evsct.app.util

import com.evsct.app.data.entity.ChargingSession

object Derived {
    fun effectiveEnergyPricePerKwh(s: ChargingSession): Double? {
        val cost = s.totalCost ?: return null
        val kwh = s.energyKwh ?: return null
        return if (kwh > 0) cost / kwh else null
    }

    fun effectiveTimeRatePerMin(s: ChargingSession): Double? {
        val cost = s.totalCost ?: return null
        val secs = s.durationSeconds ?: return null
        return if (secs > 0) cost / (secs / 60.0) else null
    }

    fun effectiveAvgPowerKw(s: ChargingSession): Double? {
        val kwh = s.energyKwh ?: return null
        val secs = s.durationSeconds ?: return null
        return if (secs > 0) kwh / (secs / 3600.0) else null
    }

    fun batteryDeltaPct(s: ChargingSession): Int? {
        val start = s.batteryStartPct ?: return null
        val end = s.batteryEndPct ?: return null
        return end - start
    }

    /**
     * "Stop time" = how long the user actually stayed at the station, charge
     * time plus any waiting/queueing time the user logged. Returns null when
     * neither field is set, the [waitTimeMinutes] alone (in seconds) when
     * only wait was logged, or the sum otherwise. Wait-only is useful for
     * sessions that ended up not charging (queue too long, gave up).
     */
    fun stopTimeSeconds(s: ChargingSession): Long? {
        val charge = s.durationSeconds
        val waitSec = s.waitTimeMinutes?.takeIf { it > 0 }?.let { it.toLong() * 60L }
        return when {
            charge != null && waitSec != null -> charge + waitSec
            charge != null -> charge
            waitSec != null -> waitSec
            else -> null
        }
    }
}
