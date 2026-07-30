package com.evsct.app.data.csv

import com.evsct.app.data.entity.ChargingSession
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Wait time moved from whole minutes (`wait_minutes`) to seconds
 * (`wait_seconds`) when the field gained seconds precision. Exports write
 * only the new column; imports must accept both so pre-change CSVs keep
 * round-tripping.
 */
class CsvFormatTest {

    private fun parse(headers: List<String>, values: List<String?>) =
        CsvFormat.fromRow(headers, values)

    @Test
    fun `wait_seconds imports as-is`() {
        val parsed = parse(
            listOf("date", "time", "wait_seconds"),
            listOf("2026-07-29", "10:00:00", "434"),
        )
        assertEquals(434L, parsed?.session?.waitTimeSeconds)
    }

    @Test
    fun `legacy wait_minutes imports converted to seconds`() {
        val parsed = parse(
            listOf("date", "time", "wait_minutes"),
            listOf("2026-07-29", "10:00:00", "7"),
        )
        assertEquals(7 * 60L, parsed?.session?.waitTimeSeconds)
    }

    @Test
    fun `export writes wait_seconds`() {
        val row = CsvFormat.toRow(
            ChargingSession(sessionStart = 1_752_000_000_000L, waitTimeSeconds = 434),
            tripName = null,
            vehicleName = null,
        )
        assertEquals("434", row[CsvFormat.HEADERS.indexOf("wait_seconds")])
    }
}
