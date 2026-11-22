package com.evsct.app.ui.vehicles

import android.os.Bundle
import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The vehicle form's process-death draft: what was edited comes back, what
 * wasn't takes the fresh load. Bundles go through a real Parcel, the way
 * saved state crosses process death, so a value Android can't write fails
 * here. Robolectric for a real Bundle and Parcel.
 */
@RunWith(AndroidJUnit4::class)
class VehicleFormDraftTest {

    private val loaded = VehicleEditUi(
        isLoading = false,
        isNew = false,
        name = "Daily",
        year = "2023",
        make = "Kia",
        model = "EV6",
        trim = "Wind",
        batteryKwh = "77.4",
        rangeText = "499",
        vin = "KNDC1",
        notes = "old",
        isDefault = true,
        imagePath = "vehicles/a.jpg",
    )

    @Test
    fun `an unedited form captures nothing`() {
        assertTrue(VehicleFormDraft.capture(loaded, loaded).isEmpty)
    }

    @Test
    fun `every editable field round-trips`() {
        val edited = loaded.copy(
            name = "Road-trip car",
            year = "2024",
            make = "Hyundai",
            model = "Ioniq 5",
            trim = "Preferred",
            batteryKwh = "84,0",
            rangeText = "310",
            vin = "KM8KR",
            notes = "typed before the photo picker opened",
            isDefault = false,
            imagePath = "vehicles/b.jpg",
        )

        assertEquals(edited, roundTrip(edited, baseline = loaded, onto = loaded))
    }

    @Test
    fun `clearing a value is an edit that survives`() {
        val cleared = loaded.copy(
            name = "",
            year = "",
            rangeText = "",
            notes = "",
            isDefault = false,
            imagePath = null,
        )

        val restored = roundTrip(cleared, baseline = loaded, onto = loaded)

        assertNull(restored.imagePath)
        assertEquals(cleared, restored)
    }

    @Test
    fun `a photo picked for a vehicle that had none survives`() {
        val bare = loaded.copy(imagePath = null)
        val picked = bare.copy(imagePath = "vehicles/new.jpg")

        assertEquals("vehicles/new.jpg", roundTrip(picked, baseline = bare, onto = bare).imagePath)
    }

    @Test
    fun `untouched fields take the fresh load`() {
        // A new vehicle seeds Default from whether another vehicle already
        // is; by the time the process comes back that may have changed.
        // Only the user's own edit should be carried over.
        val edited = loaded.copy(notes = "new note")
        val reloaded = loaded.copy(isDefault = false, imagePath = null)

        val restored = roundTrip(edited, baseline = loaded, onto = reloaded)

        assertEquals("new note", restored.notes)
        assertEquals(false, restored.isDefault)
        assertNull(restored.imagePath)
    }

    private fun roundTrip(form: VehicleEditUi, baseline: VehicleEditUi, onto: VehicleEditUi): VehicleEditUi =
        VehicleFormDraft.restore(parcelled(VehicleFormDraft.capture(form, baseline)), onto)

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
