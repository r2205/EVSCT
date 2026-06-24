package com.evsct.app.ui.stats

import com.evsct.app.data.prefs.UserUnits
import java.io.ByteArrayOutputStream
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class YearRecapHtmlTest {

    private fun render(
        ui: YearRecapUi,
        units: UserUnits = UserUnits(),
        basemap: NaBasemap? = null,
        logoSvg: String? = null,
    ): String {
        val out = ByteArrayOutputStream()
        writeYearRecapHtml(out, ui, units, basemap, logoSvg)
        return out.toString(Charsets.UTF_8.name())
    }

    /** A single ring near the sample pins so it survives the view-bbox cull. */
    private fun bcBasemap() = NaBasemap(
        listOf(listOf(48.0 to -124.0, 54.0 to -124.0, 54.0 to -118.0, 48.0 to -118.0)),
    )

    private fun stop(lat: Double, lng: Double, color: String = "#1E88E5", label: String = "Stop", visits: Int = 1) =
        RecapMapStop(lat = lat, lng = lng, colorHex = color, label = label, visits = visits)

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
    fun `embeds the logo svg in the header and drops the redundant brand prefix`() {
        val logo = "<svg id=\"evsct-logo\"><rect/></svg>"
        val html = render(
            YearRecapUi(
                isLoading = false, selectedYear = 2025, sessionCount = 1,
                monthlyCost = months(), monthlyKwh = months(),
            ),
            logoSvg = logo,
        )
        assertTrue("<div class=\"logo\">$logo</div>" in html, "logo svg inlined in header")
        // The lockup carries the brand, so the heading shouldn't repeat it.
        assertTrue("<h1>2025 Recap</h1>" in html, "heading drops the EVSCT prefix with a logo")
        assertFalse("<h1>EVSCT — 2025 Recap" in html)
        // Logo precedes the heading.
        assertTrue(html.indexOf(logo) < html.indexOf("<h1>"), "logo appears before the heading")
    }

    @Test
    fun `keeps the text brand heading when no logo is supplied`() {
        val html = render(
            YearRecapUi(
                isLoading = false, selectedYear = 2025, sessionCount = 1,
                monthlyCost = months(), monthlyKwh = months(),
            ),
            logoSvg = null,
        )
        assertTrue("<h1>EVSCT — 2025 Recap</h1>" in html)
        assertFalse("class=\"logo\"" in html, "no logo container without a logo")
    }

    @Test
    fun `no map section when no stops have coordinates`() {
        val html = render(
            YearRecapUi(
                isLoading = false, selectedYear = 2025, sessionCount = 3,
                monthlyCost = months(), monthlyKwh = months(),
                mapStops = emptyList(),
            ),
            basemap = bcBasemap(),
        )
        assertFalse("Charging map" in html, "map section omitted with no located stops")
        assertFalse("class=\"map\"" in html)
    }

    @Test
    fun `map section renders basemap pins routes and legend`() {
        val html = render(
            YearRecapUi(
                isLoading = false, selectedYear = 2025, sessionCount = 4,
                monthlyCost = months(), monthlyKwh = months(),
                mapStops = listOf(
                    stop(49.28, -123.12, "#1E88E5", "BC Hydro, Vancouver", 2),
                    stop(51.05, -114.07, "#2E7D32", "Home, Calgary", 9),
                ),
                mapTripPaths = listOf(
                    RecapTripPath("#1E88E5", listOf(49.28 to -123.12, 51.05 to -114.07)),
                ),
                mapLegend = listOf("West trip" to "#1E88E5", "Untripped" to "#2E7D32"),
            ),
            basemap = bcBasemap(),
        )
        assertTrue("<svg class=\"map\"" in html, "map svg present")
        assertTrue("viewBox=\"0 0 " in html, "map has a viewBox")
        assertTrue("<path class=\"coast\"" in html, "basemap outline drawn")
        assertTrue("<polyline class=\"route\"" in html, "trip route drawn")
        assertTrue(Regex("<circle class=\"pin\"[^>]*fill=\"#1E88E5\"").containsMatchIn(html), "trip pin")
        assertTrue(Regex("<circle class=\"pin\"[^>]*fill=\"#2E7D32\"").containsMatchIn(html), "untripped pin")
        assertTrue("West trip" in html && "Untripped" in html, "legend labels present")
    }

    @Test
    fun `pins render without a basemap`() {
        val html = render(
            YearRecapUi(
                isLoading = false, selectedYear = 2025, sessionCount = 1,
                monthlyCost = months(), monthlyKwh = months(),
                mapStops = listOf(stop(49.28, -123.12)),
            ),
            basemap = null,
        )
        assertTrue("<svg class=\"map\"" in html, "map still renders pins")
        assertTrue("<circle class=\"pin\"" in html)
        assertFalse("<path class=\"coast\"" in html, "no basemap paths without a basemap")
    }

    @Test
    fun `pin tooltip escapes the stop label`() {
        val html = render(
            YearRecapUi(
                isLoading = false, selectedYear = 2025, sessionCount = 1,
                monthlyCost = months(), monthlyKwh = months(),
                mapStops = listOf(stop(49.0, -123.0, label = "A & B <pin>")),
            ),
        )
        assertTrue("A &amp; B &lt;pin&gt;" in html, "label appears escaped in the tooltip")
        assertFalse("A & B <pin>" in html)
    }

    @Test
    fun `parseNaBasemap reads rings and skips malformed lines`() {
        val bm = parseNaBasemap(
            """
            49.00,-123.00 50.00,-123.00 50.00,-122.00
            garbage line with no commas
            1.0,2.0 3.0,4.0
            """.trimIndent(),
        )
        // First line: a 3-vertex ring (kept). Second: no valid tokens (dropped).
        // Third: only 2 vertices, below the 3-point floor (dropped).
        assertEquals(1, bm.rings.size)
        assertEquals(49.0 to -123.0, bm.rings[0][0])
        assertEquals(3, bm.rings[0].size)
    }

    @Test
    fun `map coordinates use a dot decimal under a comma locale`() {
        val previous = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY)
            val html = render(
                YearRecapUi(
                    isLoading = false, selectedYear = 2025, sessionCount = 2,
                    monthlyCost = months(), monthlyKwh = months(),
                    mapStops = listOf(stop(49.28, -123.12), stop(45.5, -73.57)),
                ),
                basemap = bcBasemap(),
            )
            // No comma should appear inside an SVG coordinate attribute.
            assertFalse(Regex("c[xy]=\"[0-9]+,[0-9]+\"").containsMatchIn(html))
            assertFalse(Regex("viewBox=\"[^\"]*,[^\"]*\"").containsMatchIn(html))
        } finally {
            java.util.Locale.setDefault(previous)
        }
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
