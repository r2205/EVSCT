# EVSCT — session summary

A snapshot of every feature, decision, and gotcha from the build sessions
that produced this app. Captured before running `/compact`, so the durable
context is in the repo even if the conversation is summarized later.

## Overview

- **What it is**: a personal Android app for tracking EV charging
  sessions (DC fast on road trips, AC at home/hotels). Designed first for
  a Pixel 9 Pro XL.
- **Target user**: solo, local-only data, no backend, no accounts.
- **Origin**: based on a `DC Fast Charging.xlsx` log the user had been
  maintaining manually. The xlsx columns drove the initial data model.
  (The xlsx itself was later purged from git history — see "Repo
  conventions" below.)

## Stack

- Kotlin 2.1, Jetpack Compose, Material 3, Navigation Compose
- Room (SQLite) with hand-rolled migrations
- Hilt for DI
- Coil 2.7 for image loading
- Apache POI for one-shot legacy XLSX import
- DataStore Preferences for cross-screen prefs
- Google Maps SDK for Android + Maps Compose 4.4.1 for the map screen
- minSdk 30, targetSdk 35, AGP 8.13.2 (upgraded mid-project)

## Data model

`charging_sessions`, `trips`, `vehicles` Room entities. FKs:
`charging_sessions.tripId → trips.id` (ON DELETE SET NULL),
`charging_sessions.vehicleId → vehicles.id` (ON DELETE SET NULL).

### Schema migrations

- **v1 → v2**: added `vehicles` table and the `vehicleId` column on
  sessions. Initial migration tried to use `ALTER TABLE ADD COLUMN` for
  `vehicleId` but SQLite can't add a foreign-key constraint that way.
  Hardened to the standard "create new table with FKs, copy rows over,
  drop, rename" pattern.
- **v2 → v3**: added `startOdometerKm` and `endOdometerKm` on `trips`
  for free-home-charging trip distance.
- **v3 → v4**: added `receiptImagePath` on sessions (nullable TEXT).
- **v4 → v5**: added `latitude` / `longitude` columns on sessions for
  the map view (populated by GPS autofill going forward, and by a
  one-shot reverse-geocode backfill for older sessions on first map
  open).
- **v5 → v6**: added `pinColor` on trips (TEXT, nullable). Auto-assigned
  on insert from a fixed 10-color palette.

`fallbackToDestructiveMigration` is enabled as a last-resort safety net.

## Feature inventory (organized by area)

### Session entry form (`SessionEditScreen`)

- Date/time, charging type (DC Fast / AC L2 / AC L1), pricing model
  (per-kWh, per-min, flat, free, hybrid).
- **Order**: Odometer first, then Energy, Cost, Currency chips,
  Duration, Battery start/end. Posted rates section below.
- **Smart duration entry**: `25` → `0h 25m 0s`; `1:25` → `1h 25m 0s`
  (h:m, not m:s — more natural for charging); `0:11:00` exact. Phone-pad
  keyboard with an inline `:` button that inserts at cursor (uses
  `TextFieldValue` for proper caret control). Format swaps between
  pretty and editable on focus change.
- **Brand picker**: tap-to-open bottom sheet with curated NA networks
  + history-sorted brands; search filters both; "Use \"...\"" custom
  entry option appears for unrecognized typed text. DAO query groups by
  `TRIM(brand) COLLATE NOCASE` so `"Tesla "` and `"Tesla"` are one entry.
- **Use a recent stop…**: bottom-sheet pre-fill for brand/city/prov/
  address/station from past sessions, deduped by
  `(brand, address-or-station, city)` case-insensitive.
- **GPS autofill**: tap the tertiary-tinted card to request location
  permission; `LocationManager` one-shot with 15s timeout, last-known
  fast path, `Geocoder` reverse-lookup. Result reported via snackbar.
  Coordinates are now stored on the session for the map view.
- **Region normalization**: `Saskatchewan`, `saskatchewan`, `Quebec`,
  `Québec` all map to two-letter codes. All CA provinces and US
  states/DC. Diacritic-insensitive.
- **Vehicle picker**: horizontally scrolling chips, defaults to the
  user's default vehicle. New sessions started from a vehicle tab on
  the log pre-select that vehicle (passed via nav arg).
- **Trip picker**: AssistChip row with "None" + each trip.
- **Currency chips**: small CAD / USD chip row beside the cost field.
  New sessions seed from the user's default-currency preference.
  Existing sessions keep whatever they were saved with.
- **Odometer / range fields are unit-aware**: label flips to (km) or
  (mi) based on the user's pref; entered values are converted to
  canonical km on save and converted back on load.
- **Receipt attachment**: photo or PDF, picked from a small "Photo /
  PDF" bottom-sheet chooser. Photos use the Photo Picker; PDFs use
  `OpenDocument` filtered to `application/pdf`. Storage preserves the
  source extension (`.jpg` / `.pdf`).
  - Photos render inline at 180dp with **pinch-to-zoom** fullscreen
    (1×–6×, pan when zoomed, double-tap toggle 1×↔2.5×, single-tap
    dismiss only at 1×). Uses `detectTransformGestures` +
    `detectTapGestures` + `Modifier.graphicsLayer`.
  - PDFs render as a "PDF receipt — tap to open" tile and hand off to
    the system PDF viewer via `ACTION_VIEW` through a `FileProvider`
    pointed at `filesDir/receipts/`.
  - Remove button now opens a confirmation dialog (avoids accidental
    delete).
- **Validation hints**: amber "heads-up" card at the **top** of the
  form when:
  - Odometer < previous session for this vehicle
  - Effective $/kWh deviates >25% from posted
  - Effective $/min deviates >25% from posted
  - Avg power exceeds posted max kW by >5%
  - Battery end < battery start
  Each hint carries a `Set<HintField>`; the screen flattens to
  `warnedFields` and passes Material 3's `isError` to the offending
  text fields, turning their borders red.
- **Empty-odometer warning** on save: confirmation dialog if the field
  is blank.
- **Delete confirmation** dialog (red "Delete" button).

### Charging log (`SessionListScreen`)

- Cards with a 6dp colored leading bar by charging type (amber /
  blue / purple), brand, city, cost in primary green, eff. $/kWh
  pill, vehicle pill (only on All tab), trip pill, receipt icon if
  attached.
- **Vehicle tabs** — `ScrollableTabRow` with All + each vehicle.
  Hidden when ≤ 1 vehicle.
- **Search** (toggle via top-bar magnifying glass): matches brand,
  city, prov, address, station, stall, notes.
- **Filter sheet** (tune icon next to search): brand FilterChips +
  date range with presets (All time / This month / Last 3 mo. /
  Last year) + custom From/To date pickers.
- **Active-filter chips** below search; tap to remove individually,
  Clear All to wipe.
- **Multi-select**: long-press to enter selection mode, tap to toggle.
  Selection-mode top bar shows count + clear / select-all / assign
  trip. Bulk-assign trip uses a single SQL UPDATE.
- **System back** in selection mode → clears selection; with active
  filters → clears them.
- **Backup nudge banner** (tertiary-tone): respects the user's
  configurable threshold (default 30 days) and the master enable
  toggle. State stored in `AppPreferences` DataStore (`last_backup_at`
  + reminder prefs).
- Top bar: Search · Stats · Map · Trips · Settings.

### Map view (`MapScreen`)

- Google Maps SDK + Maps Compose. API key read from `local.properties`
  (gitignored) into a manifest placeholder, so the repo stays free of
  secrets. README documents the Cloud Console setup, including how to
  fingerprint the debug + release signing keys via the
  `:app:signingReport` Gradle task.
- **One pin per distinct stop** — sessions are grouped by
  `brand + (address || station) + city` (case-insensitive). Coordinates
  are averaged across the contributing sessions.
- **Trip-colored pins** — each trip carries a color from a fixed
  10-swatch palette (`TripPinColor` enum: Red, Orange, Yellow, Green,
  Cyan, Azure, Blue, Violet, Magenta, Rose). Pin coloring rules:
  - all visible visits to a stop share one trip → that trip's hue
  - all un-tripped → default red marker
  - mixed across multiple trips → neutral gray "shared" pin (custom
    bitmap) so the visual doesn't lie about which trip owns the stop
- **Filter sheet** (filter icon in top bar): "Color pins by trip"
  switch (off → all-red mode), plus a checkbox list of trips and an
  "Untripped sessions" row. Hiding one trip on a multi-trip stop
  re-colors that stop to the remaining trip rather than staying gray.
  "Show all" / "Reset" actions appear when applicable. A small dot on
  the filter icon indicates active filters.
- **First-open backfill**: when the screen opens, every distinct stop
  that has only a textual address is reverse-geocoded and the
  coordinates written back to all sessions sharing that address.
  Progress shows in a banner. Stops that fail to geocode are reported
  as "N unlocated" in the subtitle. One-time cost; subsequent opens
  are instant.
- Camera auto-frames around the visible pins on first arrival.
- **Gotcha** (`ba9030b`): `BitmapDescriptorFactory.fromBitmap()` and
  `defaultMarker(hue)` only work after the Maps SDK is initialized,
  which happens when `GoogleMap` composes. Building the shared-pin
  bitmap inside a top-level `remember { ... }` ran *too early* and
  crashed the screen on entry. Fix: build all `BitmapDescriptor`s
  inside `GoogleMap`'s content lambda.

### Vehicles

- **List**: tap a row → detail screen. Default vehicle gets a star.
- **Detail screen**: hero card with photo + label, Lifetime card
  (sessions, total cost, energy, distance, $/km or $/mi, $/kWh, avg
  power, top brand), highlight tiles (fastest charge, cheapest $/kWh,
  most expensive $/kWh, last charged), recent sessions list (last
  10) with tap-to-edit. Pencil icon in top bar opens edit screen.
- **Edit screen**: profile photo via PhotoPicker (copied to
  `filesDir/vehicles/<uuid>.jpg`), name, year/make/model/trim,
  battery kWh, range (km or mi based on pref), VIN, notes,
  Default-vehicle switch (mutually exclusive — `clearDefaultExcept`),
  delete with confirmation.

### Trips

- **List**: rows show name, sessions, total $, energy, distance
  ($/km or $/mi based on pref). + FAB opens shared edit dialog.
- **Detail screen**: stats card + sessions list, pencil in top bar
  for edit dialog.
- **Edit dialog** (`TripEditDialog`, used for both create and edit):
  Name, Start/End odometer (unit-aware label, converts to km on save),
  Notes. New "Map pin color" button opens a 5×2 swatch picker.
  When both start/end odo are filled, distance = `end − start`;
  otherwise distance falls back to spread of session odometer readings.
- **Auto-color**: new trips get the least-used palette color via
  round-robin when saved without an explicit pick.
- **Bug fixed** (`fcaaf51`): repository upserts were using
  `OnConflictStrategy.REPLACE` for both insert and update, which on
  edit deleted the old row and fired the FK `ON DELETE SET NULL`
  cascade — stripping every session of its trip/vehicle tag. Fixed
  by routing existing-row saves through `@Update`.

### Stats screen (`StatsScreen`)

- Headline card: sessions, total cost, total energy, avg eff. $/kWh,
  avg power.
- Cost-by-month and Energy-by-month — 12-month rolling horizontal
  bars, normalized to the largest bucket.
- Top brands by spend (top 8).
- Charging type split (DC/AC L2/AC L1) — stacked bar with %.
- Vehicle filter mirrors the log tabs.
- No charting library; pure Compose primitives (Box width-fraction).
- Aggregate amounts use the user's default-currency suffix even when
  underlying sessions mix currencies (documented; we never auto-FX).

### Backup & export

- **Full backup** (`Settings → Full backup`): single `.zip`
  containing `backup.json` + `vehicles/<uuid>.jpg` +
  `receipts/<uuid>.{jpg,pdf}`. Schema-versioned (currently **v4**;
  older v1–v3 still restore cleanly):
  - v2 added receipts
  - v3 added per-session lat/lng
  - v4 added per-trip pinColor
  Restore wipes the DB and reinstalls inside one Room transaction;
  foreign keys remapped to fresh primary keys via in-flight ID maps so
  backups from another phone work cleanly. Red "Erase and restore"
  confirmation gates the destructive path. After a successful export,
  `BackupReminderNotifier.cancel()` clears any pending reminder.
- **CSV export/import** (kept for spreadsheet analysis use case):
  flat session table with columns including `trip_name`,
  `vehicle_name`, `latitude`, `longitude`. Round-trips via `CsvIo`.
  Trips and vehicles are recreated by name on import.
- **Legacy XLSX importer**: one-shot for the original
  `DC Fast Charging.xlsx`. Auto-tags imported rows with the user's
  default vehicle (if one exists at import time). Apache POI parses
  Excel duration cells (fraction-of-a-day) and date cells.

### Backup reminder

- Local-only reminder gated by user prefs:
  - **Master switch** (default on)
  - **Threshold days** (1–365, default 30)
  - **Also send Android notification** switch (default off; requests
    `POST_NOTIFICATIONS` on Android 13+ and flips itself off if
    permission is later revoked)
- In-app banner on the session list when threshold is exceeded (or
  ≥5 sessions and never backed up).
- `BackupReminderNotifier` posts/cancels a notification on a
  `backup_reminder` channel. Refreshed in `MainActivity.onResume`;
  cleared automatically after a successful backup.

### Theme

- Hand-tuned Material 3 palette (light + dark): deep emerald
  primary, sage secondary, blue-grey tertiary, with all container/
  on-container slots filled. Material You **disabled by default**
  so the look stays consistent regardless of wallpaper.
- Charging-type accents: amber (DC fast), blue (AC L2), purple
  (AC L1) used for row stripes, type badges, and the stats stacked bar.

## Settings screen

Cards (top to bottom):
1. **Vehicles** — entry to the vehicle list.
2. **Units & currency** — Distance segmented switch (Kilometres /
   Miles), Default currency segmented switch (CAD / USD). Existing
   sessions keep their stored currency; default seeds new sessions and
   tags aggregate totals.
3. **Full backup** — Export / Restore.
4. **Backup reminder** — enable, threshold days field, Android
   notification toggle.
5. **Backup (CSV)** — Export to CSV.
6. **Import (CSV)** — Import CSV with replace-existing toggle.
7. **One-time XLSX import** — for the legacy log.

Wrapped in `verticalScroll` so the bottom card never falls off (a
bug we hit early when the list was too tall for some screens).

## App-wide preferences

`AppPreferences` (DataStore) exposes:
- `lastBackupAt: Long?`
- `reminderSettings: Flow<BackupReminderSettings>` (enabled, threshold
  days, notify enabled)
- `userUnits: Flow<UserUnits>` (useMiles, defaultCurrency)
- `snapshot()` for one-shot reads (used by `BackupReminderNotifier`)

Units/currency are surfaced via a CompositionLocal
(`LocalUserUnits`) hosted by a tiny `UserPrefsViewModel` mounted at
`MainActivity`, so any composable can read them without ViewModel
plumbing. `Format` helpers (`distance`, `moneyRatePerDistance`) take
the unit pref as a parameter; aggregates pass the default-currency to
`Format.money`.

## Notable design decisions

- **Local-only**: no cloud, no accounts. Backups go through the user's
  storage of choice (Drive, local, etc.) via SAF.
- **Single-vehicle setup is uncluttered**: vehicle tabs and the
  vehicle pill on rows only appear when there are ≥ 2 vehicles.
- **Replace-only restore**: full-backup restore wipes existing data.
  Merge mode was deferred unless requested.
- **Schema-versioned backups**: a `settings` block in `backup.json` is
  reserved for future user preferences without breaking format.
- **No charts library**: stats use plain Compose primitives. Lighter
  APK, fewer deps to maintain.
- **Region codes over full names**: matches the user's xlsx history
  (which used `SK`, `BC`, etc.). Display is consistent throughout.
- **Validation hints are advisory, never blocking**: amber card,
  red-bordered fields, but Save still works.
- **Currency is per-session, not converted**: each charging session
  stores the currency it was logged in. The default-currency pref
  only seeds new sessions and labels aggregate totals. We never
  auto-FX between CAD and USD because rates float and we'd have to
  pick a moment.
- **Distances are stored canonically in km**: km/mi pref is purely
  display + form input/output. Switching units never changes saved
  data.
- **PDF receipts hand off externally**: rather than building an
  in-app PDF renderer, PDFs open via `ACTION_VIEW` through a
  FileProvider. Photos keep the inline pinch-to-zoom dialog.
- **Trip pin colors are mandatory but auto-assigned**: every trip
  ends up with a color so the map is always informative, but the
  user can override.
- **Multi-trip stops show as gray, not the latest trip's color**:
  better than implicitly lying about ownership.

## Notable lessons learned

- Compose extension-property icons (`Icons.Default.Warning`,
  `CloudUpload`, `ArrowDropDown`, etc.) need explicit per-file
  imports even with `material-icons-extended` on the classpath.
  Tripped us several times.
- Several material icons are deprecated in favor of
  `Icons.AutoMirrored.Filled.*` for RTL support (`ArrowBack`, `Label`,
  `TrendingUp`, `TrendingDown`). Migrated all 11 call sites in commit
  `2b26a61`.
- Kotlin 2.1 doesn't allow `break`/`continue` inside inline lambdas
  (added in 2.2). The XLSX importer originally used `continue` inside
  `XSSFWorkbook(...).use { ... }` and had to be refactored into a
  pure `parseRow` helper.
- `OutlinedTextField` with a String value resets cursor to end on
  external value changes; need `TextFieldValue` to control caret
  for the duration field's `:` insert.
- **Maps SDK initialization timing**: `BitmapDescriptorFactory` only
  works after `GoogleMap` composes and initializes the SDK. Pre-build
  any custom marker bitmaps inside `GoogleMap`'s content lambda, not
  in a top-level `remember`.
- Compose's stub `LazyColumn` `combinedClickable` integration is
  fine; long-press selection works natively.

## Outstanding ideas (not yet built)

- Per-vehicle lifetime stats by month / year (rolling charts).
- Search through Stats / Trips, not just the log.
- Merge-mode restore (current is replace-only).
- "View on map" entry from the trip detail screen, filtered to that
  trip's pins (the data and the filter mechanism already exist; just
  needs a nav route + button).
- Reminders by location ("you're at a station you've been to;
  log a session?") — nice-to-have but adds geofencing complexity.

## Repo conventions

- Conventional, descriptive commit messages.
- All commits include the Claude Code session URL in the trailer.
- Branch: `claude/get-started-AEDLP`. `main` and the feature branch
  both live on `r2205/evsct` on GitHub.
- **History rewrite**: `DC Fast Charging.xlsx` was originally
  committed to `main` early in the project, but contained somewhat
  personal data. We later purged it from history with `git
  filter-repo --invert-paths`, force-pushed both branches, and reset
  local clones. The file no longer appears in any reachable commit
  on either branch. Full instructions live in this file's commit
  history if it ever needs doing again.
- API keys and other secrets live in `local.properties` (gitignored)
  and are pulled into manifest placeholders by `app/build.gradle.kts`.
  Currently used for `MAPS_API_KEY`.
