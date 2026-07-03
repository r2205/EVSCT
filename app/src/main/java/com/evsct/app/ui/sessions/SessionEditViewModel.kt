package com.evsct.app.ui.sessions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.evsct.app.data.db.EvsctDatabase
import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.data.entity.ChargingType
import com.evsct.app.data.entity.PricingModel
import com.evsct.app.data.entity.Trip
import com.evsct.app.data.entity.Vehicle
import com.evsct.app.data.prefs.AppPreferences
import com.evsct.app.data.prefs.UserUnits
import com.evsct.app.data.entity.SessionReceipt
import com.evsct.app.data.repository.SessionReceiptRepository
import com.evsct.app.data.repository.SessionRepository
import com.evsct.app.data.repository.TripRepository
import com.evsct.app.data.repository.VehicleRepository
import android.net.Uri
import com.evsct.app.di.AppScope
import com.evsct.app.ui.navigation.Routes
import com.evsct.app.util.AutofillResult
import com.evsct.app.util.DurationFormat
import com.evsct.app.util.Format
import com.evsct.app.util.InProgressChargeNotifier
import com.evsct.app.util.LocationAutofill
import com.evsct.app.util.ReceiptImageStore
import com.evsct.app.util.Tags
import com.evsct.app.util.Units
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class HintField {
    ODOMETER, ENERGY, COST, DURATION,
    POSTED_ENERGY_PRICE, POSTED_TIME_RATE, POSTED_MAX_POWER,
    BATTERY_START, BATTERY_END,
}

data class ValidationHint(
    val title: String,
    val detail: String,
    val fields: Set<HintField> = emptySet(),
)

data class RecentStop(
    val brand: String?,
    val city: String?,
    val province: String?,
    val address: String?,
    val stationName: String?,
    val stallName: String?,
    val lastUsedAt: Long,
    val visits: Int,
) {
    /** Single line that scans well in a list row. */
    val primary: String get() = listOfNotNull(
        brand?.trim()?.takeIf { it.isNotEmpty() },
        (address ?: stationName)?.trim()?.takeIf { it.isNotEmpty() },
    ).joinToString(" · ").ifEmpty { city ?: "Unknown stop" }

    val secondary: String? get() {
        val cityProv = listOfNotNull(
            city?.trim()?.takeIf { it.isNotEmpty() },
            province?.trim()?.takeIf { it.isNotEmpty() },
        ).joinToString(", ")
        return cityProv.takeIf { it.isNotEmpty() }
    }
}

/**
 * One row in the edit screen's Receipts list. [id] is null for receipts the
 * user just attached (we insert on save); non-null for receipts that are
 * already persisted (we hand the id back on save so we know what to delete
 * if the row was removed).
 */
data class UiReceipt(
    val id: Long?,
    val filePath: String,
    /** Display name from the original picker (e.g. "expense-aug-2025.pdf").
     *  Null when the picker didn't surface a name; UI falls back to a
     *  generic label. */
    val originalFileName: String? = null,
)

data class SessionEditUi(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val sessionStart: Long = System.currentTimeMillis(),
    val durationText: String = "",
    /** Optional integer-minutes-as-string for the wait-before-charging field.
     *  Empty when unset; never affects kWh/cost computations. */
    val waitTimeText: String = "",
    val odometerText: String = "",
    val energyText: String = "",
    val costText: String = "",
    val currency: String = "CAD",
    /** Mirror of the user pref so the form can render the odometer label and
     *  convert input to canonical km on save. */
    val useMiles: Boolean = false,
    val postedEnergyPriceText: String = "",
    val postedTimeRateText: String = "",
    val postedMaxPowerText: String = "",
    val batteryStartText: String = "",
    val batteryEndText: String = "",
    val chargingType: ChargingType = ChargingType.DC_FAST,
    val pricingModel: PricingModel = PricingModel.PER_KWH,
    val brand: String = "",
    val city: String = "",
    val province: String = "",
    val address: String = "",
    val stationName: String = "",
    val stallName: String = "",
    val tripId: Long? = null,
    val continuesPrevious: Boolean = false,
    val vehicleId: Long? = null,
    val notes: String = "",
    /** Free-form tags for this session, parsed from the comma-joined storage
     *  string. The screen renders these as removable chips. */
    val tags: List<String> = emptyList(),
    /** Receipts currently attached to this session — already-saved DB rows
     *  plus speculative copies the user just attached. `id == null` means
     *  "not yet in the DB"; save() inserts those rows. The screen renders
     *  this list as a vertical stack of thumbnail / PDF tiles. */
    val receipts: List<UiReceipt> = emptyList(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val brandSuggestions: List<String> = emptyList(),
    val citySuggestions: List<String> = emptyList(),
    val recentStops: List<RecentStop> = emptyList(),
    val trips: List<Trip> = emptyList(),
    val vehicles: List<Vehicle> = emptyList(),
    val isFetchingLocation: Boolean = false,
    val transientMessage: String? = null,
    val hints: List<ValidationHint> = emptyList(),
    /** True when this edit corresponds to a live-tracked charge (the user
     *  reached this screen via "Start charge" or by tapping the in-progress
     *  notification). Surfaces the live-elapsed chip and seeds duration from
     *  the stopwatch on first open. False after process death — the
     *  notifier's in-memory state is the source of truth. */
    val isTracking: Boolean = false,
)

@HiltViewModel
class SessionEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    /** Only for [androidx.room.withTransaction] in save(): the session row
     *  and its receipt-row reconciliation must commit atomically. All data
     *  access still goes through the repositories. */
    private val database: EvsctDatabase,
    private val sessionRepository: SessionRepository,
    private val sessionReceiptRepository: SessionReceiptRepository,
    private val tripRepository: TripRepository,
    private val vehicleRepository: VehicleRepository,
    private val locationAutofill: LocationAutofill,
    private val receiptImageStore: ReceiptImageStore,
    private val appPreferences: AppPreferences,
    private val inProgressChargeNotifier: InProgressChargeNotifier,
    @AppScope private val appScope: CoroutineScope,
) : ViewModel() {

    /** Cached list of every session, used to derive validation hints (e.g.
     *  comparing the current odometer to the previous session's). */
    private var allSessionsCache: List<ChargingSession> = emptyList()

    private val sessionId: Long = savedStateHandle.get<Long>(Routes.SESSION_EDIT_ARG) ?: -1L
    private val preselectVehicleId: Long? =
        savedStateHandle.get<Long>(Routes.SESSION_PRESELECT_VEHICLE_ARG)
            ?.takeIf { it > 0 }

    private val _state = MutableStateFlow(SessionEditUi())
    val state: StateFlow<SessionEditUi> = _state.asStateFlow()

    /** Receipts the DB referenced at load time. Stays alive on disk until
     *  save() commits a different set or the user trashes the session. */
    private var originalReceiptPaths: Set<String> = emptySet()

    /** Every receipt file we've copied to disk during this edit session.
     *  Includes the originals (if any) so cleanup logic can iterate one set. */
    private val touchedReceiptPaths = mutableSetOf<String>()

    /** Becomes true once save() or deleteAndExit() has fully reconciled the
     *  filesystem with the form state. onCleared() uses this to decide
     *  whether to roll back speculative file copies. */
    @Volatile private var receiptCleanupHandled = false

    /** Re-entry guard for save()/deleteAndExit(). Both suspend on Room and
     *  then pop the back stack, so a double-tap would otherwise insert two
     *  rows (id == 0 inserts twice) and pop the navigator twice. Volatile
     *  because the reset runs in invokeOnCompletion, off the main thread. */
    @Volatile private var commitInFlight = false

    /** Latched once a commit has succeeded and navigation-out was requested.
     *  commitInFlight alone isn't enough: the save coroutine can finish (and
     *  re-arm) before the exit transition does, letting a late tap start a
     *  second commit on a screen that's already leaving. A failed commit
     *  doesn't set it, so retries still work. Reset only by
     *  [onScreenResumed] — the pop that onSaved/onDeleted requests goes
     *  through ifResumed, which silently drops it mid-transition, and a
     *  screen stuck visible with this latched would have permanently dead
     *  Save/Delete buttons. */
    @Volatile private var exitRequested = false

    /** Row id committed by a save on this screen. Normally irrelevant (the
     *  screen pops right after), but when the pop is dropped and the user
     *  saves again, a screen opened as "new" must update this row instead
     *  of inserting a duplicate — and Delete must target it too. */
    @Volatile private var committedSessionId: Long? = null

    /** Coordinates picked on the map, waiting to propagate to sibling
     *  sessions (same stop) — consumed by save(). Deferred on purpose:
     *  the picker used to rewrite sibling rows the moment the pick came
     *  back, so backing out of a mispick without saving still left every
     *  other visit to that stop sitting on the abandoned point. */
    @Volatile private var pendingPickPropagation: Pair<Double, Double>? = null

    /** Called by the screen on every ON_RESUME of its nav entry. A screen
     *  that actually popped never reaches RESUMED again, so resuming with
     *  [exitRequested] still latched means the requested pop was dropped
     *  (ifResumed swallows navigation while the entry is mid-transition).
     *  Re-arm Save/Delete instead of leaving the visible screen inert. */
    fun onScreenResumed() {
        exitRequested = false
    }

    /** The session's stored odometer in km at load time, plus the formatted
     *  display text we put into the form. Used by save() and the validation
     *  hints to short-circuit the lossy display→km round-trip when the user
     *  hasn't actually edited the field. In miles mode 100 km → "62.1" mi →
     *  99.94 km, which would silently rewrite the value on any no-op save
     *  and falsely trigger the "odometer went backward" hint by ~0.1 km. */
    private var originalOdometerKm: Double? = null
    private var originalOdometerText: String = ""

    /** Loaded row's creation timestamp. The save path rebuilds the entity
     *  from form state, and the data-class default would stamp createdAt
     *  with "now" on every edit — progressively corrupting creation dates
     *  (which round-trip into backups). Null for brand-new sessions. */
    private var originalCreatedAt: Long? = null

    /** Geocode query (address, city, province) at load time. When the user
     *  edits the address fields the stored lat/lng become stale — the pin on
     *  the map would otherwise stay at the old location. save() compares
     *  this to the current form's query and clears coords + kicks off a
     *  fresh geocode when they diverge. */
    private var originalGeocodeQuery: String? = null

    init {
        // Single collector that pushes brand/city changes through to the
        // in-progress notifier. Funnels every state mutation (typed,
        // GPS-autofilled, recent-stop-applied, map-picked) through the
        // same path so the shade entry stays in sync without scattering
        // manual notifier calls across each mutation site. Keyed to just
        // the fields the notification renders: without the distinct,
        // every keystroke in ANY field (a 40-character note, say) meant a
        // notification re-post and a DataStore disk write while
        // live-tracking.
        viewModelScope.launch {
            _state
                .map { Triple(it.brand, it.city, it.sessionStart) }
                .distinctUntilChanged()
                .collect { (brand, city, start) ->
                    refreshInProgressNotification(brand, city, start)
                }
        }
        viewModelScope.launch {
            sessionRepository.observeBrands().collect { brands ->
                _state.update { it.copy(brandSuggestions = brands) }
            }
        }
        viewModelScope.launch {
            sessionRepository.observeCities().collect { cities ->
                _state.update { it.copy(citySuggestions = cities) }
            }
        }
        viewModelScope.launch {
            sessionRepository.observeAll().collect { sessions ->
                allSessionsCache = sessions
                _state.update {
                    it.copy(recentStops = computeRecentStops(sessions))
                        .let(::withHints)
                }
            }
        }
        viewModelScope.launch {
            tripRepository.observeAll().collect { _state.update { s -> s.copy(trips = it) } }
        }
        viewModelScope.launch {
            vehicleRepository.observeAll().collect { _state.update { s -> s.copy(vehicles = it) } }
        }
        viewModelScope.launch {
            // Capture the user's units once at load time so the odometer
            // input/display stays in a single coordinate space for the lifetime
            // of this edit session. Toggling the pref later won't surprise the
            // user mid-edit.
            val units = appPreferences.userUnits.first()
            if (sessionId > 0) {
                val s = sessionRepository.findById(sessionId)
                if (s != null) loadFrom(s, units)
                else _state.update { it.copy(isLoading = false, isNew = true, useMiles = units.useMiles) }
            } else {
                val initialVehicleId = preselectVehicleId
                    ?: vehicleRepository.findDefault()?.id
                _state.update {
                    it.copy(
                        isLoading = false,
                        isNew = true,
                        vehicleId = initialVehicleId,
                        currency = units.defaultCurrency,
                        useMiles = units.useMiles,
                    )
                }
            }
        }
    }

    /** Refresh the in-progress notification with the current brand/city so
     *  the shade entry stays in sync as the user fills in the form. No-op
     *  unless the notifier is already tracking this session id, so editing
     *  an unrelated past session never accidentally posts a new
     *  notification. */
    private fun refreshInProgressNotification(brand: String, city: String, sessionStart: Long) {
        if (sessionId <= 0) return
        inProgressChargeNotifier.updateIfTracking(
            sessionId = sessionId,
            brand = brand.takeIf { it.isNotBlank() },
            city = city.takeIf { it.isNotBlank() },
            sessionStart = sessionStart,
        )
    }

    private suspend fun loadFrom(s: ChargingSession, units: UserUnits) {
        // Receipts now live in their own table — pull the list and seed both
        // the form state and the deferred-delete tracking set. The legacy
        // receiptImagePath on the entity is left null going forward; the
        // v9→v10 migration has already promoted any old value into the new
        // table, so we ignore the column here.
        val storedReceipts = sessionReceiptRepository.findForSession(s.id)
        val storedUiReceipts = storedReceipts.map {
            UiReceipt(id = it.id, filePath = it.filePath, originalFileName = it.originalFileName)
        }
        originalReceiptPaths = storedReceipts.mapTo(mutableSetOf()) { it.filePath }
        touchedReceiptPaths += originalReceiptPaths
        val odoText = s.odometerKm?.let {
            val display = Units.kmToDisplay(it, units.useMiles)
            // Trim trailing .0 for whole numbers to match the rest of the app's
            // text fields, otherwise leave one decimal of precision. Locale.US
            // pins the decimal separator to '.' — the default locale would
            // seed "62,1" on comma-decimal devices, which the save-path parse
            // rejects, silently nulling the odometer on the next edit.
            if (display % 1.0 == 0.0) display.toLong().toString()
            else "%.1f".format(Locale.US, display)
        }.orEmpty()
        originalOdometerKm = s.odometerKm
        originalOdometerText = odoText
        originalCreatedAt = s.createdAt
        originalGeocodeQuery = geocodeQueryFor(
            address = s.locationAddress,
            stationName = s.stationName,
            city = s.locationCity,
            province = s.locationProvince,
        )
        val tracking = inProgressChargeNotifier.isTrackingForSession(sessionId)
        // Seed an empty duration field with the live elapsed time when this
        // is a tracked charge — the user can either keep it or overwrite with
        // the station's reported total. Keep the DB value verbatim if it
        // already has one (re-opening a tracked session you've already
        // edited shouldn't clobber what you typed).
        val durationFromDb = DurationFormat.pretty(s.durationSeconds)
        val durationText = if (tracking && durationFromDb.isBlank()) {
            val elapsedSec = ((System.currentTimeMillis() - s.sessionStart)
                .coerceAtLeast(0L)) / 1000L
            DurationFormat.pretty(elapsedSec)
        } else durationFromDb
        _state.update { current ->
            withHints(current.copy(
                isLoading = false,
                isNew = false,
                sessionStart = s.sessionStart,
                durationText = durationText,
                waitTimeText = s.waitTimeMinutes?.toString().orEmpty(),
                isTracking = tracking,
                odometerText = odoText,
                useMiles = units.useMiles,
                energyText = s.energyKwh?.toString().orEmpty(),
                costText = s.totalCost?.toString().orEmpty(),
                currency = s.currency,
                postedEnergyPriceText = s.postedEnergyPricePerKwh?.toString().orEmpty(),
                postedTimeRateText = s.postedTimeRatePerMin?.toString().orEmpty(),
                postedMaxPowerText = s.postedMaxPowerKw?.toString().orEmpty(),
                batteryStartText = s.batteryStartPct?.toString().orEmpty(),
                batteryEndText = s.batteryEndPct?.toString().orEmpty(),
                chargingType = s.chargingType,
                pricingModel = s.pricingModel,
                brand = s.brand.orEmpty(),
                city = s.locationCity.orEmpty(),
                province = s.locationProvince.orEmpty(),
                address = s.locationAddress.orEmpty(),
                stationName = s.stationName.orEmpty(),
                stallName = s.stallName.orEmpty(),
                tripId = s.tripId,
                continuesPrevious = s.continuesPrevious,
                vehicleId = s.vehicleId,
                notes = s.notes.orEmpty(),
                tags = Tags.parse(s.tags),
                receipts = storedUiReceipts,
                latitude = s.latitude,
                longitude = s.longitude,
            ))
        }
    }

    fun update(transform: (SessionEditUi) -> SessionEditUi) =
        _state.update { withHints(transform(it)) }

    private fun withHints(form: SessionEditUi): SessionEditUi {
        val prev = previousSessionFor(form)
        return form.copy(hints = computeHints(form, prev))
    }

    private fun previousSessionFor(form: SessionEditUi): ChargingSession? {
        val vid = form.vehicleId ?: return null
        return allSessionsCache.asSequence()
            .filter { it.vehicleId == vid }
            .filter { it.id != sessionId }            // ignore the session we're currently editing
            .filter { it.sessionStart < form.sessionStart }
            .maxByOrNull { it.sessionStart }
    }

    private fun computeHints(
        form: SessionEditUi,
        previous: ChargingSession?,
    ): List<ValidationHint> {
        val out = mutableListOf<ValidationHint>()

        // Odometer text is in the user's preferred unit, but stored values
        // are km; normalize both to km before comparing.
        val odoEntered = Format.parseDecimal(form.odometerText)
        val odoKm = currentOdometerKm(form)
        val prevOdoKm = previous?.odometerKm
        if (odoKm != null && prevOdoKm != null && odoKm < prevOdoKm) {
            val unit = Units.distanceUnit(form.useMiles)
            val prevDisplay = Units.kmToDisplay(prevOdoKm, form.useMiles)
            out += ValidationHint(
                title = "Odometer went backward",
                detail = "Previous session: ${"%,.0f".format(prevDisplay)} $unit · " +
                    "Now: ${"%,.0f".format(odoEntered)} $unit. Check for typos.",
                fields = setOf(HintField.ODOMETER),
            )
        }

        val cost = Format.parseDecimal(form.costText)
        val energy = Format.parseDecimal(form.energyText)
        val durationSec = DurationFormat.parse(form.durationText)

        val effPricePerKwh = if (cost != null && energy != null && energy > 0) cost / energy else null
        val postedPrice = Format.parseDecimal(form.postedEnergyPriceText)
        if (effPricePerKwh != null && postedPrice != null && postedPrice > 0) {
            val deviation = kotlin.math.abs(effPricePerKwh - postedPrice) / postedPrice
            if (deviation > 0.25) {
                out += ValidationHint(
                    title = "Effective $/kWh differs from posted",
                    detail = "Posted ${"%.3f".format(postedPrice)} · Effective ${"%.3f".format(effPricePerKwh)}. Check kWh or cost.",
                    fields = setOf(HintField.COST, HintField.ENERGY, HintField.POSTED_ENERGY_PRICE),
                )
            }
        }

        val effPerMin = if (cost != null && durationSec != null && durationSec > 0)
            cost / (durationSec / 60.0) else null
        val postedTimeRate = Format.parseDecimal(form.postedTimeRateText)
        if (effPerMin != null && postedTimeRate != null && postedTimeRate > 0) {
            val deviation = kotlin.math.abs(effPerMin - postedTimeRate) / postedTimeRate
            if (deviation > 0.25) {
                out += ValidationHint(
                    title = "Effective $/min differs from posted",
                    detail = "Posted ${"%.3f".format(postedTimeRate)} · Effective ${"%.3f".format(effPerMin)}. Check duration or cost.",
                    fields = setOf(HintField.COST, HintField.DURATION, HintField.POSTED_TIME_RATE),
                )
            }
        }

        val avgPower = if (energy != null && durationSec != null && durationSec > 0)
            energy / (durationSec / 3600.0) else null
        val postedMaxKw = Format.parseDecimal(form.postedMaxPowerText)
        if (avgPower != null && postedMaxKw != null && postedMaxKw > 0 && avgPower > postedMaxKw * 1.05) {
            out += ValidationHint(
                title = "Avg power exceeds posted max",
                detail = "Posted max ${"%.0f".format(postedMaxKw)} kW · Effective avg ${"%.1f".format(avgPower)} kW. Check kWh or duration.",
                fields = setOf(HintField.ENERGY, HintField.DURATION, HintField.POSTED_MAX_POWER),
            )
        }

        val battStart = form.batteryStartText.toIntOrNull()
        val battEnd = form.batteryEndText.toIntOrNull()
        if (battStart != null && battEnd != null && battEnd < battStart) {
            out += ValidationHint(
                title = "Battery decreased during charge",
                detail = "Start $battStart% → End $battEnd%. Did you swap them?",
                fields = setOf(HintField.BATTERY_START, HintField.BATTERY_END),
            )
        }

        val battOutOfRange = mutableSetOf<HintField>()
        if (battStart != null && battStart !in 0..100) battOutOfRange += HintField.BATTERY_START
        if (battEnd != null && battEnd !in 0..100) battOutOfRange += HintField.BATTERY_END
        if (battOutOfRange.isNotEmpty()) {
            out += ValidationHint(
                title = "Battery % out of range",
                detail = "Battery percent should be between 0 and 100.",
                fields = battOutOfRange,
            )
        }

        val negative = mutableSetOf<HintField>()
        if (odoEntered != null && odoEntered < 0) negative += HintField.ODOMETER
        if (energy != null && energy < 0) negative += HintField.ENERGY
        if (cost != null && cost < 0) negative += HintField.COST
        if (postedPrice != null && postedPrice < 0) negative += HintField.POSTED_ENERGY_PRICE
        if (postedTimeRate != null && postedTimeRate < 0) negative += HintField.POSTED_TIME_RATE
        if (postedMaxKw != null && postedMaxKw < 0) negative += HintField.POSTED_MAX_POWER
        if (negative.isNotEmpty()) {
            out += ValidationHint(
                title = "Negative value entered",
                detail = "These fields don't normally go below zero. Check for a stray minus sign.",
                fields = negative,
            )
        }

        return out
    }

    fun addReceipt(uri: Uri) {
        viewModelScope.launch {
            // Copy the new file to disk and track it. Don't delete anything
            // yet — already-attached receipts stay valid until save() commits
            // the diff, so backing out without saving leaves the DB intact.
            val copied = try {
                receiptImageStore.copyFromUri(uri)
            } catch (e: com.evsct.app.util.FileTooLargeException) {
                val mb = e.limitBytes / (1024 * 1024)
                _state.update { it.copy(transientMessage = "Receipt is too large (max ${mb} MB).") }
                return@launch
            } catch (e: Exception) {
                // Picker URIs can become invalid between selection and read
                // (cloud storage hand-off, permission revoke, etc.). Surface
                // a snackbar instead of letting the throw kill the screen.
                _state.update { it.copy(transientMessage = "Could not attach receipt. Try again or pick a different file.") }
                return@launch
            }
            touchedReceiptPaths += copied.filePath
            _state.update {
                it.copy(
                    receipts = it.receipts + UiReceipt(
                        id = null,
                        filePath = copied.filePath,
                        originalFileName = copied.displayName,
                    ),
                )
            }
        }
    }

    /** Remove one receipt from the in-memory list. The file stays on disk
     *  until save() commits the diff — backing out without saving keeps
     *  the original receipts intact (touchedReceiptPaths handles cleanup
     *  of newly-attached files that the user later removed before saving). */
    fun removeReceipt(path: String) {
        _state.update { it.copy(receipts = it.receipts.filterNot { r -> r.filePath == path }) }
    }

    /** Update the user-facing label on an existing receipt. Lets the user
     *  backfill a name onto pre-v11 receipts that were attached before we
     *  started capturing the picker's display name. Persisted in save().
     *  For PDFs, auto-appends ".pdf" when the user-typed name doesn't
     *  already end in it (case-insensitive) — matches what the picker
     *  produces and keeps the tile consistent. */
    fun renameReceipt(path: String, newName: String?) {
        val trimmed = newName?.trim().orEmpty()
        val cleaned = when {
            trimmed.isEmpty() -> null
            com.evsct.app.util.ReceiptImageStore.isPdf(path) &&
                !trimmed.endsWith(".pdf", ignoreCase = true) -> "$trimmed.pdf"
            else -> trimmed
        }
        _state.update { current ->
            current.copy(
                receipts = current.receipts.map {
                    if (it.filePath == path) it.copy(originalFileName = cleaned) else it
                },
            )
        }
    }

    fun applyStop(stop: RecentStop) = _state.update { current ->
        current.copy(
            brand = stop.brand?.trim().orEmpty(),
            city = stop.city.orEmpty(),
            province = stop.province.orEmpty(),
            address = stop.address.orEmpty(),
            stationName = stop.stationName.orEmpty(),
            stallName = stop.stallName.orEmpty(),
        )
    }

    private fun computeRecentStops(sessions: List<ChargingSession>): List<RecentStop> {
        if (sessions.isEmpty()) return emptyList()
        return sessions
            .filter {
                !it.brand.isNullOrBlank() ||
                    !it.locationCity.isNullOrBlank() ||
                    !it.locationAddress.isNullOrBlank()
            }
            .groupBy { stopKey(it) }
            .map { (_, group) ->
                val mostRecent = group.maxBy { it.sessionStart }
                RecentStop(
                    brand = mostRecent.brand,
                    city = mostRecent.locationCity,
                    province = mostRecent.locationProvince,
                    address = mostRecent.locationAddress,
                    stationName = mostRecent.stationName,
                    stallName = mostRecent.stallName,
                    lastUsedAt = mostRecent.sessionStart,
                    visits = group.size,
                )
            }
            .sortedByDescending { it.lastUsedAt }
    }

    private fun stopKey(s: ChargingSession): String =
        stopKey(brand = s.brand, address = s.locationAddress, city = s.locationCity)

    /** Stops are grouped by brand + address + city only. Station/stall name
     *  is intentionally NOT part of the key — visits to the same physical
     *  charger should group together even when each visit logs a different
     *  stall number. */
    private fun stopKey(brand: String?, address: String?, city: String?): String =
        listOfNotNull(
            brand?.trim()?.lowercase()?.takeIf { it.isNotEmpty() },
            address?.trim()?.lowercase()?.takeIf { it.isNotEmpty() },
            city?.trim()?.lowercase()?.takeIf { it.isNotEmpty() },
        ).joinToString("|")

    /** Resolve the current odometer reading in km. When the user hasn't
     *  edited the displayed text, return the loaded km verbatim — converting
     *  display→km lossily would silently rewrite the stored value (and skew
     *  efficiency calcs that depend on prev/curr odometer differences). */
    private fun currentOdometerKm(form: SessionEditUi): Double? =
        if (form.odometerText == originalOdometerText) originalOdometerKm
        else Format.parseDecimal(form.odometerText)?.let { Units.displayToKm(it, form.useMiles) }

    /** Build the same ", "-joined address query the map screen uses for its
     *  reverse-geocode backfill, so the two stay consistent. */
    private fun geocodeQueryFor(
        address: String?,
        stationName: String?,
        city: String?,
        province: String?,
    ): String? = listOfNotNull(
        address?.takeIf { it.isNotBlank() } ?: stationName?.takeIf { it.isNotBlank() },
        city?.takeIf { it.isNotBlank() },
        province?.takeIf { it.isNotBlank() },
    ).joinToString(", ").takeIf { it.isNotBlank() }

    fun save(onSaved: () -> Unit) {
        if (commitInFlight || exitRequested) return
        commitInFlight = true
        viewModelScope.launch {
            val s = _state.value
            val odometerKm = currentOdometerKm(s)
            val newQuery = geocodeQueryFor(
                address = s.address.takeIf { it.isNotBlank() },
                stationName = s.stationName.takeIf { it.isNotBlank() },
                city = s.city.takeIf { it.isNotBlank() },
                province = s.province.takeIf { it.isNotBlank() },
            )
            val addressChanged = newQuery != originalGeocodeQuery
            // When the user edited the address, the stored coords are stale.
            // Save with null lat/lng — the next map open will pick them up via
            // the existing reverse-geocode backfill, and we also try to
            // re-geocode below so the pin updates without needing a fresh
            // map visit.
            val saveLat = if (addressChanged) null else s.latitude
            val saveLng = if (addressChanged) null else s.longitude
            // For tracked charges, fall back to the live elapsed time when
            // the user saves with an empty (or unparseable) duration field —
            // a "I forgot to fill it in" safety net. A typed value or a
            // chip-pinned value parses to non-null and wins as-is.
            val durationSeconds = DurationFormat.parse(s.durationText)
                ?: if (s.isTracking) {
                    ((System.currentTimeMillis() - s.sessionStart)
                        .coerceAtLeast(0L)) / 1000L
                } else null
            val session = ChargingSession(
                // committedSessionId wins: a prior save on this screen
                // already inserted the row (its pop was dropped) and a
                // retry must update it, not insert a duplicate.
                id = committedSessionId ?: if (s.isNew) 0 else sessionId,
                sessionStart = s.sessionStart,
                durationSeconds = durationSeconds,
                waitTimeMinutes = s.waitTimeText.toIntOrNull()?.takeIf { it >= 0 },
                odometerKm = odometerKm,
                energyKwh = Format.parseDecimal(s.energyText),
                totalCost = Format.parseDecimal(s.costText),
                currency = s.currency.ifBlank { "CAD" },
                postedEnergyPricePerKwh = Format.parseDecimal(s.postedEnergyPriceText),
                postedTimeRatePerMin = Format.parseDecimal(s.postedTimeRateText),
                postedMaxPowerKw = Format.parseDecimal(s.postedMaxPowerText),
                batteryStartPct = s.batteryStartText.toIntOrNull(),
                batteryEndPct = s.batteryEndText.toIntOrNull(),
                chargingType = s.chargingType,
                pricingModel = s.pricingModel,
                brand = s.brand.takeIf { it.isNotBlank() },
                locationCity = s.city.takeIf { it.isNotBlank() },
                locationProvince = s.province.takeIf { it.isNotBlank() },
                locationAddress = s.address.takeIf { it.isNotBlank() },
                stationName = s.stationName.takeIf { it.isNotBlank() },
                stallName = s.stallName.takeIf { it.isNotBlank() },
                tripId = s.tripId,
                continuesPrevious = s.continuesPrevious,
                vehicleId = s.vehicleId,
                notes = s.notes.takeIf { it.isNotBlank() },
                tags = Tags.serialize(s.tags),
                // Always null: receipts live in their own table now. The
                // column remains in the schema only to avoid a table rebuild.
                receiptImagePath = null,
                latitude = saveLat,
                longitude = saveLng,
                createdAt = originalCreatedAt ?: System.currentTimeMillis(),
            )
            // The session row and its receipt-row reconciliation commit as
            // ONE transaction: process death between them used to save the
            // session but leave newly attached receipts row-less — files
            // stranded on disk, invisible to the UI and never cleaned
            // (reconcileReceiptFiles hadn't run either). File deletions
            // stay outside, after commit, so a rolled-back transaction
            // can't have already destroyed files its rows still reference.
            val desiredPaths = s.receipts.mapTo(mutableSetOf()) { it.filePath }
            val savedId = database.withTransaction {
                val id = sessionRepository.upsert(session)

                // Diff the in-memory receipts against the DB and apply the
                // delta. Newly added rows (id == null) get inserted; rows
                // that the user removed get deleted. The session_receipts
                // .sessionId FK uses the freshly-upserted id, which is the
                // same as the existing id for updates and the new
                // autoincrement id for inserts.
                val existing = sessionReceiptRepository.findForSession(id)
                val existingById = existing.associateBy { it.id }
                existing
                    .filter { it.filePath !in desiredPaths }
                    .forEach { sessionReceiptRepository.delete(it) }
                val existingPaths = existing.mapTo(mutableSetOf()) { it.filePath }
                val newRows = s.receipts
                    .filter { it.filePath !in existingPaths }
                    .map {
                        SessionReceipt(
                            sessionId = id,
                            filePath = it.filePath,
                            originalFileName = it.originalFileName,
                        )
                    }
                if (newRows.isNotEmpty()) sessionReceiptRepository.insertAll(newRows)
                // Apply renames on already-persisted receipts (matched by
                // id). We skip when the name is unchanged so we don't bump
                // rows that didn't move.
                s.receipts.forEach { r ->
                    val rid = r.id ?: return@forEach
                    val current = existingById[rid] ?: return@forEach
                    if (current.originalFileName != r.originalFileName) {
                        sessionReceiptRepository.updateName(rid, r.originalFileName)
                    }
                }
                id
            }
            committedSessionId = savedId

            // Sibling propagation from the map picker, deferred until the
            // pick is actually committed. Guards: the coords being saved
            // must still be the picked ones (editing the address after the
            // pick clears them via saveLat/saveLng), and the stop needs an
            // address to identify it (propagationKey) — brand or city
            // alone would stamp the point onto unrelated stops.
            pendingPickPropagation?.let { (pickedLat, pickedLng) ->
                if (session.latitude == pickedLat && session.longitude == pickedLng) {
                    val key = propagationKey(
                        brand = session.brand,
                        address = session.locationAddress,
                        city = session.locationCity,
                    )
                    if (key != null) {
                        val siblings = allSessionsCache
                            .filter { it.id != savedId && stopKey(it) == key }
                            .map { it.id }
                        if (siblings.isNotEmpty()) {
                            sessionRepository.setCoordinates(siblings, pickedLat, pickedLng)
                        }
                    }
                }
                pendingPickPropagation = null
            }

            // Drop any speculative copies the user attached and then removed
            // before saving, plus any original files that were removed.
            reconcileReceiptFiles(finalPaths = desiredPaths)
            // The user explicitly hit Save, so they're done with this
            // in-progress entry — drop the persistent notification (and
            // its ticking stopwatch). If they need to keep editing, the
            // session is now in the list. cancelIfFor is a no-op for
            // backfill saves where the notifier was never tracking.
            inProgressChargeNotifier.cancelIfFor(savedId)
            exitRequested = true
            onSaved()

            // Re-geocode in the background when the user changed the
            // address. The navigation has already happened — we just patch
            // the row's coordinates if Geocoder returns a hit. If it
            // doesn't, lat/lng stay null and the map's backfill will retry
            // on next visit. Runs in appScope: popping the screen clears
            // this ViewModel within the exit transition, and viewModelScope
            // cancellation would kill the geocode before it could land.
            if (addressChanged && newQuery != null) {
                appScope.launch {
                    val located = locationAutofill.geocode(
                        address = s.address.takeIf { it.isNotBlank() }
                            ?: s.stationName.takeIf { it.isNotBlank() },
                        city = s.city.takeIf { it.isNotBlank() },
                        province = s.province.takeIf { it.isNotBlank() },
                    )
                    val lat = located?.latitude
                    val lng = located?.longitude
                    if (lat != null && lng != null) {
                        sessionRepository.setCoordinates(listOf(savedId), lat, lng)
                    }
                }
            }
        }.invokeOnCompletion { commitInFlight = false }
    }

    /** Delete every receipt file we touched during this edit session whose
     *  path isn't in [finalPaths]. Called from save() (after upsert commits
     *  and the receipts table is reconciled) and deleteAndExit() (which
     *  passes an empty set because the row is going away). */
    private suspend fun reconcileReceiptFiles(finalPaths: Set<String>) {
        receiptCleanupHandled = true
        val toDelete = touchedReceiptPaths - finalPaths
        toDelete.forEach { receiptImageStore.delete(it) }
    }

    fun autofillFromLocation() {
        viewModelScope.launch {
            _state.update { it.copy(isFetchingLocation = true, transientMessage = null) }
            val message = when (val result = locationAutofill.fetch()) {
                AutofillResult.MissingPermission ->
                    "Location permission is required to use this feature."
                AutofillResult.NoProvider ->
                    "No location provider is enabled. Turn on location services and try again."
                AutofillResult.NoLocation ->
                    "Could not get a location fix. Try moving outside or wait a moment."
                AutofillResult.GeocoderUnavailable ->
                    "Address lookup isn't available on this device."
                is AutofillResult.Failure -> "Lookup failed: ${result.reason}"
                is AutofillResult.Success -> {
                    val data = result.data
                    _state.update {
                        it.copy(
                            city = data.city ?: it.city,
                            province = data.provinceState ?: it.province,
                            address = data.address ?: it.address,
                            latitude = data.latitude ?: it.latitude,
                            longitude = data.longitude ?: it.longitude,
                        )
                    }
                    "Filled from current location."
                }
            }
            _state.update { it.copy(isFetchingLocation = false, transientMessage = message) }
        }
    }

    /** Apply a lat/lng picked from the map picker. The exact coordinates the
     *  user dropped become the source of truth; address fields get
     *  reverse-geocoded so they match what's at that point (only overwriting
     *  when the reverse-geocode returned a value). After applying, the
     *  saved-baseline geocode query is refreshed so save()'s "address
     *  changed by user" detection doesn't fire on this auto-fill and clear
     *  the freshly-picked coords.
     *
     *  The pick is also recorded for sibling propagation — visits to the
     *  same charger should agree on where it is, and the map averages a
     *  stop's visits, so a single re-pick would otherwise be diluted by
     *  older sessions' stale coords. The actual write to those rows is
     *  deferred to save() (see [pendingPickPropagation]); here we only
     *  surface a heads-up so the wider effect isn't a surprise. */
    fun applyPickedLocation(lat: Double, lng: Double) {
        // Commit the picked coordinates synchronously. The reverse-geocode
        // below is a network call that can take seconds — a Save tapped
        // before it returns must snapshot the picked coords, not the
        // pre-pick state (which would silently discard the pick).
        _state.update { it.copy(latitude = lat, longitude = lng) }
        pendingPickPropagation = lat to lng
        viewModelScope.launch {
            val located = locationAutofill.reverseGeocodeAt(lat, lng)
            _state.update {
                it.copy(
                    city = located?.city ?: it.city,
                    province = located?.provinceState ?: it.province,
                    address = located?.address ?: it.address,
                )
            }
            val s = _state.value
            originalGeocodeQuery = geocodeQueryFor(
                address = s.address.takeIf { it.isNotBlank() },
                stationName = s.stationName.takeIf { it.isNotBlank() },
                city = s.city.takeIf { it.isNotBlank() },
                province = s.province.takeIf { it.isNotBlank() },
            )

            val key = propagationKey(
                brand = s.brand.takeIf { it.isNotBlank() },
                address = s.address.takeIf { it.isNotBlank() },
                city = s.city.takeIf { it.isNotBlank() },
            )
            if (key != null) {
                val n = allSessionsCache.count { it.id != sessionId && stopKey(it) == key }
                if (n > 0) {
                    _state.update {
                        it.copy(
                            transientMessage = "$n other session" +
                                (if (n == 1) "" else "s") +
                                " at this stop will move here when you save.",
                        )
                    }
                }
            }
        }
    }

    /** Sibling-propagation key: like [stopKey], but null unless the stop
     *  has an address. Brand or city alone spans many physical locations —
     *  propagating a pick across every address-less "FLO" session in the
     *  log would collapse distinct stops onto one point. */
    private fun propagationKey(brand: String?, address: String?, city: String?): String? {
        if (address.isNullOrBlank()) return null
        return stopKey(brand = brand, address = address, city = city)
    }

    fun clearTransientMessage() = _state.update { it.copy(transientMessage = null) }

    fun deleteAndExit(onDeleted: () -> Unit) {
        if (commitInFlight || exitRequested) return
        commitInFlight = true
        viewModelScope.launch {
            // A save on this screen may already have committed a row even
            // when the entry opened as "new" (dropped-pop recovery) —
            // Delete must target that row, not just the nav-arg id.
            val targetId = committedSessionId ?: sessionId
            if (targetId > 0) {
                sessionRepository.findById(targetId)?.let {
                    sessionRepository.delete(it)
                }
                // The session no longer exists; the in-progress notification
                // for it would tap into a deleted row, so always clear it.
                inProgressChargeNotifier.cancelIfFor(targetId)
            }
            // No row left, so drop every file we touched (originals plus
            // speculative copies). FK CASCADE has already removed the
            // session_receipts rows for the deleted session.
            reconcileReceiptFiles(finalPaths = emptySet())
            exitRequested = true
            onDeleted()
        }.invokeOnCompletion { commitInFlight = false }
    }

    override fun onCleared() {
        super.onCleared()
        // If save()/deleteAndExit() already reconciled, nothing to do.
        if (receiptCleanupHandled) return
        // The user backed out without saving. The database rows still
        // reference originalReceiptPaths, so leave those files alone —
        // but any speculative copies we wrote during this edit are
        // unreferenced and safe to drop. Use the app-scoped scope since
        // viewModelScope is already cancelled at this point.
        val orphans = touchedReceiptPaths - originalReceiptPaths
        if (orphans.isEmpty()) return
        appScope.launch {
            orphans.forEach { receiptImageStore.delete(it) }
        }
    }
}
