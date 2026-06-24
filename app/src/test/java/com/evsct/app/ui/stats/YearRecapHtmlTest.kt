package com.evsct.app.ui.stats

import com.evsct.app.data.prefs.UserUnits
import java.io.ByteArrayOutputStream
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class YearRecapHtmlTest {

    private fun render(ui: YearRecapUi, units: UserUnits = UserUnits()): String {
        val out = ByteArrayOutputStream()
        writeYearRecapHtml(out, ui, units)
        return out.toString(Charsets.UTF_8.name())
    }

    private fun months(vararg values: Double): List<Pair<String, Double>> =
        // 12 entries to match the recap's Jan→Dec series shape.
        List(12) { i -> ("M${i + 1}") to (values.getOrNull(i) ?: 0.0) }

    @Test
    fun `renders a self-contained html document`() {
        val html = render(
            YearRecapUi(
                isLoading = false,
                selectedYear = 2025,
                sessionCount = 42,
                totalCost = 1234.5,
                totalKwh = 678.9,
                totalDistanceKm = 5000.0,
                monthlyCost = months(100.0, 200.0),
                monthlyKwh = months(50.0, 80.0),
            ),
        )
        assertTrue(html.startsWith("<!DOCTYPE html>"), "should be an HTML document")
        assertTrue("2025 Recap" in html, "title carries the year")
        assertTrue("42" in html, "session count appears")
        // Self-contained: no external resources, so the report opens offline
        // and the app's "nothing leaves the phone" promise holds.
        assertFalse("http://" in html, "no external http resources")
        assertFalse("https://" in html, "no external https resources")
    }

    @Test
    fun `escapes user-controlled brand and trip names`() {
        val html = render(
            YearRecapUi(
                isLoading = false,
                selectedYear = 2025,
                sessionCount = 1,
                topBrands = listOf("<script>alert(1)</script>" to 10.0),
                monthlyCost = months(),
                monthlyKwh = months(),
                longestTrip = LongestTripSummary(
                    name = "Trip & \"quotes\" <b>",
                    distanceKm = 100.0,
                    sessionCount = 2,
                    totalCost = null,
                    currency = null,
                ),
            ),
        )
        // The raw, executable markup must never reach the document.
        assertFalse("<script>alert(1)</script>" in html, "brand markup must be escaped")
        assertTrue("&lt;script&gt;alert(1)&lt;/script&gt;" in html, "brand appears escaped")
        assertTrue("Trip &amp; &quot;quotes&quot; &lt;b&gt;" in html, "trip name appears escaped")
    }

    @Test
    fun `includes both cost and energy in the monthly table`() {
        val html = render(
            YearRecapUi(
                isLoading = false,
                selectedYear = 2025,
                sessionCount = 3,
                costCurrency = "USD",
                monthlyCost = months(100.0),
                monthlyKwh = months(40.0),
            ),
        )
        assertTrue("Cost (USD)" in html, "cost column header carries the currency")
        assertTrue("Energy" in html, "energy column is present")
        // Both metric toggles are wired up.
        assertTrue("data-metric=\"cost\"" in html)
        assertTrue("data-metric=\"energy\"" in html)
    }

    @Test
    fun `notes currency-excluded sessions when present`() {
        val html = render(
            YearRecapUi(
                isLoading = false,
                selectedYear = 2025,
                sessionCount = 5,
                excludedByCurrency = 2,
                costCurrency = "CAD",
                monthlyCost = months(),
                monthlyKwh = months(),
            ),
        )
        assertTrue("2 sessions in another currency excluded" in html)
    }

    @Test
    fun `svg numbers use a dot decimal regardless of locale`() {
        // SVG/CSS require '.' as the decimal separator; a comma-locale device
        // must not emit "12,3" into coordinates and break the markup.
        val previous = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY)
            val html = render(
                YearRecapUi(
                    isLoading = false,
                    selectedYear = 2025,
                    sessionCount = 1,
                    monthlyCost = months(100.0, 50.0, 25.0),
                    monthlyKwh = months(10.0),
                ),
            )
            // Bar geometry is emitted as width="48.8" style numbers — assert no
            // comma sneaks into an SVG attribute value.
            val rectRegex = Regex("(?:x|y|width|height)=\"[0-9.]+\"")
            assertTrue(rectRegex.containsMatchIn(html), "svg rects rendered")
            assertFalse(Regex("(?:x|y|width|height)=\"[0-9]+,[0-9]+\"").containsMatchIn(html))
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }

    @Test
    fun `escapes ampersand in vehicle name in the title`() {
        val html = render(
            YearRecapUi(
                isLoading = false,
                selectedYear = 2025,
                sessionCount = 1,
                vehicleName = "Bob & Sue's EV",
                monthlyCost = months(),
                monthlyKwh = months(),
            ),
        )
        assertTrue("Bob &amp; Sue&#39;s EV" in html)
        assertFalse("Bob & Sue's EV" in html)
    }

    @Test
    fun `empty monthly series renders no chart but still a document`() {
        val html = render(
            YearRecapUi(
                isLoading = false,
                selectedYear = 2025,
                sessionCount = 0,
                monthlyCost = emptyList(),
                monthlyKwh = emptyList(),
            ),
        )
        assertEquals(true, html.contains("</html>"), "document still closes cleanly")
    }
}
