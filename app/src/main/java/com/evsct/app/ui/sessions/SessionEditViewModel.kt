package com.evsct.app.ui.sessions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.data.entity.ChargingType
import com.evsct.app.data.entity.PricingModel
import com.evsct.app.data.entity.Trip
import com.evsct.app.data.entity.Vehicle
import com.evsct.app.data.prefs.AppPreferences
import com.evsct.app.data.prefs.UserUnits
import com.evsct.app.data.repository.SessionRepository
import com.evsct.app.data.repository.TripRepository
import com.evsct.app.data.repository.VehicleRepository
import android.net.Uri
import com.evsct.app.di.AppScope
import com.evsct.app.ui.navigation.Routes
import com.evsct.app.util.AutofillResult
import com.evsct.app.util.DurationFormat
import com.evsct.app.util.LocationAutofill
import com.evsct.app.util.ReceiptImageStore
import com.evsct.app.util.Units
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

data class SessionEditUi(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val sessionStart: Long = System.currentTimeMillis(),
    val durationText: String = "",
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
    val receiptImagePath: String? = null,
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
)

@HiltViewModel
class SessionEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionRepository: SessionRepository,
    private val tripRepository: TripRepository,
    private val vehicleRepository: VehicleRepository,
    private val locationAutofill: LocationAutofill,
    private val receiptImageStore: ReceiptImageStore,
    private val appPreferences: AppPreferences,
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

    /** Path the database currently references at load time. Stays alive on
     *  disk until either save() commits a different value or the user trashes
     *  the session. */
    private var originalReceiptPath: String? = null

    /** Every receipt file we've copied to disk during this edit session.
     *  Includes the original (if any) so cleanup logic can iterate one set. */
    private val touchedReceiptPaths = mutableSetOf<String>()

    /** Becomes true once save() or deleteAndExit() has fully reconciled the
     *  filesystem with the form state. onCleared() uses this to decide
     *  whether to roll back speculative file copies. */
    @Volatile private var receiptCleanupHandled = false

    /** The session's stored odometer in km at load time, plus the formatted
     *  display text we put into the form. Used by save() and the validation
     *  hints to short-circuit the lossy display→km round-trip when the user
     *  hasn't actually edited the field. In miles mode 100 km → "62.1" mi →
     *  99.94 km, which would silently rewrite the value on any no-op save
     *  and falsely trigger the "odometer went backward" hint by ~0.1 km. */
    private var originalOdometerKm: Double? = null
    private var originalOdometerText: String = ""

    /** Geocode query (address, city, province) at load time. When the user
     *  edits the address fields the stored lat/lng become stale — the pin on
     *  the map would otherwise stay at the old location. save() compares
     *  this to the current form's query and clears coords + kicks off a
     *  fresh geocode when they diverge. */
    private var originalGeocodeQuery: String? = null

    init {
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

    private fun loadFrom(s: ChargingSession, units: UserUnits) {
        originalReceiptPath = s.receiptImagePath
        s.receiptImagePath?.let { touchedReceiptPaths += it }
        val odoText = s.odometerKm?.let {
            val display = Units.kmToDisplay(it, units.useMiles)
            // Trim trailing .0 for whole numbers to match the rest of the app's
            // text fields, otherwise leave one decimal of precision.
            if (display % 1.0 == 0.0) display.toLong().toString()
            else "%.1f".format(display)
        }.orEmpty()
        originalOdometerKm = s.odometerKm
        originalOdometerText = odoText
        originalGeocodeQuery = geocodeQueryFor(
            address = s.locationAddress,
            stationName = s.stationName,
            city = s.locationCity,
            province = s.locationProvince,
        )
        _state.update { current ->
            withHints(current.copy(
                isLoading = false,
                isNew = false,
                sessionStart = s.sessionStart,
                durationText = DurationFormat.pretty(s.durationSeconds),
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
                receiptImagePath = s.receiptImagePath,
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
        val odoEntered = form.odometerText.toDoubleOrNull()
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

        val cost = form.costText.toDoubleOrNull()
        val energy = form.energyText.toDoubleOrNull()
        val durationSec = DurationFormat.parse(form.durationText)

        val effPricePerKwh = if (cost != null && energy != null && energy > 0) cost / energy else null
        val postedPrice = form.postedEnergyPriceText.toDoubleOrNull()
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
        val postedTimeRate = form.postedTimeRateText.toDoubleOrNull()
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
        val postedMaxKw = form.postedMaxPowerText.toDoubleOrNull()
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

        return out
    }

    fun pickReceipt(uri: Uri) {
        viewModelScope.launch {
            // Copy the new file to disk and track it. Don't delete anything
            // yet — the previous path stays valid until save() commits, so
            // backing out without saving leaves the database row intact.
            val path = try {
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
            touchedReceiptPaths += path
            _state.update { it.copy(receiptImagePath = path) }
        }
    }

    fun clearReceipt() {
        // Pure form-state change — no disk I/O. The actual file deletion
        // happens after save() persists the null path.
        _state.update { it.copy(receiptImagePath = null) }
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
        else form.odometerText.toDoubleOrNull()?.let { Units.displayToKm(it, form.useMiles) }

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
            val session = ChargingSession(
                id = if (s.isNew) 0 else sessionId,
                sessionStart = s.sessionStart,
                durationSeconds = DurationFormat.parse(s.durationText),
                odometerKm = odometerKm,
                energyKwh = s.energyText.toDoubleOrNull(),
                totalCost = s.costText.toDoubleOrNull(),
                currency = s.currency.ifBlank { "CAD" },
                postedEnergyPricePerKwh = s.postedEnergyPriceText.toDoubleOrNull(),
                postedTimeRatePerMin = s.postedTimeRateText.toDoubleOrNull(),
                postedMaxPowerKw = s.postedMaxPowerText.toDoubleOrNull(),
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
                receiptImagePath = s.receiptImagePath,
                latitude = saveLat,
                longitude = saveLng,
            )
            val savedId = sessionRepository.upsert(session)
            // Now that the database row is committed, drop any speculative
            // copies plus the original (if it was replaced or cleared).
            reconcileReceiptFiles(finalPath = session.receiptImagePath)
            onSaved()

            // Re-geocode in the background when the user changed the
            // address. The navigation has already happened — we just patch
            // the row's coordinates if Geocoder returns a hit. If it
            // doesn't, lat/lng stay null and the map's backfill will retry
            // on next visit.
            if (addressChanged && newQuery != null) {
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
    }

    /** Delete every receipt file we touched during this edit session except
     *  the one [finalPath] now points at. Called from save() (after upsert
     *  commits) and deleteAndExit() (which clears the row entirely). */
    private suspend fun reconcileReceiptFiles(finalPath: String?) {
        receiptCleanupHandled = true
        val toDelete = touchedReceiptPaths.filter { it != finalPath }
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
     *  Also propagates the picked coords to every other session that shares
     *  this session's stopKey (brand + address|station + city). Visits to
     *  the same charger should agree on where it is — picking once should
     *  fix the whole stop instead of forcing a per-session edit. The map
     *  averages a stop's visits, so without this a single re-pick would be
     *  diluted by older sessions' stale coords. */
    fun applyPickedLocation(lat: Double, lng: Double) {
        viewModelScope.launch {
            val located = locationAutofill.reverseGeocodeAt(lat, lng)
            _state.update {
                it.copy(
                    latitude = lat,
                    longitude = lng,
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

            val key = stopKey(
                brand = s.brand,
                address = s.address.takeIf { it.isNotBlank() },
                city = s.city.takeIf { it.isNotBlank() },
            )
            if (key.isNotBlank()) {
                val siblings = allSessionsCache
                    .filter { it.id != sessionId && stopKey(it) == key }
                    .map { it.id }
                if (siblings.isNotEmpty()) {
                    sessionRepository.setCoordinates(siblings, lat, lng)
                    val n = siblings.size
                    _state.update {
                        it.copy(
                            transientMessage = "Updated $n other session" +
                                (if (n == 1) "" else "s") + " at this stop.",
                        )
                    }
                }
            }
        }
    }

    fun clearTransientMessage() = _state.update { it.copy(transientMessage = null) }

    fun deleteAndExit(onDeleted: () -> Unit) {
        viewModelScope.launch {
            if (sessionId > 0) {
                sessionRepository.findById(sessionId)?.let {
                    sessionRepository.delete(it)
                }
            }
            // No row left, so the final path is null — drop every file we
            // touched (original + speculative copies).
            reconcileReceiptFiles(finalPath = null)
            onDeleted()
        }
    }

    override fun onCleared() {
        super.onCleared()
        // If save()/deleteAndExit() already reconciled, nothing to do.
        if (receiptCleanupHandled) return
        // The user backed out without saving. The database row (if any) still
        // references originalReceiptPath, so leave that file alone — but any
        // speculative copies we wrote during this edit are unreferenced and
        // safe to drop. Use the app-scoped scope since viewModelScope is
        // already cancelled at this point.
        val orphans = touchedReceiptPaths.filter { it != originalReceiptPath }
        if (orphans.isEmpty()) return
        appScope.launch {
            orphans.forEach { receiptImageStore.delete(it) }
        }
    }
}
