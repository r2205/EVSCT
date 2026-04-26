package com.evsct.app.ui.vehicles

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleEditScreen(
    onDone: () -> Unit,
    viewModel: VehicleEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let { viewModel.pickImage(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "New vehicle" else "Edit vehicle") },
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
            ProfileImage(
                imagePath = state.imagePath,
                onPick = {
                    pickImage.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onClear = { viewModel.clearImage() },
            )

            OutlinedTextField(
                value = state.name,
                onValueChange = { v -> viewModel.update { it.copy(name = v) } },
                label = { Text("Display name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                NumField(
                    label = "Year",
                    value = state.year,
                    modifier = Modifier.weight(1f),
                ) { v -> viewModel.update { it.copy(year = v) } }
                TextEntry(
                    label = "Make",
                    value = state.make,
                    modifier = Modifier.weight(2f),
                ) { v -> viewModel.update { it.copy(make = v) } }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextEntry(
                    label = "Model",
                    value = state.model,
                    modifier = Modifier.weight(2f),
                ) { v -> viewModel.update { it.copy(model = v) } }
                TextEntry(
                    label = "Trim",
                    value = state.trim,
                    modifier = Modifier.weight(1f),
                ) { v -> viewModel.update { it.copy(trim = v) } }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                NumField(
                    label = "Battery (kWh)",
                    value = state.batteryKwh,
                    modifier = Modifier.weight(1f),
                ) { v -> viewModel.update { it.copy(batteryKwh = v) } }
                NumField(
                    label = "Range (km)",
                    value = state.rangeKm,
                    modifier = Modifier.weight(1f),
                ) { v -> viewModel.update { it.copy(rangeKm = v) } }
            }

            TextEntry(
                label = "VIN (optional)",
                value = state.vin,
            ) { v -> viewModel.update { it.copy(vin = v) } }

            OutlinedTextField(
                value = state.notes,
                onValueChange = { v -> viewModel.update { it.copy(notes = v) } },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth().height(120.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Default vehicle", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Pre-selected when logging new sessions.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = state.isDefault,
                    onCheckedChange = { v -> viewModel.update { it.copy(isDefault = v) } },
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ProfileImage(
    imagePath: String?,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    val ctx = LocalContext.current
    val file = imagePath?.let { File(ctx.filesDir, it) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onPick),
                contentAlignment = Alignment.Center,
            ) {
                if (file != null && file.exists()) {
                    AsyncImage(
                        model = file,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(120.dp),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(64.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPick) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(if (imagePath == null) "Add photo" else "Change photo")
                }
                if (imagePath != null) {
                    OutlinedButton(onClick = onClear) { Text("Remove") }
                }
            }
        }
    }
}

@Composable
private fun NumField(
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
private fun TextEntry(
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
