package com.evsct.app.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

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
    // replacement. Give each thread its own instance.
    //
    // Symbols are pinned to US conventions (dot decimal, comma grouping)
    // rather than the device locale: the money formatters prepend a
    // literal "$", and locale-default separators produced hybrids like
    // "$1.234,56 CAD" on comma-decimal devices. Pinning all four keeps
    // money and unit displays consistent with each other and with the
    // Locale.US seeding the edit dialogs already use. Input is unaffected
    // — parseDecimal still accepts both separator conventions.
    private val moneyFmt = ThreadLocal.withInitial {
        DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))
    }
    private val rateFmt = ThreadLocal.withInitial {
        DecimalFormat("#,##0.000", DecimalFormatSymbols(Locale.US))
    }
    private val kmFmt = ThreadLocal.withInitial {
        DecimalFormat("#,##0.#", DecimalFormatSymbols(Locale.US))
    }
    private val kwhFmt = ThreadLocal.withInitial {
        DecimalFormat("#,##0.###", DecimalFormatSymbols(Locale.US))
    }

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
        // Locale pinned for the same reason as the number formats above —
        // default-locale %d can render non-ASCII digit shapes.
        return if (h > 0) "%dh %02dm".format(Locale.US, h, m)
        else "%dm %02ds".format(Locale.US, m, s)
    }

    /**
     * Parse user-typed decimal input from a text field. KeyboardType.Decimal
     * keyboards surface the locale's separator, so comma-locale users type
     * "12,5" where the app renders "12.5" — toDoubleOrNull() rejects that
     * and the value would silently save as null. Accepts both separators.
     * When both appear, the one that occurs last is the decimal point and
     * the other is a thousands separator — so US "1,234.5" and European
     * "1.234,56" both parse to their intended value. A comma-only value in
     * strict thousands grouping ("1,234", "12,345,678") reads as grouped —
     * pasted US-style odometers would otherwise silently shrink ~1000×.
     * Any other lone comma is the decimal point ("12,5", "0,485"); the
     * nonzero-leading-group requirement keeps 3-decimal fractions below 1
     * out of the grouped branch. The residual ambiguity — a comma-decimal
     * value ≥ 1 with exactly 3 decimals — resolves as thousands, since no
     * field in this app realistically takes 3-decimal input above 1.
     * Returns null for blank or unparseable input.
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
            ',' in t ->
                if (GROUPED_THOUSANDS.matches(t) && !t.trimStart('-', '+').startsWith("0"))
                    t.replace(",", "")                    // "1,234" / "12,345,678"
                else
                    t.replace(',', '.')                   // "12,5" / "0,485"
            else -> t
        }
        // toDoubleOrNull happily parses "NaN", "Infinity", and overflowing
        // literals like "1e999" (→ Infinity). A non-finite value stored in
        // the DB poisons every aggregation and makes JSON backup export
        // throw, so reject it at the boundary like any other bad input.
        return normalized.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    /** Comma-only input in strict US thousands grouping: 1–3 digit leading
     *  group, then one or more groups of exactly 3. */
    private val GROUPED_THOUSANDS = Regex("""[-+]?\d{1,3}(,\d{3})+""")
}
