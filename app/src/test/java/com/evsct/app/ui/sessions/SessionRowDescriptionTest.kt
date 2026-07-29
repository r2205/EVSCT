package com.evsct.app.ui.sessions

import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.data.entity.ChargingType
import com.evsct.app.data.prefs.CardTimeRate
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The spoken description behind a session row's single TalkBack stop. Asserts
 * on substrings rather than the whole sentence: it embeds [com.evsct.app.util
 * .Format] output, whose date and money formatting follow the host's timezone
 * and locale.
 */
class SessionRowDescriptionTest {

    private fun session(
        durationSeconds: Long? = 1_800L,
        energyKwh: Double? = 42.5,
        waitTimeSeconds: Long? = null,
        chargingType: ChargingType = ChargingType.DC_FAST,
        brand: String? = "Petro-Canada",
        locationCity: String? = "Kingston",
    ) = ChargingSession(
        sessionStart = 1_752_000_000_000L,
        durationSeconds = durationSeconds,
        energyKwh = energyKwh,
        waitTimeSeconds = waitTimeSeconds,
        totalCost = 24.50,
        chargingType = chargingType,
        brand = brand,
        locationCity = locationCity,
    )

    /** Defaults to the rates off, so the tests that predate them read as they
     *  did; the rate cases pass them explicitly. */
    private fun describe(
        s: ChargingSession,
        tripName: String? = null,
        vehicleName: String? = null,
        hasReceipt: Boolean = false,
        tags: List<String> = emptyList(),
        effectiveEnergyRate: Double? = null,
        effectiveTimeRate: CardTimeRateValue? = null,
    ) = sessionRowDescription(
        s, tripName, vehicleName, hasReceipt, tags, effectiveEnergyRate, effectiveTimeRate,
    )

    @Test
    fun `units are spoken as words, not letters`() {
        val text = describe(session())
        assertTrue("kilowatt hours" in text, text)
        assertTrue("kilowatts average" in text, text)
        // The compact forms would be spelled out letter by letter.
        assertFalse("kWh" in text, text)
    }

    @Test
    fun `charging type abbreviations are expanded`() {
        assertTrue("AC level 2" in describe(session(chargingType = ChargingType.AC_L2)))
        assertTrue("AC level 1" in describe(session(chargingType = ChargingType.AC_L1)))
        assertTrue("DC fast" in describe(session(chargingType = ChargingType.DC_FAST)))
    }

    @Test
    fun `optional parts are omitted when absent`() {
        val bare = describe(session(waitTimeSeconds = null))
        assertFalse("wait" in bare, bare)
        assertFalse("trip" in bare, bare)
        assertFalse("receipt" in bare, bare)
        assertFalse("tag" in bare, bare)
    }

    @Test
    fun `optional parts are included when present`() {
        val full = describe(
            session(waitTimeSeconds = 7 * 60L),
            tripName = "Gaspé loop",
            vehicleName = "Ioniq 5",
            hasReceipt = true,
            tags = listOf("roadtrip", "reimbursed"),
        )
        assertTrue("7 minutes wait" in full, full)
        assertTrue("trip Gaspé loop" in full, full)
        assertTrue("Ioniq 5" in full, full)
        assertTrue("receipt attached" in full, full)
        assertTrue("tags roadtrip, reimbursed" in full, full)
    }

    @Test
    fun `single tag and single wait minute are not pluralized`() {
        val text = describe(session(waitTimeSeconds = 60L), tags = listOf("home"))
        assertTrue("1 minute wait" in text, text)
        assertFalse("1 minutes wait" in text, text)
        assertTrue("tag home" in text, text)
        assertFalse("tags home" in text, text)
    }

    @Test
    fun `sub-minute wait reads as less than a minute`() {
        val text = describe(session(waitTimeSeconds = 45L))
        assertTrue("less than a minute wait" in text, text)
    }

    @Test
    fun `a missing brand still names the row`() {
        val text = describe(session(brand = null, locationCity = null))
        assertTrue(text.startsWith("Unknown brand"), text)
    }

    /* ---------------------------- Effective rates --------------------------- */

    // These were on screen and missing from the sentence — the one place a
    // screen-reader user got less than a sighted one. The slash was why: it
    // splits a rate into two unrelated numbers out loud.
    @Test
    fun `the effective energy rate is spoken as a rate`() {
        val text = describe(session(), effectiveEnergyRate = 0.55)
        assertTrue("effective" in text, text)
        assertTrue("per kilowatt hour" in text, text)
        assertFalse("/kWh" in text, text)
        assertFalse("Eff." in text, text)
    }

    @Test
    fun `both time rate units are spoken as words`() {
        val perMin = describe(
            session(),
            effectiveTimeRate = CardTimeRateValue(0.81, shortUnit = "min", spokenUnit = "minute"),
        )
        assertTrue("per minute" in perMin, perMin)
        assertFalse("/min" in perMin, perMin)

        val perHour = describe(
            session(),
            effectiveTimeRate = CardTimeRateValue(48.60, shortUnit = "hr", spokenUnit = "hour"),
        )
        assertTrue("per hour" in perHour, perHour)
        assertFalse("/hr" in perHour, perHour)
    }

    // Parity with the card is the whole point: the time rate is a preference,
    // and a sentence that recites a rate the row isn't showing is its own bug.
    @Test
    fun `a rate the row is not showing is not spoken`() {
        val text = describe(session(), effectiveEnergyRate = 0.55, effectiveTimeRate = null)
        assertTrue("per kilowatt hour" in text, text)
        assertFalse("per minute" in text, text)
        assertFalse("per hour" in text, text)

        val neither = describe(session())
        assertFalse("effective" in neither, neither)
    }

    // The sentence claims to follow the card's reading order, where the rates
    // sit below the stat line and above the vehicle and trip pills.
    @Test
    fun `rates are spoken between the stats and the pills`() {
        val text = describe(
            session(),
            vehicleName = "Ioniq 5",
            tripName = "Gaspé loop",
            effectiveEnergyRate = 0.55,
            effectiveTimeRate = CardTimeRateValue(0.81, shortUnit = "min", spokenUnit = "minute"),
        )
        val order = listOf("average", "per kilowatt hour", "per minute", "Ioniq 5")
            .map { it to text.indexOf(it) }
        order.forEach { (part, at) -> assertTrue(at >= 0, "\"$part\" missing from: $text") }
        order.zipWithNext { (a, atA), (b, atB) ->
            assertTrue(atA < atB, "\"$a\" should be spoken before \"$b\": $text")
        }
    }

    /* -------------------------- cardTimeRate mapping ------------------------ */

    // The preference is interpreted here and nowhere else, so that the chip and
    // the sentence can't disagree about which rate a row shows.
    @Test
    fun `the card preference picks the matching rate`() {
        // 24.50 over 1800s: $0.8167/min, $49.00/hr.
        val s = session()
        val perMin = cardTimeRate(s, CardTimeRate.PER_MINUTE)!!
        assertEquals("min", perMin.shortUnit)
        assertEquals("minute", perMin.spokenUnit)
        assertEquals(0.8167, perMin.value, 0.001)

        val perHour = cardTimeRate(s, CardTimeRate.PER_HOUR)!!
        assertEquals("hr", perHour.shortUnit)
        assertEquals("hour", perHour.spokenUnit)
        assertEquals(49.0, perHour.value, 0.001)

        assertNull(cardTimeRate(s, CardTimeRate.OFF))
    }

    @Test
    fun `no duration means no time rate in any unit`() {
        val s = session(durationSeconds = null)
        assertNull(cardTimeRate(s, CardTimeRate.PER_MINUTE))
        assertNull(cardTimeRate(s, CardTimeRate.PER_HOUR))
    }

    @Test
    fun `durations read as words with correct plurals`() {
        assertEquals("1 hour", spokenDuration(3_600L))
        assertEquals("2 hours", spokenDuration(7_200L))
        assertEquals("1 minute", spokenDuration(60L))
        assertEquals("25 minutes", spokenDuration(1_500L))
        assertEquals("1 hour 25 minutes", spokenDuration(3_600L + 1_500L))
    }

    @Test
    fun `sub-minute charges are not silently dropped`() {
        // The visible row shows "0m 42s"; saying nothing at all would read as
        // a session with no duration recorded.
        assertEquals("less than a minute", spokenDuration(42L))
    }

    @Test
    fun `absent or zero duration says nothing`() {
        assertNull(spokenDuration(null))
        assertNull(spokenDuration(0L))
        assertNull(spokenDuration(-5L))
    }
}
