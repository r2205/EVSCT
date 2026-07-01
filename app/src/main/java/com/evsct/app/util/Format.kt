package com.evsct.app.util

import java.text.DecimalFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object Format {
    // DateTimeFormatter is thread-safe by design — share these statics across
    // every caller (UI thread, IO thread for PDF/CSV generation, etc.).
    // SimpleDateFormat is NOT, which is what we used to use here; concurrent
    // .format() calls from the PDF generator and UI could throw or produce
    // garbled output.
    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val dateTimeFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    // DecimalFormat is also not thread-safe and has no java.time-style
    // replacement. Give each thread its own instance; cheap and matches the
    // locale-default behavior the old shared instances had.
    private val moneyFmt = ThreadLocal.withInitial { DecimalFormat("#,##0.00") }
    private val rateFmt = ThreadLocal.withInitial { DecimalFormat("#,##0.000") }
    private val kmFmt = ThreadLocal.withInitial { DecimalFormat("#,##0.#") }
    private val kwhFmt = ThreadLocal.withInitial { DecimalFormat("#,##0.###") }

    private fun zonedAt(epoch: Long) =
        Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault())

    fun date(epoch: Long): String = dateFmt.format(zonedAt(epoch))
    fun dateTime(epoch: Long): String = dateTimeFmt.format(zonedAt(epoch))
    fun time(epoch: Long): String = timeFmt.format(zonedAt(epoch))

    fun money(value: Double?, currency: String = "CAD"): String =
        value?.let { "${'$'}${moneyFmt.get()!!.format(it)} $currency" } ?: "—"

    fun rate(value: Double?, suffix: String): String =
        value?.let { "${rateFmt.get()!!.format(it)} $suffix" } ?: "—"

    /** Format a money rate like "$0.385/kWh" or "$0.12/km". */
    fun moneyRate(value: Double?, perUnit: String): String =
        value?.let { "${'$'}${rateFmt.get()!!.format(it)}/$perUnit" } ?: "—"

    /** Cost-per-time display like "$0.27/min" or "$27.00/hr". Time rates read
     *  as ordinary currency, so use 2-decimal money precision instead of the
     *  3-decimal energy-rate precision [moneyRate] uses. */
    fun moneyPerTime(value: Double?, perUnit: String): String =
        value?.let { "${'$'}${moneyFmt.get()!!.format(it)}/$perUnit" } ?: "—"

    fun km(value: Double?): String = value?.let { "${kmFmt.get()!!.format(it)} km" } ?: "—"

    /** Distance display in user-preferred unit. [valueKm] is always stored km. */
    fun distance(valueKm: Double?, useMiles: Boolean): String =
        valueKm?.let {
            val display = Units.kmToDisplay(it, useMiles)
            "${kmFmt.get()!!.format(display)} ${Units.distanceUnit(useMiles)}"
        } ?: "—"

    /** Cost-per-distance display, given $/km in storage units. */
    fun moneyRatePerDistance(valuePerKm: Double?, useMiles: Boolean): String =
        valuePerKm?.let {
            val perDisplay = if (useMiles) it * 1.609344 else it
            "${'$'}${rateFmt.get()!!.format(perDisplay)}/${Units.distanceUnit(useMiles)}"
        } ?: "—"

    fun kwh(value: Double?): String = value?.let { "${kwhFmt.get()!!.format(it)} kWh" } ?: "—"
    fun kw(value: Double?): String = value?.let { "${kmFmt.get()!!.format(it)} kW" } ?: "—"
    fun pct(value: Int?): String = value?.let { "$it%" } ?: "—"

    fun duration(seconds: Long?): String {
        if (seconds == null) return "—"
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%dh %02dm".format(h, m) else "%dm %02ds".format(m, s)
    }

    /**
     * Parse user-typed decimal input from a text field. KeyboardType.Decimal
     * keyboards surface the locale's separator, so comma-locale users type
     * "12,5" where the app renders "12.5" — toDoubleOrNull() rejects that
     * and the value would silently save as null. Accepts both separators:
     * a lone comma is treated as the decimal point; when both appear, the
     * one that occurs last is the decimal point and the other is a
     * thousands separator — so US "1,234.5" and European "1.234,56" both
     * parse to their intended value. Returns null for blank or
     * unparseable input.
     */
    fun parseDecimal(text: String): Double? {
        val t = text.trim()
        if (t.isEmpty()) return null
        val normalized = when {
            ',' in t && '.' in t ->
                if (t.lastIndexOf(',') > t.lastIndexOf('.'))
                    t.replace(".", "").replace(',', '.')  // European: dot-thousands, comma-decimal
                else
                    t.replace(",", "")                    // US: comma-thousands, dot-decimal
            ',' in t -> t.replace(',', '.')
            else -> t
        }
        return normalized.toDoubleOrNull()
    }
}
