package com.evsct.app.data.csv

import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.data.entity.ChargingType
import com.evsct.app.data.entity.PricingModel
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object CsvFormat {
    val HEADERS = listOf(
        "id",
        "date",
        "time",
        "duration_seconds",
        "wait_minutes",
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
        "vehicle_name",
        "notes",
        "tags",
        "latitude",
        "longitude",
        "continues_previous",
    )

    // DateTimeFormatter is thread-safe (unlike the SimpleDateFormats these
    // replaced, which shared mutable state across threads) and the zone is
    // read per call instead of being frozen at class-load time, so exports
    // stay correct if the device changes timezone mid-process. Locale.US
    // pins digit shapes so the CSV stays machine-readable everywhere. The
    // parse pattern uses single-letter fields (1–2 digit widths) so
    // hand-edited values like "2024-1-5 9:30:00" still import; the old
    // lenient SimpleDateFormat rolled impossible dates over instead of
    // rejecting the row — strict resolution now skips them as malformed.
    private val isoDate = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)
    private val isoTime = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US)
    private val isoDateTimeParse = DateTimeFormatter.ofPattern("yyyy-M-d H:m:s", Locale.US)

    fun toRow(session: ChargingSession, tripName: String?, vehicleName: String?): List<String?> {
        val date = Instant.ofEpochMilli(session.sessionStart).atZone(ZoneId.systemDefault())
        return listOf(
            session.id.toString(),
            isoDate.format(date),
            isoTime.format(date),
            session.durationSeconds?.toString(),
            session.waitTimeMinutes?.toString(),
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
            vehicleName,
            session.notes,
            session.tags,
            session.latitude?.toString(),
            session.longitude?.toString(),
            if (session.continuesPrevious) "true" else "false",
        )
    }

    data class ParsedCsvRow(
        val session: ChargingSession,
        val tripName: String?,
        val vehicleName: String?,
    )

    /** Returns the parsed session plus the trip and vehicle names (caller resolves IDs). */
    fun fromRow(headers: List<String>, row: List<String?>): ParsedCsvRow? {
        fun get(name: String): String? {
            val idx = headers.indexOf(name)
            if (idx < 0 || idx >= row.size) return null
            val v = row[idx]?.trim()
            return if (v.isNullOrEmpty()) null else v
        }
        val date = get("date") ?: return null
        val time = get("time") ?: "00:00:00"
        val epoch = runCatching {
            LocalDateTime.parse("$date $time", isoDateTimeParse)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }.getOrNull() ?: return null

        val type = get("charging_type")?.let { runCatching { ChargingType.valueOf(it) }.getOrNull() } ?: ChargingType.DC_FAST
        val pricing = get("pricing_model")?.let { runCatching { PricingModel.valueOf(it) }.getOrNull() } ?: PricingModel.PER_KWH

        val session = ChargingSession(
            id = 0,
            sessionStart = epoch,
            durationSeconds = get("duration_seconds")?.toLongOrNull(),
            // Columns absent from older exports simply read null here, so
            // pre-wait/tags CSVs import unchanged.
            waitTimeMinutes = get("wait_minutes")?.toIntOrNull(),
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
            tags = get("tags"),
            latitude = get("latitude")?.toDoubleOrNull(),
            longitude = get("longitude")?.toDoubleOrNull(),
            continuesPrevious = get("continues_previous")?.equals("true", ignoreCase = true) ?: false,
        )
        // Hand-edited CSVs can carry physically impossible values (battery
        // 8500%, negative odometer, latitude 400). Nobody sees advisory
        // hints on this path, so drop them at the boundary.
        return ParsedCsvRow(ImportSanitizer.sanitize(session), get("trip_name"), get("vehicle_name"))
    }
}
