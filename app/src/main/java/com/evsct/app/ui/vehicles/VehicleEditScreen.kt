package com.evsct.app.ui.vehicles

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.evsct.app.ui.readableFormWidth
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleEditScreen(
    onDone: () -> Unit,
    viewModel: VehicleEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Re-arm the ViewModel's exit latch whenever this nav entry reaches
    // RESUMED (LocalLifecycleOwner inside a NavHost is the back-stack
    // entry). A screen whose post-save pop actually ran never resumes
    // again — so resuming with the latch set means ifResumed dropped the
    // pop mid-transition, and without the reset Save/Delete would stay
    // dead on a screen that's still visible.
    LifecycleResumeEffect(Unit) {
        viewModel.onScreenResumed()
        onPauseOrDispose { }
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let { viewModel.pickImage(it) } }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(state.transientMessage) {
        state.transientMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearTransientMessage()
        }
    }

    val dirty = viewModel.isDirty(state)
    fun requestExit() {
        if (dirty) showDiscardConfirm = true else onDone()
    }
    // System back gets the same guard as the toolbar arrow. Enabled only
    // while dirty so a clean form keeps the default (unintercepted) pop.
    BackHandler(enabled = dirty) { showDiscardConfirm = true }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            // Until the load lands, isNew defaults true even
                            // for an existing vehicle — don't claim either.
                            state.isLoading -> ""
                            state.isNew -> "New vehicle"
                            else -> "Edit vehicle"
                        },
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                navigationIcon = {
                    IconButton(onClick = { requestExit() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                    // Saving before the load lands would insert a blank NEW
                    // row (isNew still defaults true), not update the one
                    // being opened.
                    IconButton(onClick = { viewModel.save(onDone) }, enabled = !state.isLoading) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            // Fields aren't seeded yet — rendering the form now shows a
            // blank shell whose values pop in a beat later.
            Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxHeight()
                // Capped and centred in wide windows; identical to the old
                // fillMaxSize on a portrait phone. See ui/AdaptiveLayout.kt.
                .readableFormWidth()
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

            val focusManager = LocalFocusManager.current
            OutlinedTextField(
                value = state.name,
                onValueChange = { v -> viewModel.update { it.copy(name = v) } },
                label = { Text("Display name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Next) },
                ),
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
                    label = "Range (${com.evsct.app.util.Units.distanceUnit(state.useMiles)})",
                    value = state.rangeText,
                    modifier = Modifier.weight(1f),
                ) { v -> viewModel.update { it.copy(rangeText = v) } }
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
                // One merged toggle target: the whole row flips the switch
                // and TalkBack reads "Default vehicle, switch, on" instead
                // of an unlabeled switch after two loose text nodes.
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .toggleable(
                        value = state.isDefault,
                        role = Role.Switch,
                        onValueChange = { v -> viewModel.update { it.copy(isDefault = v) } },
                    ),
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
                    onCheckedChange = null,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDiscardConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("Discard changes?") },
            text = { Text("This vehicle has unsaved edits. Leaving now throws them away.") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showDiscardConfirm = false
                        onDone()
                    },
                ) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDiscardConfirm = false }) {
                    Text("Keep editing")
                }
            },
        )
    }

    if (showDeleteConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text("Delete this vehicle?") },
            text = {
                Text(
                    "Sessions previously tagged with this vehicle will keep their data " +
                        "but be unassigned."
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        showDeleteConfirm = false
                        viewModel.deleteAndExit(onDone)
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
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
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Next,
        ),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Next) },
        ),
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
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Next) },
        ),
        singleLine = true,
        modifier = modifier,
    )
}
