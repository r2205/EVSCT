package com.evsct.app.ui.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.evsct.app.data.entity.Trip

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
    var name by remember { mutableStateOf(trip?.name.orEmpty()) }
    var startKm by remember { mutableStateOf(trip?.startOdometerKm?.toString().orEmpty()) }
    var endKm by remember { mutableStateOf(trip?.endOdometerKm?.toString().orEmpty()) }
    var notes by remember { mutableStateOf(trip?.notes.orEmpty()) }

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
                        value = startKm,
                        onValueChange = { startKm = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("Start km") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = endKm,
                        onValueChange = { endKm = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("End km") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    val merged = (trip ?: Trip(name = name.trim())).copy(
                        name = name.trim(),
                        startOdometerKm = startKm.toDoubleOrNull(),
                        endOdometerKm = endKm.toDoubleOrNull(),
                        notes = notes.trim().takeIf { it.isNotEmpty() },
                    )
                    onSave(merged)
                },
            ) { Text(if (trip == null) "Create" else "Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
