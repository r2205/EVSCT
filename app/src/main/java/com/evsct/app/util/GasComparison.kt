package com.evsct.app.util

/**
 * The gas car the Stats "vs gas" card compares charging against: what fuel
 * costs and how much the car burns. Set in Settings, in the user's default
 * currency. Stored canonically in $/L and L/100 km, the way distances are
 * stored in km; [GasUnits] converts to $/gal and mpg for miles users.
 */
data class GasComparison(
    val pricePerLitre: Double = DEFAULT_PRICE_PER_LITRE,
    val litresPer100Km: Double = DEFAULT_LITRES_PER_100_KM,
) {
    /** What driving [km] would have cost in gas. */
    fun costForKm(km: Double): Double = km / 100.0 * litresPer100Km * pricePerLitre

    companion object {
        /** BC pump price in CAD, the figure the card used before it was a setting. */
        const val DEFAULT_PRICE_PER_LITRE = 2.15
        /** A thirsty-ish gas car, likewise the old hard-coded figure. */
        const val DEFAULT_LITRES_PER_100_KM = 12.0
    }
}

/** Display ↔ storage conversions for [GasComparison]: kilometre users enter
 *  $/L and L/100 km, miles users $/US gallon and US mpg. */
object GasUnits {

    const val LITRES_PER_US_GALLON = 3.785411784
    private const val KM_PER_MILE = 1.609344

    /** L/100 km × mpg = this constant (the two are reciprocal). */
    private const val L100KM_TIMES_MPG = 100.0 * LITRES_PER_US_GALLON / KM_PER_MILE

    fun priceToDisplay(pricePerLitre: Double, useMiles: Boolean): Double =
        if (useMiles) pricePerLitre * LITRES_PER_US_GALLON else pricePerLitre

    fun priceFromDisplay(display: Double, useMiles: Boolean): Double =
        if (useMiles) display / LITRES_PER_US_GALLON else display

    fun economyToDisplay(litresPer100Km: Double, useMiles: Boolean): Double =
        if (useMiles) L100KM_TIMES_MPG / litresPer100Km else litresPer100Km

    fun economyFromDisplay(display: Double, useMiles: Boolean): Double =
        if (useMiles) L100KM_TIMES_MPG / display else display

    /** "12" or "19.6": one decimal, dropped when whole, US digits like
     *  every other display number. */
    fun economyNumber(display: Double): String {
        val rounded = Math.round(display * 10) / 10.0
        return if (rounded % 1.0 == 0.0) rounded.toLong().toString()
        else "%.1f".format(java.util.Locale.US, rounded)
    }

    fun priceUnit(useMiles: Boolean): String = if (useMiles) "gal" else "L"

    fun economyUnit(useMiles: Boolean): String = if (useMiles) "mpg" else "L/100 km"
}
