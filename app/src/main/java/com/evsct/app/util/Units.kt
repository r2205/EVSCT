package com.evsct.app.util

object Units {
    private const val KM_PER_MILE = 1.609344

    fun kmToMi(km: Double): Double = km / KM_PER_MILE
    fun miToKm(mi: Double): Double = mi * KM_PER_MILE

    fun kmToDisplay(km: Double, useMiles: Boolean): Double =
        if (useMiles) kmToMi(km) else km

    fun displayToKm(value: Double, useMiles: Boolean): Double =
        if (useMiles) miToKm(value) else value

    fun distanceUnit(useMiles: Boolean): String = if (useMiles) "mi" else "km"
}
