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
import com.evsct.app.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val brandSuggestions: List<String> = emptyList(),
    val citySuggestions: List<String> = emptyList(),
    val trips: List<Trip> = emptyList(),
    val vehicles: List<Vehicle> = emptyList(),
)

@HiltViewModel
class SessionEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionRepository: SessionRepository,
    private val tripRepository: TripRepository,
    private val vehicleRepository: VehicleRepository,
) : ViewModel() {

    private val sessionId: Long = savedStateHandle.get<Long>(Routes.SESSION_EDIT_ARG) ?: -1L

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
                val defaultVehicle = vehicleRepository.findDefault()
                _state.update { it.copy(isLoading = false, isNew = true, vehicleId = defaultVehicle?.id) }
            }
        }
    }

    private fun loadFrom(s: ChargingSession) {
        _state.update {
            it.copy(
                isLoading = false,
                isNew = false,
                sessionStart = s.sessionStart,
                durationText = s.durationSeconds?.let { secs -> formatDurationInput(secs) } ?: "",
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
            )
        }
    }

    fun update(transform: (SessionEditUi) -> SessionEditUi) = _state.update(transform)

    fun save(onSaved: () -> Unit) {
        viewModelScope.launch {
            val s = _state.value
            val session = ChargingSession(
                id = if (s.isNew) 0 else sessionId,
                sessionStart = s.sessionStart,
                durationSeconds = parseDurationInput(s.durationText),
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
            )
            sessionRepository.upsert(session)
            onSaved()
        }
    }

    fun deleteAndExit(onDeleted: () -> Unit) {
        viewModelScope.launch {
            if (sessionId > 0) {
                sessionRepository.findById(sessionId)?.let { sessionRepository.delete(it) }
            }
            onDeleted()
        }
    }

    private fun formatDurationInput(secs: Long): String {
        val h = secs / 3600
        val m = (secs % 3600) / 60
        val s = secs % 60
        return "%d:%02d:%02d".format(h, m, s)
    }

    private fun parseDurationInput(text: String): Long? {
        val t = text.trim()
        if (t.isEmpty()) return null
        return runCatching {
            val parts = t.split(":").map { it.toInt() }
            when (parts.size) {
                3 -> parts[0] * 3600L + parts[1] * 60L + parts[2]
                2 -> parts[0] * 60L + parts[1]
                1 -> parts[0] * 60L
                else -> null
            }
        }.getOrNull()
    }
}
