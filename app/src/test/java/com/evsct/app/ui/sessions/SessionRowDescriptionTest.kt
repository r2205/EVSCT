package com.evsct.app.ui.sessions

import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.data.entity.ChargingType
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
        waitTimeMinutes: Int? = null,
        chargingType: ChargingType = ChargingType.DC_FAST,
        brand: String? = "Petro-Canada",
        locationCity: String? = "Kingston",
    ) = ChargingSession(
        sessionStart = 1_752_000_000_000L,
        durationSeconds = durationSeconds,
        energyKwh = energyKwh,
        waitTimeMinutes = waitTimeMinutes,
        totalCost = 24.50,
        chargingType = chargingType,
        brand = brand,
        locationCity = locationCity,
    )

    private fun describe(
        s: ChargingSession,
        tripName: String? = null,
        vehicleName: String? = null,
        hasReceipt: Boolean = false,
        tags: List<String> = emptyList(),
    ) = sessionRowDescription(s, tripName, vehicleName, hasReceipt, tags)

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
        val bare = describe(session(waitTimeMinutes = null))
        assertFalse("wait" in bare, bare)
        assertFalse("trip" in bare, bare)
        assertFalse("receipt" in bare, bare)
        assertFalse("tag" in bare, bare)
    }

    @Test
    fun `optional parts are included when present`() {
        val full = describe(
            session(waitTimeMinutes = 7),
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
        val text = describe(session(waitTimeMinutes = 1), tags = listOf("home"))
        assertTrue("1 minute wait" in text, text)
        assertFalse("1 minutes wait" in text, text)
        assertTrue("tag home" in text, text)
        assertFalse("tags home" in text, text)
    }

    @Test
    fun `a missing brand still names the row`() {
        val text = describe(session(brand = null, locationCity = null))
        assertTrue(text.startsWith("Unknown brand"), text)
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
