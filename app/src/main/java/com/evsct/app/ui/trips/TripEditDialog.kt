package com.evsct.app.ui.trips

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.evsct.app.data.entity.Trip
import com.evsct.app.ui.LocalUserUnits
import com.evsct.app.ui.map.TripPinColor
import com.evsct.app.util.Format
import com.evsct.app.util.Units
import java.util.Locale

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

    var name by remember { mutableStateOf(trip?.name.orEmpty()) }
    val initialStartText = remember { displayText(trip?.startOdometerKm) }
    val initialEndText = remember { displayText(trip?.endOdometerKm) }
    var startText by remember { mutableStateOf(initialStartText) }
    var endText by remember { mutableStateOf(initialEndText) }
    var notes by remember { mutableStateOf(trip?.notes.orEmpty()) }
    var pinColorKey by remember { mutableStateOf(trip?.pinColor) }
    var showColorPicker by remember { mutableStateOf(false) }

    val resolvedColor = TripPinColor.fromKey(pinColorKey)

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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (trip == null) "New trip" else "Edit trip") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
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
                enabled = name.isNotBlank() && !odometerError,
                onClick = {
                    val merged = (trip ?: Trip(name = name.trim())).copy(
                        name = name.trim(),
                        startOdometerKm = startKm,
                        endOdometerKm = endKm,
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
                pinColorKey = picked.name
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false },
        )
    }
}

@Composable
private fun TripPinColorPicker(
    current: TripPinColor?,
    onPick: (TripPinColor) -> Unit,
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
                                    .clickable { onPick(c) },
                                contentAlignment = Alignment.Center,
                            ) { }
                        }
                    }
                }
                Text(
                    "Pins for this trip will use this color on the map.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
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
