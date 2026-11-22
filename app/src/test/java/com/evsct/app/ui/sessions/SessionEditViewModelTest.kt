package com.evsct.app.ui.sessions

import android.content.Context
import android.os.Looper
import android.os.Parcel
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.evsct.app.data.db.EvsctDatabase
import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.data.entity.Trip
import com.evsct.app.data.entity.Vehicle
import com.evsct.app.data.prefs.AppPreferences
import com.evsct.app.data.repository.SessionReceiptRepository
import com.evsct.app.data.repository.SessionRepository
import com.evsct.app.data.repository.TripRepository
import com.evsct.app.data.repository.VehicleRepository
import com.evsct.app.ui.navigation.Routes
import com.evsct.app.util.InProgressChargeNotifier
import com.evsct.app.util.LocationAutofill
import com.evsct.app.util.ReceiptImageStore
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The session form against a real (in-memory) database and a real
 * SavedStateHandle: "Use a recent stop" keeps the stop's coordinates through
 * save, and unsaved edits survive the process dying while the app is in the
 * background — the camera or photo picker, typically.
 */
@RunWith(AndroidJUnit4::class)
class SessionEditViewModelTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, EvsctDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val tempDir: File = Files.createTempDirectory("session-edit-vm").toFile()
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = AppPreferences(
        PreferenceDataStoreFactory.create(scope = appScope) { File(tempDir, "prefs.preferences_pb") },
    )
    private val sessionRepository = SessionRepository(db.sessionDao())
    private val receiptRepository = SessionReceiptRepository(db.sessionReceiptDao())
    private val vehicleRepository = VehicleRepository(db.vehicleDao())
    private val tripRepository = TripRepository(db.tripDao(), sessionRepository, vehicleRepository)
    private val receiptStore = ReceiptImageStore(context)
    private val notifier = InProgressChargeNotifier(context, prefs, appScope)
    private val stores = mutableListOf<ViewModelStore>()

    @After
    fun tearDown() {
        stores.forEach { it.clear() }
        shadowOf(Looper.getMainLooper()).idle()
        appScope.cancel()
        db.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun `a recent stop keeps its coordinates through save`() {
        val car = runBlocking { db.vehicleDao().insert(Vehicle(name = "EV6", isDefault = true)) }
        val earlier = runBlocking {
            db.sessionDao().insert(
                ChargingSession(
                    sessionStart = 1_700_000_000_000,
                    brand = "FLO",
                    locationAddress = "2010 Victoria Ave",
                    locationCity = "Regina",
                    locationProvince = "SK",
                    // A map-picked point, more precise than a text geocode.
                    latitude = 50.4452,
                    longitude = -104.6189,
                    vehicleId = car,
                ),
            )
        }

        val vm = newViewModel(newSessionHandle())
        awaitUntil { !vm.state.value.isLoading && vm.state.value.recentStops.isNotEmpty() }
        vm.applyStop(vm.state.value.recentStops.single())
        save(vm)

        val saved = runBlocking { db.sessionDao().observeAll().first() }.single { it.id != earlier }
        assertEquals(50.4452, saved.latitude)
        assertEquals(-104.6189, saved.longitude)
    }

    @Test
    fun `unsaved edits survive process death`() {
        val car = runBlocking { db.vehicleDao().insert(Vehicle(name = "EV6")) }
        val trip = runBlocking { db.tripDao().insert(Trip(name = "Summer", vehicleId = car)) }
        val id = runBlocking {
            db.sessionDao().insert(
                ChargingSession(
                    sessionStart = 1_700_000_000_000,
                    odometerKm = 10_250.0,
                    energyKwh = 40.0,
                    notes = "old",
                    tripId = trip,
                    vehicleId = car,
                ),
            )
        }
        val handle = SavedStateHandle(mapOf(Routes.SESSION_EDIT_ARG to id))
        val before = newViewModel(handle)
        awaitLoaded(before)
        before.update { it.copy(notes = "typed before the camera opened", energyText = "45.5", tripId = null) }

        val after = newViewModel(processDeath(handle))
        awaitLoaded(after)

        val s = after.state.value
        assertEquals("typed before the camera opened", s.notes)
        assertEquals("45.5", s.energyText)
        assertNull(s.tripId)
        // Untouched: straight from the database.
        assertEquals("10250", s.odometerText)
        assertEquals(car, s.vehicleId)
        // Still unsaved, so backing out still asks before discarding them.
        assertTrue(after.isDirty(s))
    }

    @Test
    fun `edits survive the process dying again before the form reloads`() {
        val id = runBlocking { db.sessionDao().insert(ChargingSession(sessionStart = 1_700_000_000_000)) }
        val handle = SavedStateHandle(mapOf(Routes.SESSION_EDIT_ARG to id))
        val first = newViewModel(handle)
        awaitLoaded(first)
        first.update { it.copy(notes = "keep me") }

        // Recreated, then killed again before its load ran.
        val restoredHandle = processDeath(handle)
        newViewModel(restoredHandle)
        val last = newViewModel(processDeath(restoredHandle))
        awaitLoaded(last)

        assertEquals("keep me", last.state.value.notes)
    }

    @Test
    fun `a recent stop picked before process death keeps its coordinates`() {
        runBlocking {
            db.sessionDao().insert(
                ChargingSession(
                    sessionStart = 1_700_000_000_000,
                    brand = "Tesla",
                    locationAddress = "1 Main St",
                    locationCity = "Moose Jaw",
                    latitude = 50.39,
                    longitude = -105.53,
                ),
            )
        }
        val handle = newSessionHandle()
        val before = newViewModel(handle)
        awaitUntil { !before.state.value.isLoading && before.state.value.recentStops.isNotEmpty() }
        before.applyStop(before.state.value.recentStops.single())

        // The geocode baseline the stop moved has to come back too, or the
        // restored form's save reads the address as edited.
        val after = newViewModel(processDeath(handle))
        awaitLoaded(after)
        save(after)

        val rows = runBlocking { db.sessionDao().observeAll().first() }
        assertEquals(2, rows.size)
        rows.forEach {
            assertEquals(50.39, it.latitude)
            assertEquals(-105.53, it.longitude)
        }
    }

    private fun newSessionHandle() = SavedStateHandle(mapOf(Routes.SESSION_EDIT_ARG to -1L))

    private fun newViewModel(handle: SavedStateHandle): SessionEditViewModel {
        val store = ViewModelStore().also { stores += it }
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = SessionEditViewModel(
                handle, db, sessionRepository, receiptRepository, tripRepository, vehicleRepository,
                LocationAutofill(context), receiptStore, prefs, notifier,
                DeletedSessionUndoHolder(sessionRepository, receiptRepository, receiptStore, appScope),
                appScope,
            ) as T
        }
        return ViewModelProvider(store, factory)[SessionEditViewModel::class.java]
    }

    /** What Android keeps of [handle] when it kills the process — written
     *  through a real Parcel — and the handle a recreated screen gets. */
    private fun processDeath(handle: SavedStateHandle): SavedStateHandle {
        val parcel = Parcel.obtain()
        try {
            parcel.writeBundle(handle.savedStateProvider().saveState())
            parcel.setDataPosition(0)
            return SavedStateHandle.createHandle(parcel.readBundle(javaClass.classLoader), null)
        } finally {
            parcel.recycle()
        }
    }

    private fun save(vm: SessionEditViewModel) {
        var saved = false
        vm.save { saved = true }
        awaitUntil { saved }
    }

    private fun awaitLoaded(vm: SessionEditViewModel) = awaitUntil { !vm.state.value.isLoading }

    /** Room and DataStore finish on their own threads and resume on the main
     *  looper, which Robolectric only runs when told to. */
    private fun awaitUntil(timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            check(System.currentTimeMillis() < deadline) { "Timed out waiting for the ViewModel" }
            Thread.sleep(10)
        }
    }
}
