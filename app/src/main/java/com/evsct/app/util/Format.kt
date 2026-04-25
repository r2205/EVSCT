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

    fun km(value: Double?): String = value?.let { "${km.format(it)} km" } ?: "—"
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
