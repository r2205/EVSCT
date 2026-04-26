package com.evsct.app.ui.sessions

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.evsct.app.data.entity.ChargingType
import com.evsct.app.data.entity.PricingModel
import com.evsct.app.util.Brands
import com.evsct.app.util.Format
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionEditScreen(
    onDone: () -> Unit,
    viewModel: SessionEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "New session" else "Edit session") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { viewModel.deleteAndExit(onDone) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                    IconButton(onClick = { viewModel.save(onDone) }) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DateTimeRow(
                epoch = state.sessionStart,
                onPickDateTime = { newEpoch -> viewModel.update { it.copy(sessionStart = newEpoch) } },
                context = context,
            )

            ChargingTypeRow(state.chargingType) { type ->
                viewModel.update { it.copy(chargingType = type) }
            }

            PricingModelRow(state.pricingModel) { pm ->
                viewModel.update { it.copy(pricingModel = pm) }
            }

            SectionLabel("Session")
            NumberField("Energy delivered (kWh)", state.energyText) { v ->
                viewModel.update { it.copy(energyText = v) }
            }
            NumberField("Total cost (${state.currency})", state.costText) { v ->
                viewModel.update { it.copy(costText = v) }
            }
            TextFieldPlain("Charging duration (h:mm:ss or m:ss)", state.durationText) { v ->
                viewModel.update { it.copy(durationText = v) }
            }
            NumberField("Odometer (km)", state.odometerText) { v ->
                viewModel.update { it.copy(odometerText = v) }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                NumberField(
                    label = "Battery start %",
                    value = state.batteryStartText,
                    modifier = Modifier.weight(1f),
                    onValue = { v -> viewModel.update { it.copy(batteryStartText = v) } },
                )
                NumberField(
                    label = "Battery end %",
                    value = state.batteryEndText,
                    modifier = Modifier.weight(1f),
                    onValue = { v -> viewModel.update { it.copy(batteryEndText = v) } },
                )
            }

            SectionLabel("Posted rates (optional)")
            NumberField("Posted energy price ($/kWh)", state.postedEnergyPriceText) { v ->
                viewModel.update { it.copy(postedEnergyPriceText = v) }
            }
            NumberField("Posted time-based rate ($/min)", state.postedTimeRateText) { v ->
                viewModel.update { it.copy(postedTimeRateText = v) }
            }
            NumberField("Posted max power (kW)", state.postedMaxPowerText) { v ->
                viewModel.update { it.copy(postedMaxPowerText = v) }
            }

            SectionLabel("Station")
            BrandPicker(state.brand, state.brandSuggestions) { v ->
                viewModel.update { it.copy(brand = v) }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextFieldPlain(
                    label = "City",
                    value = state.city,
                    modifier = Modifier.weight(2f),
                    onValue = { v -> viewModel.update { it.copy(city = v) } },
                )
                TextFieldPlain(
                    label = "Prov",
                    value = state.province,
                    modifier = Modifier.weight(1f),
                    onValue = { v -> viewModel.update { it.copy(province = v) } },
                )
            }
            TextFieldPlain("Address", state.address) { v ->
                viewModel.update { it.copy(address = v) }
            }
            TextFieldPlain("Station / stall name", state.stationName) { v ->
                viewModel.update { it.copy(stationName = v) }
            }

            SectionLabel("Vehicle")
            VehiclePicker(state) { id -> viewModel.update { it.copy(vehicleId = id) } }

            SectionLabel("Trip")
            TripPicker(state) { id -> viewModel.update { it.copy(tripId = id) } }

            SectionLabel("Notes")
            OutlinedTextField(
                value = state.notes,
                onValueChange = { v -> viewModel.update { it.copy(notes = v) } },
                modifier = Modifier.fillMaxWidth().height(120.dp),
                label = { Text("Notes") },
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun DateTimeRow(
    epoch: Long,
    onPickDateTime: (Long) -> Unit,
    context: android.content.Context,
) {
    val cal = remember(epoch) { Calendar.getInstance().apply { timeInMillis = epoch } }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("Date / time", style = MaterialTheme.typography.labelSmall)
                Text(Format.dateTime(epoch), style = MaterialTheme.typography.titleMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = {
                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                cal.set(y, m, d)
                                onPickDateTime(cal.timeInMillis)
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH),
                        ).show()
                    },
                    label = { Text("Date") },
                )
                AssistChip(
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, h, m ->
                                cal.set(Calendar.HOUR_OF_DAY, h)
                                cal.set(Calendar.MINUTE, m)
                                cal.set(Calendar.SECOND, 0)
                                onPickDateTime(cal.timeInMillis)
                            },
                            cal.get(Calendar.HOUR_OF_DAY),
                            cal.get(Calendar.MINUTE),
                            true,
                        ).show()
                    },
                    label = { Text("Time") },
                )
            }
        }
    }
}

@Composable
private fun ChargingTypeRow(current: ChargingType, onPick: (ChargingType) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChargingType.entries.forEach { t ->
            FilterChip(
                selected = t == current,
                onClick = { onPick(t) },
                label = { Text(t.displayName()) },
            )
        }
    }
}

@Composable
private fun PricingModelRow(current: PricingModel, onPick: (PricingModel) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PricingModel.entries.forEach { p ->
            FilterChip(
                selected = p == current,
                onClick = { onPick(p) },
                label = { Text(p.displayName()) },
            )
        }
    }
}

private fun ChargingType.displayName(): String = when (this) {
    ChargingType.DC_FAST -> "DC Fast"
    ChargingType.AC_L2 -> "AC L2"
    ChargingType.AC_L1 -> "AC L1"
}

private fun PricingModel.displayName(): String = when (this) {
    PricingModel.PER_KWH -> "$/kWh"
    PricingModel.PER_MINUTE -> "$/min"
    PricingModel.FLAT -> "Flat"
    PricingModel.FREE -> "Free"
    PricingModel.HYBRID -> "Hybrid"
}

@Composable
private fun BrandPicker(
    value: String,
    history: List<String>,
    onValue: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = remember(history) { (history + Brands.SUGGESTED).distinct() }
    val filtered = options.filter { value.isBlank() || it.contains(value, ignoreCase = true) }.take(12)

    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValue(it)
                expanded = true
            },
            label = { Text("Station brand") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Toggle brand suggestions",
                    )
                }
            },
        )
        DropdownMenu(
            expanded = expanded && filtered.isNotEmpty(),
            onDismissRequest = { expanded = false },
        ) {
            filtered.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt) },
                    onClick = {
                        onValue(opt)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripPicker(state: SessionEditUi, onPick: (Long?) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AssistChip(
            onClick = { onPick(null) },
            label = { Text("None") },
            colors = if (state.tripId == null) AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) else AssistChipDefaults.assistChipColors(),
        )
        state.trips.forEach { trip ->
            AssistChip(
                onClick = { onPick(trip.id) },
                label = { Text(trip.name) },
                colors = if (state.tripId == trip.id) AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ) else AssistChipDefaults.assistChipColors(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VehiclePicker(state: SessionEditUi, onPick: (Long?) -> Unit) {
    if (state.vehicles.isEmpty()) {
        Text(
            "Add a vehicle from Settings to tag sessions by car.",
            style = MaterialTheme.typography.bodySmall,
        )
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AssistChip(
            onClick = { onPick(null) },
            label = { Text("Unassigned") },
            colors = if (state.vehicleId == null) AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) else AssistChipDefaults.assistChipColors(),
        )
        state.vehicles.forEach { vehicle ->
            AssistChip(
                onClick = { onPick(vehicle.id) },
                label = { Text(vehicle.name) },
                colors = if (state.vehicleId == vehicle.id) AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ) else AssistChipDefaults.assistChipColors(),
            )
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onValue: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = modifier,
    )
}

@Composable
private fun TextFieldPlain(
    label: String,
    value: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onValue: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier,
    )
}
