package com.evsct.app.ui.sessions

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UnreadableFieldsTest {

    @Test
    fun `blank and readable fields are not flagged`() {
        val form = SessionEditUi(
            odometerText = "10,250",
            energyText = "48.2",
            costText = "12,50",
            batteryStartText = "20",
            batteryEndText = "82.5",
            durationText = "32:14",
            waitTimeText = "",
        )
        assertTrue(unreadableFields(form).isEmpty())
    }

    @Test
    fun `pasted units and currency signs are flagged`() {
        val form = SessionEditUi(energyText = "48.2 kWh", costText = "$12.50")
        assertEquals(setOf(HintField.ENERGY, HintField.COST), unreadableFields(form))
    }

    @Test
    fun `malformed numbers are flagged in every number field`() {
        val form = SessionEditUi(
            odometerText = "12..5",
            postedEnergyPriceText = "0.4.9",
            postedTimeRateText = "abc",
            postedMaxPowerText = "1 234,5",
            batteryStartText = "2O",
            batteryEndText = "80%",
        )
        assertEquals(
            setOf(
                HintField.ODOMETER,
                HintField.POSTED_ENERGY_PRICE,
                HintField.POSTED_TIME_RATE,
                HintField.POSTED_MAX_POWER,
                HintField.BATTERY_START,
                HintField.BATTERY_END,
            ),
            unreadableFields(form),
        )
    }

    @Test
    fun `unreadable durations are flagged, charge time and wait time alike`() {
        val form = SessionEditUi(durationText = "about an hour", waitTimeText = "1:2:3:4")
        assertEquals(setOf(HintField.DURATION, HintField.WAIT_TIME), unreadableFields(form))
    }
}
