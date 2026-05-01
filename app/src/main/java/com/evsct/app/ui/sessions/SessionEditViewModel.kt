package com.evsct.app.ui.sessions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.data.entity.ChargingType
import com.evsct.app.data.entity.PricingModel
import com.evsct.app.data.entity.Trip
import com.evsct.app.data.entity.Vehicle
import com.evsct.app.data.repository.SessionRepository
import com.evsct.app.data.repository.TripRepository
import com.evsct.app.data.repository.VehicleRepository
import android.net.Uri
import com.evsct.app.ui.navigation.Routes
import com.evsct.app.util.AutofillResult
import com.evsct.app.util.DurationFormat
import com.evsct.app.util.LocationAutofill
import com.evsct.app.util.ReceiptImageStore
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
    val vehicleId: Long? = null,
    val notes: String = "",
    val receiptImagePath: String? = null,
    val brandSuggestions: List<String> = emptyList(),
    val citySuggestions: List<String> = emptyList(),
    val recentStops: List<RecentStop> = emptyList(),
    val trips: List<Trip> = emptyList(),
    val vehicles: List<Vehicle> = emptyList(),
    val isFetchingLocation: Boolean = false,
    val locationMessage: String? = null,
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
            if (sessionId > 0) {
                val s = sessionRepository.findById(sessionId)
                if (s != null) loadFrom(s) else _state.update { it.copy(isLoading = false, isNew = true) }
            } else {
                val initialVehicleId = preselectVehicleId
                    ?: vehicleRepository.findDefault()?.id
                _state.update { it.copy(isLoading = false, isNew = true, vehicleId = initialVehicleId) }
            }
        }
    }

    private fun loadFrom(s: ChargingSession) {
        _state.update { current ->
            withHints(current.copy(
                isLoading = false,
                isNew = false,
                sessionStart = s.sessionStart,
                durationText = DurationFormat.pretty(s.durationSeconds),
                odometerText = s.odometerKm?.toString().orEmpty(),
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
                vehicleId = s.vehicleId,
                notes = s.notes.orEmpty(),
                receiptImagePath = s.receiptImagePath,
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

        val odo = form.odometerText.toDoubleOrNull()
        val prevOdo = previous?.odometerKm
        if (odo != null && prevOdo != null && odo < prevOdo) {
            out += ValidationHint(
                title = "Odometer went backward",
                detail = "Previous session: ${"%,.0f".format(prevOdo)} km · Now: ${"%,.0f".format(odo)} km. Check for typos.",
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
            val previous = _state.value.receiptImagePath
            val path = receiptImageStore.copyFromUri(uri)
            _state.update { it.copy(receiptImagePath = path) }
            if (previous != null) receiptImageStore.delete(previous)
        }
    }

    fun clearReceipt() {
        viewModelScope.launch {
            val previous = _state.value.receiptImagePath
            _state.update { it.copy(receiptImagePath = null) }
            if (previous != null) receiptImageStore.delete(previous)
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
        listOfNotNull(
            s.brand?.trim()?.lowercase()?.takeIf { it.isNotEmpty() },
            (s.locationAddress ?: s.stationName)?.trim()?.lowercase()?.takeIf { it.isNotEmpty() },
            s.locationCity?.trim()?.lowercase()?.takeIf { it.isNotEmpty() },
        ).joinToString("|")

    fun save(onSaved: () -> Unit) {
        viewModelScope.launch {
            val s = _state.value
            val session = ChargingSession(
                id = if (s.isNew) 0 else sessionId,
                sessionStart = s.sessionStart,
                durationSeconds = DurationFormat.parse(s.durationText),
                odometerKm = s.odometerText.toDoubleOrNull(),
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
                vehicleId = s.vehicleId,
                notes = s.notes.takeIf { it.isNotBlank() },
                receiptImagePath = s.receiptImagePath,
            )
            sessionRepository.upsert(session)
            onSaved()
        }
    }

    fun autofillFromLocation() {
        viewModelScope.launch {
            _state.update { it.copy(isFetchingLocation = true, locationMessage = null) }
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
                        )
                    }
                    "Filled from current location."
                }
            }
            _state.update { it.copy(isFetchingLocation = false, locationMessage = message) }
        }
    }

    fun clearLocationMessage() = _state.update { it.copy(locationMessage = null) }

    fun deleteAndExit(onDeleted: () -> Unit) {
        viewModelScope.launch {
            if (sessionId > 0) {
                sessionRepository.findById(sessionId)?.let {
                    sessionRepository.delete(it)
                    receiptImageStore.delete(it.receiptImagePath)
                }
            }
            onDeleted()
        }
    }

}
