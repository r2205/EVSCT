package com.evsct.app.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.evsct.app.data.prefs.AppPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenVehicles: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var replaceOnImport by remember { mutableStateOf(false) }
    var showXlsxConfirm by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf<android.net.Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> uri?.let { viewModel.exportCsv(it) } }

    val csvImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.importCsv(it, replaceOnImport) } }

    val xlsxImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.importXlsx(it) } }

    val backupExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> uri?.let { viewModel.exportBackup(it) } }

    val backupImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { showRestoreConfirm = it } }

    val context = LocalContext.current

    // The share path: VM builds the file in cacheDir and posts it here; we
    // wrap it with FileProvider, fire ACTION_SEND through a chooser (Drive,
    // email, Messages, etc.), then immediately consume the pending state so
    // the launch is one-shot. MIME type and chooser title are derived from
    // the file extension so the same effect handles both the full-backup zip
    // and the CSV export.
    LaunchedEffect(state.pendingShareFile) {
        val file = state.pendingShareFile ?: return@LaunchedEffect
        val authority = "${context.packageName}.fileprovider"
        val contentUri = FileProvider.getUriForFile(context, authority, file)
        val isCsv = file.name.endsWith(".csv", ignoreCase = true)
        val mimeType = if (isCsv) "text/csv" else "application/zip"
        val chooserTitle = if (isCsv) "Share CSV" else "Share backup"
        // Many share targets (Drive especially) treat EXTRA_SUBJECT/TITLE as
        // the saved filename and ignore the FileProvider's display name —
        // pass the actual filename (with extension + timestamp) on both
        // extras so it round-trips with the same naming convention as Save.
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            putExtra(Intent.EXTRA_TITLE, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, chooserTitle))
        viewModel.consumePendingShare()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenVehicles),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.DirectionsCar,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.size(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Vehicles",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Manage your EVs and choose a default for new sessions.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }

            UnitsCurrencyCard(
                useMiles = state.units.useMiles,
                defaultCurrency = state.units.defaultCurrency,
                onUseMilesChange = viewModel::setUseMiles,
                onDefaultCurrencyChange = viewModel::setDefaultCurrency,
            )

            ThemeCard(
                themeMode = state.themeMode,
                onThemeModeChange = viewModel::setThemeMode,
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Full backup",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Save or restore everything — sessions, trips, vehicles, and " +
                            "vehicle photos — as a single .zip. Save picks a folder on " +
                            "this device; Share sends the file out via Drive, email, or " +
                            "any other app you have installed.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(
                        onClick = {
                            val ts = java.text.SimpleDateFormat(
                                "yyyy-MM-dd-HHmm",
                                java.util.Locale.US,
                            ).format(java.util.Date())
                            backupExportLauncher.launch("evsct-backup-$ts.zip")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.busy,
                    ) { Text("Save backup file…") }
                    OutlinedButton(
                        onClick = { viewModel.shareBackup() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.busy,
                    ) { Text("Share backup file…") }
                    OutlinedButton(
                        onClick = {
                            backupImportLauncher.launch(
                                arrayOf(
                                    "application/zip",
                                    "application/octet-stream",
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.busy,
                    ) { Text("Restore from backup…") }
                }
            }

            BackupReminderCard(
                reminder = state.reminder,
                onToggleEnabled = viewModel::setReminderEnabled,
                onChangeThreshold = viewModel::setReminderThresholdDays,
                onToggleNotify = viewModel::setReminderNotifyEnabled,
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Backup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Export every session to a CSV file you can open in Excel or Google " +
                            "Sheets. Save picks a folder on this device; Share sends the file " +
                            "out via Drive, email, or any other app you have installed.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(
                        onClick = {
                            val name = "evsct-export-${System.currentTimeMillis()}.csv"
                            exportLauncher.launch(name)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.busy,
                    ) { Text("Export to CSV…") }
                    OutlinedButton(
                        onClick = { viewModel.shareCsv() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.busy,
                    ) { Text("Share CSV file…") }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Import", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Import sessions from a previously exported CSV.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = replaceOnImport,
                            onCheckedChange = { replaceOnImport = it },
                        )
                        Text("Replace existing sessions before import")
                    }
                    OutlinedButton(
                        onClick = { csvImportLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/csv")) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.busy,
                    ) { Text("Import CSV…") }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("One-time XLSX import", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Import the legacy \"DC Fast Charging.xlsx\" sheet you've been keeping. " +
                            "This is intended to be run once when first setting up the app.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(
                        onClick = { showXlsxConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.busy,
                    ) { Text("Import legacy XLSX…") }
                }
            }
        }
    }

    if (showXlsxConfirm) {
        AlertDialog(
            onDismissRequest = { showXlsxConfirm = false },
            title = { Text("Import legacy XLSX?") },
            text = {
                Text(
                    "This will append every row from the chosen .xlsx into your sessions. " +
                        "Run it only once to avoid duplicates."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showXlsxConfirm = false
                    xlsxImportLauncher.launch(arrayOf(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "application/octet-stream",
                    ))
                }) { Text("Pick file") }
            },
            dismissButton = {
                TextButton(onClick = { showXlsxConfirm = false }) { Text("Cancel") }
            },
        )
    }

    showRestoreConfirm?.let { uri ->
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = null },
            title = { Text("Restore from backup?") },
            text = {
                Text(
                    "This will erase every session, trip, and vehicle currently in " +
                        "the app and replace them with the contents of the backup file. " +
                        "This can't be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRestoreConfirm = null
                    viewModel.restoreBackup(uri)
                }) {
                    Text("Erase and restore", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = null }) { Text("Cancel") }
            },
        )
    }

    state.message?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewModel.clearMessage() },
            title = { Text("Result") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { viewModel.clearMessage() }) { Text("OK") } },
        )
    }
}

@Composable
private fun BackupReminderCard(
    reminder: com.evsct.app.data.prefs.BackupReminderSettings,
    onToggleEnabled: (Boolean) -> Unit,
    onChangeThreshold: (Long) -> Unit,
    onToggleNotify: (Boolean) -> Unit,
) {
    val context = LocalContext.current

    // Track the field text separately so the user can clear / retype freely
    // without us thrashing the saved value on every keystroke.
    var thresholdText by remember(reminder.thresholdDays) {
        mutableStateOf(reminder.thresholdDays.toString())
    }

    var pendingNotifyToggle by remember { mutableStateOf(false) }

    val notifyPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (pendingNotifyToggle && granted) onToggleNotify(true)
        pendingNotifyToggle = false
    }

    fun requestNotifyOn() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                onToggleNotify(true)
            } else {
                pendingNotifyToggle = true
                notifyPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            onToggleNotify(true)
        }
    }

    // If the user revokes the OS permission outside the app and comes back,
    // turn the in-app notify toggle off so we don't lie about being on.
    LaunchedEffect(reminder.notifyEnabled) {
        if (reminder.notifyEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) onToggleNotify(false)
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Backup reminder",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Nudge me when it's been a while since my last full backup.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = reminder.enabled,
                    onCheckedChange = onToggleEnabled,
                )
            }

            OutlinedTextField(
                value = thresholdText,
                onValueChange = { input ->
                    val digits = input.filter { it.isDigit() }.take(3)
                    thresholdText = digits
                    digits.toLongOrNull()?.let { days ->
                        if (days in AppPreferences.MIN_THRESHOLD_DAYS..AppPreferences.MAX_THRESHOLD_DAYS &&
                            days != reminder.thresholdDays
                        ) {
                            onChangeThreshold(days)
                        }
                    }
                },
                label = { Text("Remind me after (days)") },
                supportingText = {
                    Text(
                        "Between ${AppPreferences.MIN_THRESHOLD_DAYS} and " +
                            "${AppPreferences.MAX_THRESHOLD_DAYS} days. Default: " +
                            "${AppPreferences.DEFAULT_THRESHOLD_DAYS}.",
                    )
                },
                singleLine = true,
                enabled = reminder.enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Also send Android notification",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "Show the reminder in the notification shade, not just inside the app.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = reminder.notifyEnabled,
                    enabled = reminder.enabled,
                    onCheckedChange = { wantOn ->
                        if (wantOn) requestNotifyOn() else onToggleNotify(false)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitsCurrencyCard(
    useMiles: Boolean,
    defaultCurrency: String,
    onUseMilesChange: (Boolean) -> Unit,
    onDefaultCurrencyChange: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "Units & currency",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Display preferences. Existing data is stored once and " +
                            "shown in your chosen units.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Text("Distance", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !useMiles,
                    onClick = { onUseMilesChange(false) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("Kilometres") }
                SegmentedButton(
                    selected = useMiles,
                    onClick = { onUseMilesChange(true) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("Miles") }
            }

            Text("Default currency", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                AppPreferences.SUPPORTED_CURRENCIES.forEachIndexed { index, code ->
                    SegmentedButton(
                        selected = defaultCurrency == code,
                        onClick = { onDefaultCurrencyChange(code) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = AppPreferences.SUPPORTED_CURRENCIES.size,
                        ),
                    ) { Text(code) }
                }
            }
            Text(
                "Each session keeps the currency it was saved with. The default " +
                    "is used for new sessions and for totals on the dashboard.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeCard(
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
) {
    val options = listOf(
        "SYSTEM" to "System",
        "LIGHT" to "Light",
        "DARK" to "Dark",
    )
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Brightness6,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "Theme",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "System follows your phone's dark-mode setting; " +
                            "Light and Dark force the corresponding palette.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, (key, label) ->
                    SegmentedButton(
                        selected = themeMode == key,
                        onClick = { onThemeModeChange(key) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    ) { Text(label) }
                }
            }
        }
    }
}
