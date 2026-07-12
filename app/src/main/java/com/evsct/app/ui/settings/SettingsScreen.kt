package com.evsct.app.ui.settings

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.evsct.app.BuildConfig
import com.evsct.app.data.backup.BackupShareChosenReceiver
import com.evsct.app.data.prefs.AppPreferences
import com.evsct.app.data.prefs.CardTimeRate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenVehicles: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Saveable: the SAF picker sits over this screen and low-RAM devices
    // kill the process behind it; the redelivered activity result would
    // otherwise see the flag reset to false and quietly downgrade a
    // replace-import into an append that duplicates the whole log.
    var replaceOnImport by rememberSaveable { mutableStateOf(false) }
    var showXlsxConfirm by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf<android.net.Uri?>(null) }
    var showUndoRestoreConfirm by remember { mutableStateOf(false) }
    var showCsvReplaceConfirm by remember { mutableStateOf<android.net.Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> uri?.let { viewModel.exportCsv(it) } }

    val csvImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let {
            // Replace-mode wipes the whole log — confirm like Restore does.
            // Append-mode stays a one-step flow.
            if (replaceOnImport) showCsvReplaceConfirm = it
            else viewModel.importCsv(it, replaceExisting = false)
        }
    }

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
        val chooser = if (isCsv) {
            Intent.createChooser(send, chooserTitle)
        } else {
            // Backup shares defer the "last backed up" record to the
            // moment the user actually picks a target: the system fires
            // this IntentSender on selection and never on cancel, so a
            // dismissed sheet no longer silences the backup reminder.
            // FLAG_MUTABLE because the system appends the chosen
            // component to the fired intent.
            val chosenCallback = PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, BackupShareChosenReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            Intent.createChooser(send, chooserTitle, chosenCallback.intentSender)
        }
        context.startActivity(chooser)
        viewModel.consumePendingShare()
    }

    val snackbarHostState = remember { SnackbarHostState() }
    // Routine successes go to a snackbar; failures and destructive-replace
    // results render as the titled dialog below.
    LaunchedEffect(state.feedback) {
        val fb = state.feedback
        if (fb != null && !fb.asDialog) {
            snackbarHostState.showSnackbar(fb.body)
            viewModel.clearFeedback()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                cardTimeRate = state.units.cardTimeRate,
                onUseMilesChange = viewModel::setUseMiles,
                onDefaultCurrencyChange = viewModel::setDefaultCurrency,
                onCardTimeRateChange = viewModel::setCardTimeRate,
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
                            "any other app you have installed. Android's automatic " +
                            "backup doesn't include photos or receipts, so this is the " +
                            "only complete copy.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    // Updates when a Save completes, a share target is
                    // picked (not on a cancelled share sheet), or a
                    // restore succeeds.
                    Text(
                        "Last backed up: " + (state.lastBackupAt?.let {
                            java.text.DateFormat.getDateTimeInstance(
                                java.text.DateFormat.MEDIUM,
                                java.text.DateFormat.SHORT,
                            ).format(java.util.Date(it))
                        } ?: "never"),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
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
                    ) {
                        BusyButtonContent("Save backup file…", state.busyOp == SettingsOp.EXPORT_BACKUP)
                    }
                    OutlinedButton(
                        onClick = { viewModel.shareBackup() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.busy,
                    ) {
                        BusyButtonContent("Share backup file…", state.busyOp == SettingsOp.SHARE_BACKUP)
                    }
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
                    ) {
                        BusyButtonContent("Restore from backup…", state.busyOp == SettingsOp.RESTORE)
                    }
                    state.preRestoreSnapshotAt?.let { snapAt ->
                        OutlinedButton(
                            onClick = { showUndoRestoreConfirm = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.busy,
                        ) {
                            BusyButtonContent(
                                "Undo last restore or import…",
                                state.busyOp == SettingsOp.UNDO_RESTORE,
                            )
                        }
                        Text(
                            "Brings back the data as it was just before your last " +
                                "restore or replace-import (snapshot taken automatically " +
                                java.text.DateFormat.getDateTimeInstance(
                                    java.text.DateFormat.MEDIUM,
                                    java.text.DateFormat.SHORT,
                                ).format(java.util.Date(snapAt)) + ").",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
                    ) {
                        BusyButtonContent("Export to CSV…", state.busyOp == SettingsOp.EXPORT_CSV)
                    }
                    OutlinedButton(
                        onClick = { viewModel.shareCsv() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.busy,
                    ) {
                        BusyButtonContent("Share CSV file…", state.busyOp == SettingsOp.SHARE_CSV)
                    }
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
                    ) {
                        BusyButtonContent("Import CSV…", state.busyOp == SettingsOp.IMPORT_CSV)
                    }
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
                    ) {
                        BusyButtonContent("Import legacy XLSX…", state.busyOp == SettingsOp.IMPORT_XLSX)
                    }
                }
            }

            AboutCard(context = context)
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

    showCsvReplaceConfirm?.let { uri ->
        AlertDialog(
            onDismissRequest = { showCsvReplaceConfirm = null },
            title = { Text("Replace all sessions?") },
            text = {
                Text(
                    "\"Replace existing\" is ticked: this will erase every " +
                        "session currently in the app and replace them with " +
                        "the contents of the CSV. A snapshot of your current " +
                        "data is saved automatically first, so you can undo " +
                        "this from Settings if it's the wrong file."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showCsvReplaceConfirm = null
                    viewModel.importCsv(uri, replaceExisting = true)
                }) {
                    Text("Erase and import", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCsvReplaceConfirm = null }) { Text("Cancel") }
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
                        "A snapshot of your current data is saved automatically first, " +
                        "so you can undo this from Settings if it's the wrong file."
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

    if (showUndoRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showUndoRestoreConfirm = false },
            title = { Text("Undo last restore or import?") },
            text = {
                Text(
                    "This replaces everything currently in the app with the " +
                        "snapshot taken just before your last restore or " +
                        "replace-import. The data you're replacing is " +
                        "snapshotted first, so you can undo the undo."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showUndoRestoreConfirm = false
                    viewModel.undoRestore()
                }) {
                    Text("Undo", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUndoRestoreConfirm = false }) { Text("Cancel") }
            },
        )
    }

    state.feedback?.let { fb ->
        if (fb.asDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.clearFeedback() },
                icon = if (fb.isError) {
                    {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                } else null,
                title = { Text(fb.title) },
                text = { Text(fb.body) },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearFeedback() }) { Text("OK") }
                },
            )
        }
    }
}

/** Button label that swaps in a leading spinner while its operation runs —
 *  progress lives ON the button the user tapped, not in a bar that may
 *  have scrolled off-screen. */
@Composable
private fun BusyButtonContent(text: String, busy: Boolean) {
    if (busy) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
        )
        Spacer(Modifier.size(8.dp))
    }
    Text(text)
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
    cardTimeRate: CardTimeRate,
    onUseMilesChange: (Boolean) -> Unit,
    onDefaultCurrencyChange: (String) -> Unit,
    onCardTimeRateChange: (CardTimeRate) -> Unit,
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

            Text("Time rate on cards", style = MaterialTheme.typography.labelLarge)
            val rateOptions = listOf(
                CardTimeRate.OFF to "Off",
                CardTimeRate.PER_MINUTE to "$/min",
                CardTimeRate.PER_HOUR to "$/hr",
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                rateOptions.forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        selected = cardTimeRate == mode,
                        onClick = { onCardTimeRateChange(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = rateOptions.size),
                    ) { Text(label) }
                }
            }
            Text(
                "Show each charge's cost per minute or hour on its card, " +
                    "computed from cost ÷ charging time.",
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

/**
 * Bottom-of-Settings card showing the running build's version + git commit.
 * The commit line is tappable when the build came from a clean working tree
 * — taps open the matching GitHub commit page. Dirty or unknown builds
 * render the same text in muted color and don't link anywhere, since the
 * URL wouldn't actually reflect what's on the phone.
 */
@Composable
private fun AboutCard(context: Context) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "About",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "EVSCT ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodyMedium,
            )
            val sha = BuildConfig.GIT_SHA
            val commitDate = formatCommitDate(BuildConfig.GIT_COMMIT_DATE)
            val canOpen = sha != "unknown" && !sha.endsWith("-dirty")
            val commitLine = buildString {
                append("Commit ")
                append(sha)
                if (commitDate.isNotEmpty()) {
                    append(" · ")
                    append(commitDate)
                }
            }
            if (canOpen) {
                Text(
                    commitLine,
                    modifier = Modifier.clickable {
                        val url = "https://github.com/r2205/EVSCT/commit/$sha"
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Text(
                    commitLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "View on GitHub",
                modifier = Modifier.clickable {
                    val url = "https://github.com/r2205/EVSCT"
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Privacy policy",
                modifier = Modifier.clickable {
                    val url = "https://r2205.github.io/EVSCT/privacy-policy.html"
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Render `git log %cI`-style ISO offset dates as e.g.
 *  "May 13, 2026 19:32 UTC", normalised to UTC so two devices in different
 *  timezones see the same string for the same commit. Falls back to the raw
 *  string when parsing fails so a future format quirk degrades gracefully
 *  instead of hiding the field. */
private fun formatCommitDate(raw: String): String =
    if (raw == "unknown" || raw.isBlank()) ""
    else runCatching {
        OffsetDateTime.parse(raw)
            .withOffsetSameInstant(ZoneOffset.UTC)
            .format(DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm 'UTC'"))
    }.getOrDefault(raw)
