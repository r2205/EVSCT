package com.evsct.app.data.csv

import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.data.entity.ChargingType
import com.evsct.app.data.entity.PricingModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object CsvFormat {
    val HEADERS = listOf(
        "id",
        "date",
        "time",
        "duration_seconds",
        "odometer_km",
        "energy_kwh",
        "total_cost",
        "currency",
        "posted_energy_price_per_kwh",
        "posted_time_rate_per_min",
        "posted_max_power_kw",
        "battery_start_pct",
        "battery_end_pct",
        "charging_type",
        "pricing_model",
        "brand",
        "city",
        "province",
        "address",
        "station_name",
        "stall_name",
        "trip_name",
        "notes",
    )

    private val isoDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getDefault() }
    private val isoTime = SimpleDateFormat("HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getDefault() }
    private val isoDateTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getDefault() }

    fun toRow(session: ChargingSession, tripName: String?): List<String?> {
        val date = Date(session.sessionStart)
        return listOf(
            session.id.toString(),
            isoDate.format(date),
            isoTime.format(date),
            session.durationSeconds?.toString(),
            session.odometerKm?.toString(),
            session.energyKwh?.toString(),
            session.totalCost?.toString(),
            session.currency,
            session.postedEnergyPricePerKwh?.toString(),
            session.postedTimeRatePerMin?.toString(),
            session.postedMaxPowerKw?.toString(),
            session.batteryStartPct?.toString(),
            session.batteryEndPct?.toString(),
            session.chargingType.name,
            session.pricingModel.name,
            session.brand,
            session.locationCity,
            session.locationProvince,
            session.locationAddress,
            session.stationName,
            session.stallName,
            tripName,
            session.notes,
        )
    }

    /** Returns the parsed session and an optional trip name (caller resolves trip ID). */
    fun fromRow(headers: List<String>, row: List<String?>): Pair<ChargingSession, String?>? {
        fun get(name: String): String? {
            val idx = headers.indexOf(name)
            if (idx < 0 || idx >= row.size) return null
            val v = row[idx]?.trim()
            return if (v.isNullOrEmpty()) null else v
        }
        val date = get("date") ?: return null
        val time = get("time") ?: "00:00:00"
        val epoch = runCatching { isoDateTime.parse("$date $time")?.time }.getOrNull() ?: return null

        val type = get("charging_type")?.let { runCatching { ChargingType.valueOf(it) }.getOrNull() } ?: ChargingType.DC_FAST
        val pricing = get("pricing_model")?.let { runCatching { PricingModel.valueOf(it) }.getOrNull() } ?: PricingModel.PER_KWH

        val session = ChargingSession(
            id = 0,
            sessionStart = epoch,
            durationSeconds = get("duration_seconds")?.toLongOrNull(),
            odometerKm = get("odometer_km")?.toDoubleOrNull(),
            energyKwh = get("energy_kwh")?.toDoubleOrNull(),
            totalCost = get("total_cost")?.toDoubleOrNull(),
            currency = get("currency") ?: "CAD",
            postedEnergyPricePerKwh = get("posted_energy_price_per_kwh")?.toDoubleOrNull(),
            postedTimeRatePerMin = get("posted_time_rate_per_min")?.toDoubleOrNull(),
            postedMaxPowerKw = get("posted_max_power_kw")?.toDoubleOrNull(),
            batteryStartPct = get("battery_start_pct")?.toIntOrNull(),
            batteryEndPct = get("battery_end_pct")?.toIntOrNull(),
            chargingType = type,
            pricingModel = pricing,
            brand = get("brand"),
            locationCity = get("city"),
            locationProvince = get("province"),
            locationAddress = get("address"),
            stationName = get("station_name"),
            stallName = get("stall_name"),
            notes = get("notes"),
        )
        return session to get("trip_name")
    }
}
