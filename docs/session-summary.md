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

## Stack

- Kotlin 2.1, Jetpack Compose, Material 3, Navigation Compose
- Room (SQLite) with hand-rolled migrations
- Hilt for DI
- Coil 2.7 for image loading
- Apache POI for one-shot legacy XLSX import
- DataStore Preferences for the backup-nudge timestamp
- minSdk 30, targetSdk 35, AGP 8.7.3

## Data model

`charging_sessions`, `trips`, `vehicles` Room entities. FKs:
`charging_sessions.tripId → trips.id` (ON DELETE SET NULL),
`charging_sessions.vehicleId → vehicles.id` (ON DELETE SET NULL).

### Schema migrations

- **v1 → v2**: added `vehicles` table and the `vehicleId` column on
  sessions. Initial migration tried to use `ALTER TABLE ADD COLUMN` for
  `vehicleId` but SQLite can't add a foreign-key constraint that way.
  Hardened to the standard "create new table with FKs, copy rows over,
  drop, rename" pattern (commit `35873a7`).
- **v2 → v3**: added `startOdometerKm` and `endOdometerKm` on `trips`
  for free-home-charging trip distance.
- **v3 → v4**: added `receiptImagePath` on sessions (nullable TEXT).

`fallbackToDestructiveMigration` is enabled as a last-resort safety net.

## Feature inventory (organized by area)

### Session entry form (`SessionEditScreen`)

- Date/time, charging type (DC Fast / AC L2 / AC L1), pricing model
  (per-kWh, per-min, flat, free, hybrid).
- **Order**: Odometer first, then Energy, Cost, Duration, Battery
  start/end. Posted rates section below.
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
- **Region normalization**: `Saskatchewan`, `saskatchewan`, `Quebec`,
  `Québec` all map to two-letter codes. All CA provinces and US
  states/DC. Diacritic-insensitive.
- **Vehicle picker**: horizontally scrolling chips, defaults to the
  user's default vehicle. New sessions started from a vehicle tab on
  the log pre-select that vehicle (passed via nav arg).
- **Trip picker**: AssistChip row with "None" + each trip.
- **Receipt photo**: card with photo preview (180dp), Add/Change/Remove
  buttons, tap-to-fullscreen with **pinch-to-zoom** (1×–6×), pan when
  zoomed, double-tap toggle 1×↔2.5×, single-tap dismiss only at 1×.
  Uses `detectTransformGestures` + `detectTapGestures` +
  `Modifier.graphicsLayer`.
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
- **Backup nudge banner** (tertiary-tone): shown at the top of the
  list when total sessions ≥ 5 and never backed up, OR last backup
  > 30 days ago. "Open Settings" jumps to the Settings screen,
  "Not now" hides for the current process. State stored in
  `AppPreferences` DataStore key `last_backup_at`.

### Vehicles

- **List**: tap a row → detail screen. Default vehicle gets a star.
- **Detail screen**: hero card with photo + label, Lifetime card
  (sessions, total cost, energy, distance, $/km, $/kWh, avg power,
  top brand), highlight tiles (fastest charge, cheapest $/kWh,
  most expensive $/kWh, last charged), recent sessions list (last
  10) with tap-to-edit. Pencil icon in top bar opens edit screen.
- **Edit screen**: profile photo via PhotoPicker (copied to
  `filesDir/vehicles/<uuid>.jpg`), name, year/make/model/trim,
  battery kWh, range km, VIN, notes, Default-vehicle switch
  (mutually exclusive — `clearDefaultExcept`), delete with
  confirmation.

### Trips

- **List**: rows show name, sessions, total $, energy, distance,
  $/km. + FAB opens shared edit dialog.
- **Detail screen**: stats card + sessions list, pencil in top bar
  for edit dialog.
- **Edit dialog** (`TripEditDialog`, used for both create and edit):
  Name, Start km, End km, Notes. When both odo are filled, distance
  = `end − start`; otherwise distance falls back to spread of session
  odometer readings.
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

### Backup & export

- **Full backup** (`Settings → Full backup`): single `.zip`
  containing `backup.json` + `vehicles/<uuid>.jpg` + `receipts/<uuid>.jpg`.
  Schema-versioned (currently v2; v1 backups still restorable, just
  without receipts). Restore wipes the DB and reinstalls inside one
  Room transaction; foreign keys remapped to fresh primary keys via
  in-flight ID maps so backups from another phone work cleanly. Red
  "Erase and restore" confirmation gates the destructive path.
- **CSV export/import** (kept for spreadsheet analysis use case):
  flat session table with columns including `trip_name` and
  `vehicle_name`. Round-trips via `CsvIo`. Trips and vehicles are
  recreated by name on import.
- **Legacy XLSX importer**: one-shot for the original
  `DC Fast Charging.xlsx`. Auto-tags imported rows with the user's
  default vehicle (if one exists at import time). Apache POI parses
  Excel duration cells (fraction-of-a-day) and date cells.

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
2. **Full backup** — Export / Restore.
3. **Backup (CSV)** — Export to CSV.
4. **Import (CSV)** — Import CSV with replace-existing toggle.
5. **One-time XLSX import** — for the legacy log.

Wrapped in `verticalScroll` so the bottom card never falls off (a
bug we hit early when the list was too tall for some screens).

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

## Notable lessons learned

- Compose extension-property icons (`Icons.Default.Warning`,
  `CloudUpload`, `ArrowDropDown`, etc.) need explicit per-file
  imports even with `material-icons-extended` on the classpath.
  Tripped us several times.
- Kotlin 2.1 doesn't allow `break`/`continue` inside inline lambdas
  (added in 2.2). The XLSX importer originally used `continue` inside
  `XSSFWorkbook(...).use { ... }` and had to be refactored into a
  pure `parseRow` helper.
- `OutlinedTextField` with a String value resets cursor to end on
  external value changes; need `TextFieldValue` to control caret
  for the duration field's `:` insert.
- Compose's stub `LazyColumn` `combinedClickable` integration is
  fine; long-press selection works natively.

## Outstanding ideas (not yet built)

- Per-vehicle lifetime stats by month / year (rolling charts).
- Map view of charging locations.
- Search through Stats / Trips, not just the log.
- Currency / units (CAD↔USD, km↔mi) toggles in Settings — earlier
  the JSON `settings` block was reserved for this.
- Merge-mode restore (current is replace-only).
- Reminders by location ("you're at a station you've been to;
  log a session?") — nice-to-have but adds geofencing complexity.

## Repo conventions

- Conventional, descriptive commit messages.
- All commits include the Claude Code session URL in the trailer.
- Branch: `claude/get-started-AEDLP`, pushed to
  `r2205/evsct` on GitHub.
