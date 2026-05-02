package com.evsct.app.util

import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Format {
    private val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val dateTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    private val time = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val money = DecimalFormat("#,##0.00")
    private val rate = DecimalFormat("#,##0.000")
    private val km = DecimalFormat("#,##0.#")
    private val kwh = DecimalFormat("#,##0.###")

    fun date(epoch: Long): String = date.format(Date(epoch))
    fun dateTime(epoch: Long): String = dateTime.format(Date(epoch))
    fun time(epoch: Long): String = time.format(Date(epoch))

    fun money(value: Double?, currency: String = "CAD"): String =
        value?.let { "${'$'}${money.format(it)} $currency" } ?: "—"

    fun rate(value: Double?, suffix: String): String =
        value?.let { "${rate.format(it)} $suffix" } ?: "—"

    /** Format a money rate like "$0.385/kWh" or "$0.12/km". */
    fun moneyRate(value: Double?, perUnit: String): String =
        value?.let { "${'$'}${rate.format(it)}/$perUnit" } ?: "—"

    fun km(value: Double?): String = value?.let { "${km.format(it)} km" } ?: "—"

    /** Distance display in user-preferred unit. [valueKm] is always stored km. */
    fun distance(valueKm: Double?, useMiles: Boolean): String =
        valueKm?.let {
            val display = Units.kmToDisplay(it, useMiles)
            "${km.format(display)} ${Units.distanceUnit(useMiles)}"
        } ?: "—"

    /** Cost-per-distance display, given $/km in storage units. */
    fun moneyRatePerDistance(valuePerKm: Double?, useMiles: Boolean): String =
        valuePerKm?.let {
            val perDisplay = if (useMiles) it * 1.609344 else it
            "${'$'}${rate.format(perDisplay)}/${Units.distanceUnit(useMiles)}"
        } ?: "—"

    fun kwh(value: Double?): String = value?.let { "${kwh.format(it)} kWh" } ?: "—"
    fun kw(value: Double?): String = value?.let { "${km.format(it)} kW" } ?: "—"
    fun pct(value: Int?): String = value?.let { "$it%" } ?: "—"

    fun duration(seconds: Long?): String {
        if (seconds == null) return "—"
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%dh %02dm".format(h, m) else "%dm %02ds".format(m, s)
    }
}
