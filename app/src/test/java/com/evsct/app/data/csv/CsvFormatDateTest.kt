package com.evsct.app.data.csv

import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Date parsing in [CsvFormat.fromRow]: impossible dates must skip the row,
 *  not clamp — DateTimeFormatter's default SMART resolution silently turned
 *  "2023-2-29" into Feb 28. */
class CsvFormatDateTest {

    private fun rowWithDate(date: String, time: String = "10:00:00"): CsvFormat.ParsedCsvRow? =
        CsvFormat.fromRow(listOf("date", "time"), listOf(date, time))

    @Test
    fun `valid dates parse, lenient widths included`() {
        assertNotNull(rowWithDate("2024-01-05"))
        assertNotNull(rowWithDate("2024-1-5", time = "9:30:00"))
        assertNotNull(rowWithDate("2024-02-29"))  // real leap day
    }

    @Test
    fun `impossible dates skip the row instead of clamping`() {
        assertNull(rowWithDate("2023-2-29"))   // not a leap year
        assertNull(rowWithDate("2024-4-31"))   // April has 30 days
        assertNull(rowWithDate("2024-2-30"))
        assertNull(rowWithDate("2024-13-01"))
    }
}
