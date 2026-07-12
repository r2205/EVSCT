package com.evsct.app.ui.trips

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.evsct.app.data.entity.Trip
import com.evsct.app.ui.LocalUserUnits
import com.evsct.app.ui.map.TripPinColor
import com.evsct.app.util.Format
import com.evsct.app.util.Units
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Shared dialog for creating or editing a trip. When [trip] is null the
 * dialog acts as create; otherwise edit.
 */
@Composable
fun TripEditDialog(
    trip: Trip?,
    onDismiss: () -> Unit,
    onSave: (Trip) -> Unit,
) {
    val units = LocalUserUnits.current
    val unitLabel = Units.distanceUnit(units.useMiles)

    // Locale.US pins the decimal separator to '.' — the default locale would
    // seed "1234,5" on comma-decimal devices, which the old dot-only parse
    // rejected, silently nulling both odometers on any save (even a rename).
    fun displayText(km: Double?): String = km?.let {
        val display = Units.kmToDisplay(it, units.useMiles)
        if (display % 1.0 == 0.0) display.toLong().toString()
        else "%.1f".format(Locale.US, display)
    }.orEmpty()

    // Saveable: a configuration change (rotation, dark-mode toggle,
    // split-screen resize) recreates the activity, and plain remember would
    // reset every field to the stored trip's values — silently discarding
    // whatever was typed. The host's open-flag is saveable too, so the
    // dialog itself survives to restore these.
    var name by rememberSaveable { mutableStateOf(trip?.name.orEmpty()) }
    val initialStartText = remember { displayText(trip?.startOdometerKm) }
    val initialEndText = remember { displayText(trip?.endOdometerKm) }
    var startText by rememberSaveable { mutableStateOf(initialStartText) }
    var endText by rememberSaveable { mutableStateOf(initialEndText) }
    var startBattText by rememberSaveable { mutableStateOf(trip?.startBatteryPct?.toString().orEmpty()) }
    var endBattText by rememberSaveable { mutableStateOf(trip?.endBatteryPct?.toString().orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(trip?.notes.orEmpty()) }
    var pinColorKey by rememberSaveable { mutableStateOf(trip?.pinColor) }
    var showColorPicker by rememberSaveable { mutableStateOf(false) }
    // -1 sentinel keeps these Bundle-friendly Longs (matching the app's
    // other saveable id/date fields) instead of nullable boxing.
    var startDateMillis by rememberSaveable { mutableStateOf(trip?.startDate ?: NO_TRIP_DATE) }
    var endDateMillis by rememberSaveable { mutableStateOf(trip?.endDate ?: NO_TRIP_DATE) }
    var pickingStartDate by rememberSaveable { mutableStateOf(false) }
    var pickingEndDate by rememberSaveable { mutableStateOf(false) }

    val resolvedColor = TripPinColor.fromKey(pinColorKey)
    val startDate = startDateMillis.takeIf { it > 0 }
    val endDate = endDateMillis.takeIf { it > 0 }
    // Same shape as the odometer check: block the save only when both are
    // filled and ordered the wrong way around.
    val dateError = startDate != null && endDate != null && startDate > endDate

    // Untouched fields keep the stored km verbatim — the display↔km round-trip
    // is lossy in miles mode and would drift the stored value on every no-op
    // save. Computed here (not just in onClick) so the same values feed the
    // ordering check below.
    val startKm = if (startText == initialStartText) trip?.startOdometerKm
    else Format.parseDecimal(startText)?.let { Units.displayToKm(it, units.useMiles) }
    val endKm = if (endText == initialEndText) trip?.endOdometerKm
    else Format.parseDecimal(endText)?.let { Units.displayToKm(it, units.useMiles) }

    // Distance is End − Start, so Start cannot be more than End. Block the save
    // when both are filled but ordered the wrong way (which would yield a
    // negative distance).
    val odometerError = startKm != null && endKm != null && startKm > endKm

    // Input is digit-filtered, so the only invalid shape is out-of-range.
    val startBatt = startBattText.toIntOrNull()
    val endBatt = endBattText.toIntOrNull()
    val batteryError = (startBatt != null && startBatt !in 0..100) ||
        (endBatt != null && endBatt !in 0..100)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (trip == null) "New trip" else "Edit trip") },
        text = {
            Column(
                // Scrollable: seven controls plus helper/error rows exceed
                // the dialog's height on short screens and in landscape,
                // clipping the lower fields.
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Optional: dates label the trip and sort the list (newest first).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { pickingStartDate = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            startDate?.let { Format.date(it) } ?: "Start date…",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    OutlinedButton(
                        onClick = { pickingEndDate = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            endDate?.let { Format.date(it) } ?: "End date…",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (dateError) {
                    Text(
                        "Start date cannot be after end date.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (startDate != null || endDate != null) {
                    TextButton(
                        onClick = {
                            startDateMillis = NO_TRIP_DATE
                            endDateMillis = NO_TRIP_DATE
                        },
                    ) { Text("Clear dates") }
                }
                Text(
                    "Optional: distance is computed as End − Start when both are filled.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = startText,
                        onValueChange = { startText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                        label = { Text("Start $unitLabel") },
                        singleLine = true,
                        isError = odometerError,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = endText,
                        onValueChange = { endText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                        label = { Text("End $unitLabel") },
                        singleLine = true,
                        isError = odometerError,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }
                if (odometerError) {
                    Text(
                        "Start $unitLabel cannot be more than End $unitLabel.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    "Optional: battery % when the trip began and ended. With the " +
                        "odometer readings these measure the drives to the first " +
                        "charge and home from the last one.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = startBattText,
                        onValueChange = { startBattText = it.filter(Char::isDigit).take(3) },
                        label = { Text("Start battery %") },
                        singleLine = true,
                        isError = startBatt != null && startBatt !in 0..100,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = endBattText,
                        onValueChange = { endBattText = it.filter(Char::isDigit).take(3) },
                        label = { Text("End battery %") },
                        singleLine = true,
                        isError = endBatt != null && endBatt !in 0..100,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                if (batteryError) {
                    Text(
                        "Battery % must be between 0 and 100.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedButton(
                    onClick = { showColorPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ColorSwatch(
                        color = resolvedColor?.swatch ?: MaterialTheme.colorScheme.outline,
                        size = 16.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Map pin color: ${resolvedColor?.displayName ?: "Auto"}",
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && !odometerError && !batteryError && !dateError,
                onClick = {
                    val merged = (trip ?: Trip(name = name.trim())).copy(
                        name = name.trim(),
                        startDate = startDate,
                        endDate = endDate,
                        startOdometerKm = startKm,
                        endOdometerKm = endKm,
                        startBatteryPct = startBatt,
                        endBatteryPct = endBatt,
                        notes = notes.trim().takeIf { it.isNotEmpty() },
                        pinColor = pinColorKey,
                    )
                    onSave(merged)
                },
            ) { Text(if (trip == null) "Create" else "Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )

    if (showColorPicker) {
        TripPinColorPicker(
            current = resolvedColor,
            onPick = { picked ->
                pinColorKey = picked?.name
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false },
        )
    }

    if (pickingStartDate) {
        TripDatePickerDialog(
            initial = startDate,
            onPick = { millis ->
                startDateMillis = millis
                pickingStartDate = false
            },
            onDismiss = { pickingStartDate = false },
        )
    }
    if (pickingEndDate) {
        TripDatePickerDialog(
            initial = endDate,
            onPick = { millis ->
                endDateMillis = millis
                pickingEndDate = false
            },
            onDismiss = { pickingEndDate = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripDatePickerDialog(
    initial: Long?,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial?.let(::localMidnightToPickerUtc),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = pickerState.selectedDateMillis != null,
                onClick = {
                    pickerState.selectedDateMillis?.let { onPick(pickerUtcToLocalMidnight(it)) }
                },
            ) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    ) {
        DatePicker(state = pickerState)
    }
}

/** Sentinel for "no date set" so the saveable state stays a plain Long. */
private const val NO_TRIP_DATE = -1L

/* Trip dates are stored as epoch millis at LOCAL midnight (session
 * timestamps are local instants, and TripAnchor compares the two), while
 * the M3 date picker speaks UTC-midnight millis. Convert by calendar
 * fields so the chosen day survives the round trip in any timezone —
 * naive passthrough would show/store the previous day west of UTC. */

private fun localMidnightToPickerUtc(localMillis: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = localMillis }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

private fun pickerUtcToLocalMidnight(pickerMillis: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = pickerMillis }
    return Calendar.getInstance().apply {
        clear()
        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

@Composable
private fun TripPinColorPicker(
    current: TripPinColor?,
    /** A picked palette color, or null for "Auto" (the app assigns the
     *  least-used color when the trip is saved). */
    onPick: (TripPinColor?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Map pin color") },
        text = {
            // Two rows of five swatches.
            val rows = TripPinColor.entries.chunked(5)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                rows.forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        row.forEach { c ->
                            val selected = c == current
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(c.swatch)
                                    .then(
                                        if (selected) Modifier.border(
                                            width = 3.dp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            shape = CircleShape,
                                        ) else Modifier
                                    )
                                    .clickable { onPick(c) }
                                    // The swatches are pure color — name them
                                    // for TalkBack, which otherwise announces
                                    // ten identical unlabeled buttons.
                                    .semantics {
                                        contentDescription = c.displayName
                                        this.selected = selected
                                    },
                                contentAlignment = Alignment.Center,
                            ) { }
                        }
                    }
                }
                TextButton(onClick = { onPick(null) }) {
                    Text("Auto — let the app pick the least-used color")
                }
                Text(
                    "Pins for this trip will use this color on the map.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun ColorSwatch(color: Color, size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
    )
}
