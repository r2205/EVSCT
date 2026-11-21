package com.evsct.app.ui.vehicles

import android.content.Context
import android.net.Uri
import android.os.Looper
import android.os.Parcel
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.evsct.app.data.db.EvsctDatabase
import com.evsct.app.data.entity.Vehicle
import com.evsct.app.data.prefs.AppPreferences
import com.evsct.app.data.repository.VehicleRepository
import com.evsct.app.ui.navigation.Routes
import com.evsct.app.util.VehicleImageStore
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The vehicle form against a real (in-memory) database and a real
 * SavedStateHandle: unsaved edits and a picked profile photo survive the
 * process dying while the app is in the background — behind the photo
 * picker, typically — and the photo copies are still cleaned up (or kept)
 * correctly afterwards.
 */
@RunWith(AndroidJUnit4::class)
class VehicleEditViewModelTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, EvsctDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val tempDir: File = Files.createTempDirectory("vehicle-edit-vm").toFile()
    private val dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) {
        File(tempDir, "prefs.preferences_pb")
    }
    private val prefs = AppPreferences(dataStore)

    /** The ViewModel's app scope, apart from DataStore's long-lived one so
     *  [awaitCleanup] can wait for back-out cleanup to finish. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository = VehicleRepository(db.vehicleDao())
    private val imageStore = VehicleImageStore(context)
    private val photoDir = File(context.filesDir, "vehicles")
    private val stores = mutableListOf<ViewModelStore>()

    @After
    fun tearDown() {
        stores.forEach { it.clear() }
        shadowOf(Looper.getMainLooper()).idle()
        awaitCleanup()
        appScope.cancel()
        dataStoreScope.cancel()
        db.close()
        tempDir.deleteRecursively()
        photoDir.deleteRecursively()
    }

    @Test
    fun `unsaved edits survive process death`() {
        val id = runBlocking {
            db.vehicleDao().insert(
                Vehicle(name = "Daily", year = 2023, make = "Kia", model = "EV6", nominalRangeKm = 499, notes = "old"),
            )
        }
        val handle = SavedStateHandle(mapOf(Routes.VEHICLE_EDIT_ARG to id))
        val before = newViewModel(handle)
        awaitLoaded(before)
        before.update { it.copy(name = "Road-trip car", notes = "typed before the picker opened", rangeText = "450") }

        val after = newViewModel(processDeath(handle))
        awaitLoaded(after)

        val s = after.state.value
        assertEquals("Road-trip car", s.name)
        assertEquals("typed before the picker opened", s.notes)
        assertEquals("450", s.rangeText)
        // Untouched: straight from the database.
        assertEquals("2023", s.year)
        assertEquals("EV6", s.model)
        // Still unsaved, so backing out still asks before discarding them.
        assertTrue(after.isDirty(s))

        save(after)
        val row = runBlocking { repository.findById(id) }!!
        assertEquals("Road-trip car", row.name)
        assertEquals(450, row.nominalRangeKm)
        assertEquals(2023, row.year)
    }

    @Test
    fun `edits survive the process dying again before the form reloads`() {
        val id = runBlocking { db.vehicleDao().insert(Vehicle(name = "Daily")) }
        val handle = SavedStateHandle(mapOf(Routes.VEHICLE_EDIT_ARG to id))
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
    fun `a photo picked while the recreated form loads is kept`() {
        val original = photo("vehicles/original.jpg")
        val id = runBlocking { db.vehicleDao().insert(Vehicle(name = "Daily", imagePath = original)) }
        val handle = SavedStateHandle(mapOf(Routes.VEHICLE_EDIT_ARG to id))
        val before = newViewModel(handle)
        awaitLoaded(before)
        before.update { it.copy(notes = "typed before the picker opened") }

        // The process dies behind the picker. The recreated screen's load is
        // held back until the picked photo has been copied, so the result
        // lands while the form is still loading.
        val loadGate = CompletableDeferred<Unit>()
        val after = newViewModel(processDeath(handle), AppPreferences(GatedDataStore(dataStore, loadGate)))
        after.pickImage(pickerUri("picked"))
        awaitUntil { photoFiles().size == 2 }
        settle()
        assertTrue(after.state.value.isLoading)
        loadGate.complete(Unit)
        awaitUntil { !after.state.value.isLoading && after.state.value.imagePath != original }

        val picked = after.state.value.imagePath
        assertNotNull(picked)
        assertEquals("typed before the picker opened", after.state.value.notes)
        assertTrue(after.isDirty(after.state.value))

        save(after)
        assertEquals(picked, runBlocking { repository.findById(id) }!!.imagePath)
        // The replaced photo goes once the new one is saved.
        assertEquals(setOf(picked), photoFiles())
    }

    @Test
    fun `a photo picked before process death is removed when the user backs out`() {
        val original = photo("vehicles/original.jpg")
        val id = runBlocking { db.vehicleDao().insert(Vehicle(name = "Daily", imagePath = original)) }
        val handle = SavedStateHandle(mapOf(Routes.VEHICLE_EDIT_ARG to id))
        val before = newViewModel(handle)
        awaitLoaded(before)
        val picked = pick(before, "picked")

        val (store, after) = newViewModelWithStore(processDeath(handle))
        awaitLoaded(after)
        assertEquals(picked, after.state.value.imagePath)

        // Back out without saving: the copy the earlier instance made is
        // known to the recreated one, so it doesn't linger on disk.
        store.clear()
        awaitCleanup()

        assertEquals(setOf(original), photoFiles())
        assertEquals(original, runBlocking { repository.findById(id) }!!.imagePath)
    }

    @Test
    fun `after process death a save updates the row an earlier save committed`() {
        val handle = SavedStateHandle(mapOf(Routes.VEHICLE_EDIT_ARG to -1L))
        val before = newViewModel(handle)
        awaitLoaded(before)
        before.update { it.copy(name = "EV6") }
        // Saved, but the pop was dropped, so the form is still showing.
        save(before)

        val after = newViewModel(processDeath(handle))
        awaitLoaded(after)
        after.update { it.copy(notes = "second save") }
        save(after)

        val rows = runBlocking { repository.observeAll().first() }
        assertEquals(1, rows.size)
        assertEquals("EV6", rows.single().name)
        assertEquals("second save", rows.single().notes)
    }

    @Test
    fun `backing out after process death keeps a photo an earlier save committed`() {
        val handle = SavedStateHandle(mapOf(Routes.VEHICLE_EDIT_ARG to -1L))
        val before = newViewModel(handle)
        awaitLoaded(before)
        val saved = pick(before, "saved")
        before.update { it.copy(name = "EV6") }
        // Saved, but the pop was dropped, so the form is still showing.
        save(before)

        // The recreated screen opened as "new", so its loaded photo is none:
        // the saved one is only in the restored bookkeeping.
        val (store, after) = newViewModelWithStore(processDeath(handle))
        awaitLoaded(after)
        val speculative = pick(after, "speculative")
        store.clear()
        awaitCleanup()

        val row = runBlocking { repository.observeAll().first() }.single()
        assertEquals(saved, row.imagePath)
        assertTrue(File(context.filesDir, saved).exists())
        assertFalse(File(context.filesDir, speculative).exists())
    }

    private fun newViewModel(handle: SavedStateHandle, preferences: AppPreferences = prefs): VehicleEditViewModel =
        newViewModelWithStore(handle, preferences).second

    private fun newViewModelWithStore(
        handle: SavedStateHandle,
        preferences: AppPreferences = prefs,
    ): Pair<ViewModelStore, VehicleEditViewModel> {
        val store = ViewModelStore().also { stores += it }
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                VehicleEditViewModel(handle, repository, imageStore, preferences, appScope) as T
        }
        return store to ViewModelProvider(store, factory)[VehicleEditViewModel::class.java]
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

    /** A photo already in the app's vehicle folder, as a row would store it. */
    private fun photo(relativePath: String): String {
        File(context.filesDir, relativePath).apply { parentFile?.mkdirs() }.writeBytes(byteArrayOf(1, 2, 3))
        return relativePath
    }

    /** A Photo Picker result the content resolver can open. */
    private fun pickerUri(name: String): Uri {
        val uri = Uri.parse("content://media/picker/0/$name")
        shadowOf(context.contentResolver).registerInputStreamSupplier(uri) {
            ByteArrayInputStream(name.toByteArray())
        }
        return uri
    }

    /** Picks a photo on a loaded form and returns its stored path. */
    private fun pick(vm: VehicleEditViewModel, name: String): String {
        val previous = vm.state.value.imagePath
        vm.pickImage(pickerUri(name))
        awaitUntil { vm.state.value.imagePath != previous }
        return requireNotNull(vm.state.value.imagePath)
    }

    /** The vehicle photos on disk, as paths relative to filesDir. */
    private fun photoFiles(): Set<String> =
        photoDir.listFiles().orEmpty().mapTo(mutableSetOf()) { "vehicles/${it.name}" }

    private fun save(vm: VehicleEditViewModel) {
        var saved = false
        vm.save { saved = true }
        awaitUntil { saved }
    }

    private fun awaitLoaded(vm: VehicleEditViewModel) = awaitUntil { !vm.state.value.isLoading }

    /** Waits for back-out cleanup, which runs on the app scope. */
    private fun awaitCleanup() = runBlocking { appScope.coroutineContext.job.children.toList().joinAll() }

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

    /** Lets work already finishing on other threads resume on the main
     *  looper — here, the photo copy handing its path back. */
    private fun settle() {
        repeat(20) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
    }

    /** Preferences whose first read waits for [gate]: holds a ViewModel's
     *  load back while the test delivers a picker result. */
    private class GatedDataStore(
        private val delegate: DataStore<Preferences>,
        private val gate: CompletableDeferred<Unit>,
    ) : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow {
            gate.await()
            emitAll(delegate.data)
        }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            delegate.updateData(transform)
    }
}
