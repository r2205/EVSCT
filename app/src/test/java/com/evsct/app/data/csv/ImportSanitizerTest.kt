package com.evsct.app.data.csv

import com.evsct.app.data.entity.ChargingSession
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ImportSanitizerTest {

    private val base = ChargingSession(sessionStart = 0)

    // --- sanitize: fields that must be dropped ---

    @Test
    fun `battery percent outside 0-100 is dropped`() {
        val s = ImportSanitizer.sanitize(
            base.copy(batteryStartPct = 8500, batteryEndPct = -5),
        )
        assertNull(s.batteryStartPct)
        assertNull(s.batteryEndPct)
    }

    @Test
    fun `battery percent at the bounds is kept`() {
        val s = ImportSanitizer.sanitize(base.copy(batteryStartPct = 0, batteryEndPct = 100))
        assertEquals(0, s.batteryStartPct)
        assertEquals(100, s.batteryEndPct)
    }

    @Test
    fun `negative duration wait odometer and energy are dropped`() {
        val s = ImportSanitizer.sanitize(
            base.copy(
                durationSeconds = -100,
                waitTimeMinutes = -5,
                odometerKm = -1.0,
                energyKwh = -20.0,
            ),
        )
        assertNull(s.durationSeconds)
        assertNull(s.waitTimeMinutes)
        assertNull(s.odometerKm)
        assertNull(s.energyKwh)
    }

    @Test
    fun `negative posted rates are dropped`() {
        val s = ImportSanitizer.sanitize(
            base.copy(
                postedEnergyPricePerKwh = -0.5,
                postedTimeRatePerMin = -0.2,
                postedMaxPowerKw = -50.0,
            ),
        )
        assertNull(s.postedEnergyPricePerKwh)
        assertNull(s.postedTimeRatePerMin)
        assertNull(s.postedMaxPowerKw)
    }

    @Test
    fun `out-of-range coordinates are dropped independently`() {
        val s = ImportSanitizer.sanitize(base.copy(latitude = 95.0, longitude = -79.4))
        assertNull(s.latitude)
        assertEquals(-79.4, s.longitude)

        val t = ImportSanitizer.sanitize(base.copy(latitude = 43.6, longitude = 400.0))
        assertEquals(43.6, t.latitude)
        assertNull(t.longitude)
    }

    @Test
    fun `non-finite doubles are dropped`() {
        val s = ImportSanitizer.sanitize(
            base.copy(
                odometerKm = Double.NaN,
                energyKwh = Double.POSITIVE_INFINITY,
                totalCost = Double.NEGATIVE_INFINITY,
            ),
        )
        assertNull(s.odometerKm)
        assertNull(s.energyKwh)
        assertNull(s.totalCost)
    }

    // --- sanitize: values that must survive ---

    @Test
    fun `negative cost survives - the edit screen allows refunds`() {
        val s = ImportSanitizer.sanitize(base.copy(totalCost = -5.0))
        assertEquals(-5.0, s.totalCost)
    }

    @Test
    fun `an ordinary valid session passes through unchanged`() {
        val full = base.copy(
            durationSeconds = 1800,
            waitTimeMinutes = 10,
            odometerKm = 42_500.0,
            energyKwh = 48.2,
            totalCost = 21.50,
            postedEnergyPricePerKwh = 0.45,
            postedTimeRatePerMin = 0.30,
            postedMaxPowerKw = 150.0,
            batteryStartPct = 18,
            batteryEndPct = 80,
            latitude = 43.65,
            longitude = -79.38,
        )
        assertEquals(full, ImportSanitizer.sanitize(full))
    }

    // --- cellToPercent (XLSX battery heuristic) ---

    @Test
    fun `percent-formatted fraction scales by 100`() {
        assertEquals(85, ImportSanitizer.cellToPercent(0.85, isPercentFormatted = true))
        assertEquals(86, ImportSanitizer.cellToPercent(0.856, isPercentFormatted = true))
        assertEquals(100, ImportSanitizer.cellToPercent(1.0, isPercentFormatted = true))
    }

    @Test
    fun `unformatted plain number is already a percent`() {
        // The old unconditional x100 turned this into 8500.
        assertEquals(85, ImportSanitizer.cellToPercent(85.0, isPercentFormatted = false))
        assertEquals(100, ImportSanitizer.cellToPercent(100.0, isPercentFormatted = false))
    }

    @Test
    fun `unformatted fraction still reads as a fraction`() {
        assertEquals(85, ImportSanitizer.cellToPercent(0.85, isPercentFormatted = false))
    }

    @Test
    fun `results outside 0-100 are dropped`() {
        // 85 in a %-formatted cell means 8500% — impossible.
        assertNull(ImportSanitizer.cellToPercent(85.0, isPercentFormatted = true))
        assertNull(ImportSanitizer.cellToPercent(101.0, isPercentFormatted = false))
        assertNull(ImportSanitizer.cellToPercent(-0.05, isPercentFormatted = false))
        assertNull(ImportSanitizer.cellToPercent(Double.NaN, isPercentFormatted = false))
    }

    // --- cellToTimeOfDaySeconds (XLSX time cells) ---

    @Test
    fun `plain time fraction converts to seconds since midnight`() {
        assertEquals(12 * 3600, ImportSanitizer.cellToTimeOfDaySeconds(0.5))
        assertEquals(0, ImportSanitizer.cellToTimeOfDaySeconds(0.0))
        // 23:59:59
        assertEquals(86_399, ImportSanitizer.cellToTimeOfDaySeconds(0.99999))
    }

    @Test
    fun `full datetime serial in a time cell keeps its time of day`() {
        // 45123.5 = some date at noon. The old code multiplied the whole
        // serial by 86400 and overflowed Int, shifting sessions decades
        // backward; the fractional part is the intended time.
        assertEquals(12 * 3600, ImportSanitizer.cellToTimeOfDaySeconds(45_123.5))
    }

    @Test
    fun `negative or non-finite time cells are rejected`() {
        assertNull(ImportSanitizer.cellToTimeOfDaySeconds(-0.25))
        assertNull(ImportSanitizer.cellToTimeOfDaySeconds(Double.NaN))
        assertNull(ImportSanitizer.cellToTimeOfDaySeconds(Double.POSITIVE_INFINITY))
    }

    // --- cellToDurationSeconds (XLSX duration cells) ---

    @Test
    fun `duration fraction converts to seconds`() {
        // 45 minutes = 0.03125 of a day.
        assertEquals(2_700L, ImportSanitizer.cellToDurationSeconds(0.03125))
        assertEquals(0L, ImportSanitizer.cellToDurationSeconds(0.0))
    }

    @Test
    fun `durations of a day or more are rejected`() {
        // A full datetime serial here would read as ~45,000 days.
        assertNull(ImportSanitizer.cellToDurationSeconds(1.0))
        assertNull(ImportSanitizer.cellToDurationSeconds(45_123.5))
    }

    @Test
    fun `negative or non-finite durations are rejected`() {
        assertNull(ImportSanitizer.cellToDurationSeconds(-0.1))
        assertNull(ImportSanitizer.cellToDurationSeconds(Double.NaN))
    }

    // --- CSV import path applies the gate end-to-end ---

    @Test
    fun `csv row with impossible values imports with those fields nulled`() {
        val parsed = fromCsv(
            "date" to "2024-01-05",
            "time" to "10:00:00",
            "battery_start_pct" to "8500",
            "battery_end_pct" to "80",
            "energy_kwh" to "-5",
            "odometer_km" to "-1",
            "duration_seconds" to "-100",
            "total_cost" to "-5.25",
            "latitude" to "95",
            "longitude" to "-79.4",
        )
        assertNotNull(parsed)
        val s = parsed.session
        assertNull(s.batteryStartPct)
        assertEquals(80, s.batteryEndPct)
        assertNull(s.energyKwh)
        assertNull(s.odometerKm)
        assertNull(s.durationSeconds)
        assertEquals(-5.25, s.totalCost) // refunds survive
        assertNull(s.latitude)
        assertEquals(-79.4, s.longitude)
    }

    @Test
    fun `csv row with normal values is untouched`() {
        val parsed = fromCsv(
            "date" to "2024-01-05",
            "time" to "10:00:00",
            "battery_start_pct" to "18",
            "battery_end_pct" to "80",
            "energy_kwh" to "48.2",
            "odometer_km" to "42500",
        )
        assertNotNull(parsed)
        val s = parsed.session
        assertEquals(18, s.batteryStartPct)
        assertEquals(80, s.batteryEndPct)
        assertEquals(48.2, s.energyKwh)
        assertEquals(42500.0, s.odometerKm)
    }

    private fun fromCsv(vararg fields: Pair<String, String>): CsvFormat.ParsedCsvRow? {
        val byName = fields.toMap()
        val row = CsvFormat.HEADERS.map { byName[it] }
        return CsvFormat.fromRow(CsvFormat.HEADERS, row)
    }
}
