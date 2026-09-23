package com.evsct.app.data.csv

import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.data.entity.PaymentMethod
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Columns whose format changed or arrived after the first export, so old
 * and new CSVs must both import.
 *
 * Wait time moved from whole minutes (`wait_minutes`) to seconds
 * (`wait_seconds`) when the field gained seconds precision. Exports write
 * only the new column; imports must accept both so pre-change CSVs keep
 * round-tripping. Payment columns are newer still: absent means unrecorded.
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

    /* ------------------------------- Payment ------------------------------- */

    @Test
    fun `payment round-trips through export and import`() {
        val row = CsvFormat.toRow(
            ChargingSession(
                sessionStart = 1_752_000_000_000L,
                paymentMethod = PaymentMethod.CREDIT_CARD,
                paymentDetail = "TD Visa",
            ),
            tripName = null,
            vehicleName = null,
        )
        assertEquals("CREDIT_CARD", row[CsvFormat.HEADERS.indexOf("payment_method")])
        assertEquals("TD Visa", row[CsvFormat.HEADERS.indexOf("payment_detail")])

        val parsed = parse(CsvFormat.HEADERS, row)?.session
        assertEquals(PaymentMethod.CREDIT_CARD, parsed?.paymentMethod)
        assertEquals("TD Visa", parsed?.paymentDetail)
    }

    @Test
    fun `payment columns are appended so older columns keep their positions`() {
        assertEquals(
            listOf("continues_previous", "payment_method", "payment_detail"),
            CsvFormat.HEADERS.takeLast(3),
        )
    }

    @Test
    fun `a hand-typed method imports regardless of case`() {
        val parsed = parse(
            listOf("date", "payment_method", "payment_detail"),
            listOf("2026-07-29", "mobile_wallet", "Google Pay"),
        )
        assertEquals(PaymentMethod.MOBILE_WALLET, parsed?.session?.paymentMethod)
        assertEquals("Google Pay", parsed?.session?.paymentDetail)
    }

    @Test
    fun `an unknown method drops the detail with it`() {
        // A detail is never stored without its method — the form would have
        // nowhere to show it.
        val parsed = parse(
            listOf("date", "payment_method", "payment_detail"),
            listOf("2026-07-29", "CHEQUE", "Chequing"),
        )
        assertNull(parsed?.session?.paymentMethod)
        assertNull(parsed?.session?.paymentDetail)
    }

    @Test
    fun `older exports without payment columns import unrecorded`() {
        val parsed = parse(listOf("date", "time"), listOf("2026-07-29", "10:00:00"))
        assertNull(parsed?.session?.paymentMethod)
        assertNull(parsed?.session?.paymentDetail)
    }
}
