package com.evsct.app.ui.sessions

import android.os.Bundle
import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.evsct.app.data.entity.ChargingType
import com.evsct.app.data.entity.PaymentMethod
import com.evsct.app.data.entity.PricingModel
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The session form's process-death draft: what was edited comes back, what
 * wasn't takes the fresh load. Bundles go through a real Parcel, the way
 * saved state crosses process death, so a value Android can't write fails
 * here. Robolectric for a real Bundle and Parcel.
 */
@RunWith(AndroidJUnit4::class)
class SessionFormDraftTest {

    private val loaded = SessionEditUi(
        isLoading = false,
        isNew = false,
        sessionStart = 1_700_000_000_000,
        durationText = "0h 12m 0s",
        odometerText = "10250",
        energyText = "40.0",
        currency = "CAD",
        paymentMethod = PaymentMethod.CREDIT_CARD,
        paymentDetail = "TD Visa",
        brand = "FLO",
        city = "Regina",
        tripId = 7,
        vehicleId = 2,
        notes = "old",
        tags = listOf("roadtrip"),
        receipts = listOf(UiReceipt(id = 11, filePath = "receipts/a.jpg", originalFileName = null)),
        latitude = 50.45,
        longitude = -104.61,
    )

    @Test
    fun `an unedited form captures nothing`() {
        assertTrue(SessionFormDraft.capture(loaded, loaded).isEmpty)
    }

    @Test
    fun `every editable field round-trips`() {
        val edited = loaded.copy(
            sessionStart = loaded.sessionStart + 60_000,
            durationText = "45",
            waitTimeText = "5",
            odometerText = "10,300",
            energyText = "45,678",
            costText = "18.20",
            currency = "USD",
            paymentMethod = PaymentMethod.APP,
            paymentDetail = "FLO app",
            postedEnergyPriceText = "0.39",
            postedTimeRateText = "27",
            postedTimeRateUnit = TimeRateUnit.PER_HOUR,
            postedMaxPowerText = "100",
            batteryStartText = "18",
            batteryEndText = "80",
            chargingType = ChargingType.AC_L2,
            pricingModel = PricingModel.HYBRID,
            brand = "Petro-Canada",
            city = "Moose Jaw",
            province = "SK",
            address = "1 Main St",
            stationName = "Station A",
            stallName = "Stall 3",
            tripId = 8,
            continuesPrevious = true,
            vehicleId = 3,
            notes = "typed before the camera opened",
            tags = listOf("roadtrip", "winter"),
            tagDraft = "half-typ",
            receipts = listOf(
                UiReceipt(id = 11, filePath = "receipts/a.jpg", originalFileName = "Renamed.jpg"),
                UiReceipt(id = null, filePath = "receipts/b.pdf", originalFileName = "statement.pdf"),
                UiReceipt(id = null, filePath = "receipts/c.jpg", originalFileName = null),
            ),
            latitude = 50.39,
            longitude = -105.53,
        )

        assertEquals(edited, roundTrip(edited, baseline = loaded, onto = loaded))
    }

    @Test
    fun `clearing a value is an edit that survives`() {
        val cleared = loaded.copy(
            paymentMethod = null,
            paymentDetail = "",
            tripId = null,
            vehicleId = null,
            tags = emptyList(),
            receipts = emptyList(),
            latitude = null,
            longitude = null,
        )

        val restored = roundTrip(cleared, baseline = loaded, onto = loaded)

        assertNull(restored.paymentMethod)
        assertNull(restored.tripId)
        assertNull(restored.vehicleId)
        assertNull(restored.latitude)
        assertNull(restored.longitude)
        assertEquals(cleared, restored)
    }

    @Test
    fun `untouched fields take the fresh load`() {
        // A tracked charge seeds its duration with the elapsed time at open;
        // after the process dies the fresh load seeds a later one. Only the
        // user's own edit should be carried over.
        val edited = loaded.copy(notes = "new note")
        val reloaded = loaded.copy(durationText = "0h 45m 0s")

        val restored = roundTrip(edited, baseline = loaded, onto = reloaded)

        assertEquals("new note", restored.notes)
        assertEquals("0h 45m 0s", restored.durationText)
    }

    private fun roundTrip(form: SessionEditUi, baseline: SessionEditUi, onto: SessionEditUi): SessionEditUi =
        SessionFormDraft.restore(parcelled(SessionFormDraft.capture(form, baseline)), onto)

    private fun parcelled(bundle: Bundle): Bundle {
        val parcel = Parcel.obtain()
        try {
            parcel.writeBundle(bundle)
            parcel.setDataPosition(0)
            return requireNotNull(parcel.readBundle(javaClass.classLoader))
        } finally {
            parcel.recycle()
        }
    }
}
