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

- Kotlin 2.2.10, Jetpack Compose, Material 3, Navigation Compose
- Room 2.8.4 (SQLite) with hand-rolled migrations
- Hilt 2.59.2 for DI; AndroidX Hilt 1.2.0 (`hilt-work`,
  `hilt-navigation-compose`)
- WorkManager 2.10.0 for the background backup-reminder check
- Coil 2.7 for image loading
- Apache POI for one-shot legacy XLSX import
- DataStore Preferences for cross-screen prefs
- Google Maps SDK + Maps Compose 4.4.1 + maps-compose-utils
  (for clustering, BitmapDescriptor pins, and `HeatmapTileProvider`)
- `android.graphics.pdf.PdfDocument` for the year-recap PDF export
  (no third-party PDF library)
- minSdk 30, targetSdk 35, AGP 9.2.1, Gradle 9.4.1, KSP 2.3.2
  (upgraded mid-project from AGP 8.13.2 / Gradle 8.13 / Kotlin 2.1)

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
- **v6 → v7**: added `continuesPrevious` (INTEGER NOT NULL DEFAULT 0)
  on sessions. Marks consecutive-stall sessions that share a continuous
  drive with the next one. Used by the driving-efficiency calculator to
  pair "previous-leg-end" with "current-leg-start" odometer readings.
- **v7 → v8**: added `waitTimeMinutes` (INTEGER, nullable) on sessions.
  Optional minutes the user spent queueing before the cable plugged in;
  doesn't enter kWh / cost calculations, only feeds the derived "stop
  time" stat (`Derived.stopTimeSeconds` = charge + wait, in seconds).
- **v8 → v9**: added `tags` (TEXT, nullable) on sessions. Free-form
  user labels stored comma-joined. `util/Tags.kt` handles parsing,
  sanitizing (commas in input are stripped so they can't break round-
  trip), and case-insensitive dedupe.

`fallbackToDestructiveMigration(dropAllTables = true)` is enabled as a
last-resort safety net (the explicit `dropAllTables` arg replaces the
deprecated zero-arg overload).

## Feature inventory (organized by area)

### Session entry form (`SessionEditScreen`)

- Date/time, charging type (DC Fast / AC L2 / AC L1), pricing model
  (per-kWh, per-min, flat, free, hybrid).
- **Order**: Vehicle chips at top (under date/time), then Odometer,
  Energy, Cost, Currency chips, Duration, Battery start/end. Posted
  rates section below.
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
  `(brand, address, city)` case-insensitive. Station/stall name is
  intentionally **not** part of the dedup key (see "stop-grouping" note
  below).
- **GPS autofill**: tap the tertiary-tinted card to request location
  permission; `LocationManager` one-shot with 15s timeout, last-known
  fast path, `Geocoder` reverse-lookup. Result reported via snackbar.
  Coordinates are stored on the session for the map view.
- **Pick on map**: alternative entry point that opens
  `MapPickerScreen` — a fullscreen map with a centered crosshair pin
  and "Cancel / Use this location" buttons. On confirm, the picker
  reverse-geocodes the chosen point and populates address/city/province,
  then propagates the picked coordinates to every session sharing the
  same stop key (so the averaged map pin actually moves — see
  `applyPickedLocation` in `SessionEditViewModel`). Includes the same
  layer switcher as the main map.
- **Region normalization**: `Saskatchewan`, `saskatchewan`, `Quebec`,
  `Québec` all map to two-letter codes. All CA provinces and US
  states/DC. Diacritic-insensitive.
- **Vehicle picker**: horizontally scrolling chips at the top of the
  form, defaults to the user's default vehicle. New sessions started
  from a vehicle tab on the log pre-select that vehicle (passed via
  nav arg).
- **Trip picker**: AssistChip row with "None" + each trip,
  horizontally scrollable so trips don't get clipped beyond screen
  width.
- **Currency chips**: small CAD / USD chip row beside the cost field.
  New sessions seed from the user's default-currency preference.
  Existing sessions keep whatever they were saved with.
- **Odometer / range fields are unit-aware**: label flips to (km) or
  (mi) based on the user's pref; entered values are converted to
  canonical km on save and converted back on load. **Round-trip fix**:
  when text is unchanged we save the original km value verbatim so the
  km↔mi roundtrip never drifts the stored number.
- **`continuesPrevious` toggle**: small switch labelled "Continued
  drive after this session" that the driving-efficiency calculator
  uses to pair adjacent sessions. Prevents counting "we sat at the
  charger overnight" gaps as a leg.
- **Wait time field**: optional "Wait time (min, optional)" integer
  field placed under the duration block. Captures queue time before
  charging started; stored as `waitTimeMinutes: Int?` and parsed
  through `toIntOrNull()?.takeIf { it >= 0 }` so negatives never land
  in the DB.
- **Tags field**: a "Tags" section above Notes. Existing tags render
  as `InputChip`s with an X to remove; an "Add tag…" `OutlinedTextField`
  commits on Enter or on typing a comma — the comma path lets the
  user type "work, winter, fast" without reaching for Done. Dedupes
  case-insensitively via `Tags.add` so "Work" and "work" don't end
  up as two chips.
- **Receipt attachment**: photo or PDF, picked from a small "Photo /
  PDF" bottom-sheet chooser. Photos use the Photo Picker; PDFs use
  `OpenDocument` filtered to `application/pdf`. Storage preserves the
  source extension (`.jpg` / `.pdf`). 25 MB cap enforced via a bounded
  copy; oversize attachments raise `FileTooLargeException` and surface
  a snackbar instead of silently truncating.
  - Photos render inline at 180dp with **pinch-to-zoom** fullscreen
    (1×–6×, pan when zoomed, double-tap toggle 1×↔2.5×, single-tap
    dismiss only at 1×). Shared with the vehicle profile photo viewer
    via `ImageZoomDialog`.
  - PDFs render as a "PDF receipt — tap to open" tile and hand off to
    the system PDF viewer via `ACTION_VIEW` through a `FileProvider`
    pointed at `filesDir/receipts/`.
  - Remove button now opens a confirmation dialog (avoids accidental
    delete).
- **File-deletion deferral**: replacing or removing the receipt
  doesn't delete the prior file until `save()` actually commits.
  `originalReceiptPath` and `touchedReceiptPaths` track what's safe to
  clean up; `onCleared()` (via an `@AppScope` CoroutineScope) handles
  the back-out case so a user who edits, swaps the photo, then taps
  Back doesn't end up with their original photo deleted.
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
  attached. The duration line appends `+Xm wait` when the session
  carries a wait time; a wrapping FlowRow of `#tag` pills sits below
  the meta line when tags are set.
- **Vehicle tabs** — `ScrollableTabRow` with All + each vehicle.
  Hidden when ≤ 1 vehicle.
- **Search** (toggle via top-bar magnifying glass): matches brand,
  city, prov, address, station, stall, notes.
- **Filter sheet** (tune icon next to search): brand FilterChips +
  date range with presets (All time / This month / Last 3 mo. /
  Last year) + custom From/To date pickers + a Tags multi-select
  FilterChip row populated from `tagsInUse` (every distinct tag the
  user has ever set, case-insensitive deduped, A–Z). Date filter
  honors the device's local timezone (a bug we fixed mid-project —
  was applying UTC bounds and excluding sessions logged late in the
  day). Tag matching is OR (a session matches if it carries any of
  the selected tags) and case-insensitive.
- **Active-filter chips** below search; tap to remove individually,
  Clear All to wipe. Tag filter shows as `#tagname` (single) or
  `N tags` (multiple); the filter-icon badge counts tags as one slot.
- **Sort**: a Sort icon (`Icons.AutoMirrored.Filled.Sort`) in the top
  bar opens a small `DropdownMenu` with four options — Date (newest)
  default, Cost (highest), Efficiency ($/kWh, cheapest first), Brand
  (A–Z). Each option uses date as the secondary key so ties resolve
  to recency, and sessions missing the primary field fall to the
  end via `nullsLast` / `nullsFirst` (descending needs the latter
  to keep nulls at the bottom). Not persisted across launches —
  matches the in-memory `vehicleFilter` pattern.
- **Multi-select**: long-press to enter selection mode, tap to toggle.
  Selection-mode top bar shows count + clear / select-all / assign
  trip. Bulk-assign trip uses a single SQL UPDATE.
- **System back** in selection mode → clears selection; with active
  filters → clears them.
- **Backup nudge banner** (tertiary-tone): respects the user's
  configurable threshold (default 30 days) and the master enable
  toggle. State stored in `AppPreferences` DataStore (`last_backup_at`
  + reminder prefs).
- **Empty state**: shared `EmptyState` composable on first launch with
  a "Add session" call to action. Same composable used on Vehicles,
  Trips, Stats, and Map for consistent zero-data guidance.
- Top bar: Search · Sort · Stats · Map · Trips · Settings.

### Map view (`MapScreen`)

- Google Maps SDK + Maps Compose. API key read from `local.properties`
  (gitignored) into a manifest placeholder, so the repo stays free of
  secrets. README documents the Cloud Console setup, including how to
  fingerprint the debug + release signing keys via the
  `:app:signingReport` Gradle task.
- **One pin per distinct stop** — sessions are grouped by
  `brand + address + city` (case-insensitive). Coordinates are
  averaged across the contributing sessions. Station/stall name is
  **intentionally not part of the key** so visits where the user
  logged a different stall number all share the same pin (mirrored in
  `SessionEditViewModel.stopKey`).
- **Trip-colored pins** — each trip carries a color from a fixed
  10-swatch palette (`TripPinColor` enum: Red, Orange, Yellow, Green,
  Cyan, Azure, Blue, Violet, Magenta, Rose). Pin coloring rules:
  - all visible visits to a stop share one trip → that trip's hue
  - all un-tripped → default red marker
  - mixed across multiple trips → neutral gray "shared" pin (custom
    bitmap) so the visual doesn't lie about which trip owns the stop
- **Filter sheet** (filter icon in top bar):
  - "Color pins by trip" switch (off → all-red mode). Toggle forces a
    `clearItems() + addItems()` on the cluster manager so the
    `DefaultClusterRenderer` actually re-runs `onBeforeClusterItemRendered`
    instead of returning cached markers.
  - "Cluster nearby pins" switch — disables clustering entirely so
    every pin renders individually regardless of zoom (useful for
    full-detail browsing of a road trip).
  - "Show pins for vehicle" — single-select FilterChip row ("All
    vehicles" + one chip per garage entry) hidden when fewer than two
    vehicles exist. The selection is applied *before* stops and trip
    options are computed so the trip list collapses to only the trips
    that vehicle actually visited; stale ids (deleted underneath) are
    dropped silently.
  - Checkbox list of trips and an "Untripped sessions" row. Hiding one
    trip on a multi-trip stop re-colors that stop to the remaining
    trip rather than staying gray.
  - "Show all" *and* "Hide all" actions next to the trip list — each
    appears only when it has work to do, so they don't both light up
    when the filter is already at one extreme. Hide-all is the
    "start from nothing, opt in just a few" workflow when the trip
    list grows long.
  - A small dot on the filter icon indicates active filters.
- **Layer switcher** in the top app bar — opens a small menu offering
  Default / Satellite / Hybrid / Terrain. Selection persists via
  `AppPreferences.mapType`. Shared composable `MapTypeMenu` is reused
  by `MapPickerScreen`.
- **Heatmap toggle** lives below the basemap rows in the same Layers
  menu (only on the charging map, not the location picker — the
  picker passes `null` for the optional `heatmapEnabled` /
  `onToggleHeatmap` params and the row hides). When on, pins disappear
  and a `TileOverlay` driven by `HeatmapTileProvider` renders instead,
  weighted by `MapStop.visits` so a daily home charger burns brighter
  than a one-off road-trip stop. The cluster manager is `clearItems`'d
  while heatmap mode is active so toggling clustering doesn't fight
  the overlay. Persisted as `mapHeatmapEnabled` (defaults off so the
  first-open experience stays the familiar pin view).
- **Trip route polylines** — a "Trip routes" toggle sits next to the
  Heatmap row in the Layers menu. When on, every visible trip with
  two or more located visits draws a colored `Polyline` connecting
  its sessions in chronological order, using the trip's existing
  `pinColor` swatch (gray fallback when none). `geodesic = true` so
  long-distance lines bend with the great circle instead of looking
  like flat-Earth shortcuts. Polylines respect the same hidden-trip
  and vehicle filters as the pins, and are suppressed when heatmap
  mode is on — the layers menu grays the row out at the same time
  (preserving its checkmark state, so flipping heatmap back off
  restores the previous polyline preference). Persisted as
  `mapPolylinesEnabled` (defaults off).
- **Manual `ClusterManager`** (rather than the `Clustering(items, …)`
  convenience composable) so we can tune the algorithm and keep
  trip-colored markers. Set up inside `MapEffect(Unit)` once the
  `GoogleMap` is available, then driven via `LaunchedEffect`s on the
  pin list and the color-by-trip flag. Tunables:
  - `NonHierarchicalDistanceBasedAlgorithm.maxDistanceBetweenClusteredItems = 40`
    (px). Loose enough that a road trip's pins stay visible at country
    zoom but tight enough to merge same-city stops.
  - `minClusterSize = 6` so two or three nearby visits don't collapse
    into a generic cluster bubble; you have to have a real density of
    stops before the bubble appears.
  - Custom `ChargingStopClusterRenderer` extending
    `DefaultClusterRenderer` to apply per-pin `BitmapDescriptor`s
    (trip color or "shared" gray bitmap).
- **First-open backfill**: when the screen opens, every distinct stop
  that has only a textual address is reverse-geocoded and the
  coordinates written back to all sessions sharing that address.
  Progress shows in a banner. Stops that fail to geocode are reported
  as "N unlocated" in the subtitle. Throttled to once per 24h via
  `lastMapBackfillAt` in `AppPreferences` so cold starts don't
  re-attempt the same unresolvable addresses every launch.
- Camera auto-frames around the visible pins on first arrival (gated
  by a `rememberSaveable` flag so subsequent state ticks don't yank
  the camera back).
- **Geocoder disambiguation** (`LocationAutofill.geocode`):
  forward-geocode tries the plain query first, retries with the
  country code (Canada/USA) appended if no result, then falls back to
  city-only, and validates results against the typed city's
  `Address.locality`. Fixed a bug where "100 North Service Road,
  Davidson, SK" resolved to a Moose Jaw service road.
- **Sibling propagation**: when a session's address is edited and
  re-geocodes, the new coords are propagated to every session sharing
  the same stop key. Without this, the averaged pin barely moves
  because most rows still have stale coords.
- **Gotcha** (`ba9030b`): `BitmapDescriptorFactory.fromBitmap()` and
  `defaultMarker(hue)` only work after the Maps SDK is initialized,
  which happens when `GoogleMap` composes. Building the shared-pin
  bitmap inside a top-level `remember { ... }` ran *too early* and
  crashed the screen on entry. Fix: build all `BitmapDescriptor`s
  inside `GoogleMap`'s content lambda (or inside `MapEffect`).

### Map picker (`MapPickerScreen`)

Standalone fullscreen flow used from the session edit form's "Pick on
map" action. Centered crosshair pin always tracks the camera target;
"Cancel" returns to the edit screen, "Use this location" returns the
camera target lat/lng via `SavedStateHandle` on the previous back
stack entry. Includes the same `MapTypeMenu` for layer selection.
Camera initial position is seeded via nav String args (Float would
lose precision; `NavType.Double` doesn't exist out of the box).

### Driving efficiency

A bottom-of-page collapsible card on the trip detail screen showing
measured km/kWh between consecutive charging sessions for the same
vehicle on the same trip.

- **Formula** (`util/EfficiencyAnalysis.kt`): for adjacent sessions A
  → B,
  `energy_used_kWh = (battery_end[A] − battery_start[B]) × capacity / 100`.
  Distance comes from `odometer[B] − odometer[A]`. Both sides are
  required, plus the vehicle's battery capacity, plus `continuesPrevious
  = true` on A. We **don't** fall back to "kWh delivered to A" — that
  isn't what the car used between stops.
- **Reporting**: `EfficiencyReport` exposes pairs that calculated and
  pairs that didn't, with reason codes (`MissingOdometer`,
  `MissingBattery`, `MissingCapacity`, `NotContinuous`). The UI
  surfaces both: average km/kWh + a dropdown of unmeasurable legs so
  the user can see why a number is missing.
- **Hidden when no data**: card collapses entirely if there are zero
  measurable legs and zero excluded pairs to explain.

### Vehicles

- **List**: tap a row → detail screen. Default vehicle gets a star.
- **Detail screen**: hero card with photo + label, Lifetime card
  (sessions, total cost, energy, distance, $/km or $/mi, $/kWh, avg
  power, total charge time, top brand), highlight tiles (fastest
  charge, cheapest $/kWh, most expensive $/kWh, last charged), recent
  sessions list (last 10) with tap-to-edit. Pencil icon in top bar
  opens edit screen. Total charge time shows a "(N sessions missing
  duration)" caveat when not all sessions have a recorded duration.
- **Tap-to-zoom photo**: tapping the hero photo opens
  `ImageZoomDialog` — pinch zoom 1×–6×, pan when zoomed, double-tap
  toggle. Shared with the receipt photo dialog.
- **Edit screen**: profile photo via PhotoPicker (copied to
  `filesDir/vehicles/<uuid>.jpg`, 25 MB cap with size-specific
  snackbar on overflow), name, year/make/model/trim, battery kWh,
  range (km or mi based on pref), VIN, notes, Default-vehicle switch
  (mutually exclusive — `clearDefaultExcept`), delete with
  confirmation.

### Trips

- **List**: rows show name, sessions, total $, energy, distance
  ($/km or $/mi based on pref). + FAB opens shared edit dialog.
- **Detail screen**: stats card (sessions, total $, energy, distance,
  total charge time with missing-duration flag, average km/kWh from
  the efficiency analysis when available) + sessions list + driving
  efficiency card + pencil in top bar for edit dialog.
- **Edit dialog** (`TripEditDialog`, used for both create and edit):
  Name, Start/End odometer (unit-aware label, converts to km on save),
  Notes. "Map pin color" button opens a 5×2 swatch picker.
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
- **"vs gas this month" card** — slotted right under the headline.
  Big "Saved $X" line with sub-text "$cost charging vs ~$gas on gas"
  and an "Assumes $2.15/L · 12 L/100 km" caption. Distance preference:
  per-vehicle odometer deltas where the *end* session is in this
  month (so the delta from the previous month's last charge counts),
  falling back to month-kWh × 4 km/kWh when odometer data is sparse.
  Constants are hardcoded for now (BC pump-price defaults), earmarked
  for a Settings page later. Card hides entirely when there's no
  driving data this month.
- Cost-by-month and Energy-by-month — 12-month rolling horizontal
  bars, normalized to the largest bucket.
- Top brands by spend (top 8).
- Charging type split (DC/AC L2/AC L1) — stacked bar with %.
- **"When you charge" card** — two 7×24 day-of-week × hour-of-day
  heatmaps (Sunday on top, midnight on the left) showing where in
  the week the user actually plugs in. **DC Fast** (amber) is
  rendered separately from **AC L2 + L1 grouped** (blue) so road-trip
  patterns don't blur with home/commute charging. Each grid shows a
  "Peak: Sat 2 pm" line at the top right; cell shading is alpha-scaled
  0.20→1.00 against the busiest hour so single-session cells still
  visibly tint. Hour ticks (12 am / 6 am / noon / 6 pm / 11 pm) sit
  underneath via `Arrangement.SpaceBetween` — heuristic alignment with
  the corresponding columns above, deliberately not pixel-perfect.
  Empty buckets hide their grid entirely.
- **Year recap entry** — a PDF-icon action in the Stats top bar
  carries the currently-selected `vehicleFilterId` into a new
  `YearRecapScreen` (see below). The icon is always present once
  there's at least one session.
- Vehicle filter mirrors the log tabs.
- No charting library; pure Compose primitives (Box width-fraction).
- **Multi-currency totals**: the Stats headline + per-vehicle lifetime
  uses `CurrencyTotals` (a map keyed by ISO currency code). When all
  rows share one currency it renders normally (`titleMedium`); when
  mixed, the row stacks vertically as `titleSmall`
  (`"$245.00 CAD"` / `"$89.50 USD"`). We never auto-FX — see "Notable
  design decisions".
- Aggregate $/km and $/kWh use the user's default currency for the
  conversion target *only when all underlying rows already share it*;
  otherwise the metric is hidden to avoid mixing units.

### Year recap (`YearRecapScreen`)

End-of-year-style recap reachable from the Stats top-bar PDF icon.
Pre-selects the year of the user's most recent session (`ScrollableTabRow`
of years where they have data) and recomputes the recap on each pick.

- **Scoping**: takes an optional `vehicleId` nav arg
  (`-1L` sentinel = "All"). When the user opens it from a specific
  vehicle's Stats tab, the VM reads the arg from `SavedStateHandle`
  and filters the session list *before* any year bucketing — so
  available-years, totals, top brands, monthly trend, longest trip,
  and the rendered PDF all reflect that one vehicle. The VM also
  resolves the vehicle's display name into `state.vehicleName`.
- **Content** (scrollable, top to bottom):
  - Headline grid: sessions / total cost / total energy / total
    distance.
  - Monthly cost (Jan–Dec horizontal bars, same `BarList`-style
    primitives as the rest of Stats — local copy in
    `YearRecapScreen` to keep the recap decoupled from `StatsScreen`'s
    private composables).
  - Top 8 brands by spend.
  - Longest trip (whole-trip distance from
    `TripRepository.observeAllWithStats()`, picked among trips with
    at least one session in the selected year — slight overclaim
    when a trip spans years, accepted for v1).
- **Save / Share PDF buttons** at the bottom mirror the Full backup
  pattern: Save uses SAF `CreateDocument("application/pdf")`; Share
  writes to `cacheDir/recap-share/` and fires `ACTION_SEND` through
  the existing FileProvider (cache path declared in `file_paths.xml`).
  Both filenames go through `defaultRecapFilename(year, vehicleName)`
  — `evsct-recap-2024.pdf` when scope is All,
  `evsct-recap-2024-Tesla-Model-3.pdf` when scoped, slugified.
- **PDF rendering** (`YearRecapPdf.writeYearRecapPdf`) — single A4
  portrait page with `android.graphics.pdf.PdfDocument`. Layout is
  hand-coded against a fixed point grid: title block, 4-cell
  headline row, monthly-cost bar chart drawn straight to `Canvas`,
  top-brands list with right-aligned $-amounts, longest-trip block,
  footer. No third-party PDF library; deliberately text-and-list
  heavy so wide character ranges don't break layout.

### Backup & export

- **Full backup** (`Settings → Full backup`): single `.zip`
  containing `backup.json` + `vehicles/<uuid>.jpg` +
  `receipts/<uuid>.{jpg,pdf}`. Schema-versioned (currently **v5**;
  older v1–v4 still restore cleanly):
  - v2 added receipts
  - v3 added per-session lat/lng
  - v4 added per-trip pinColor
  - v5 added per-session `continuesPrevious`
  - **Post-v5 additive fields** (no version bump): `waitTimeMinutes`
    and `tags` are read/written through the same JSON keys regardless
    of version. v5 readers ignore unknown keys, and a future version
    bump can promote them to a "v6 added wait time + tags" entry
    without code changes. Pending decision; tracked in Outstanding
    ideas.
  Restore wipes the DB and reinstalls inside one Room transaction;
  foreign keys remapped to fresh primary keys via in-flight ID maps so
  backups from another phone work cleanly. Red "Erase and restore"
  confirmation gates the destructive path. After a successful export,
  the scheduler clears any pending reminder notification and re-arms
  the next check for `now + thresholdDays`. Restore also calls
  `recordBackup()` so the reminder timer resets.
- **Save / Share buttons**: the card has *two* export actions side by
  side. **Save backup file…** is the SAF flow that's been there
  forever — `CreateDocument("application/zip")`, user picks a folder.
  **Share backup file…** is the newer sibling: `BackupIo.prepareShareFile`
  builds the same zip into `cacheDir/backup-share/` (clearing prior
  share files first so cache doesn't accumulate), the screen wraps it
  with FileProvider and fires `ACTION_SEND` through `Intent.createChooser`
  for Drive / email / Messages / etc. Both paths go through the same
  private `writeBackupZip(out: OutputStream)` helper so they can't
  drift. Filenames are `evsct-backup-yyyy-MM-dd-HHmm.zip`; the share
  intent passes the same name in `EXTRA_SUBJECT` *and* `EXTRA_TITLE`
  because Drive ignores the FileProvider's display name and uses one
  of those as the saved filename instead.
- **Hardening**:
  - Top-level array presence is required (rejects empty/malformed
    payloads up front).
  - Per-row `runCatching` so a single bad row skips itself rather
    than failing the whole import.
  - `installFiles` runs *only after* the DB transaction commits,
    avoiding partial-state media in `filesDir`.
  - `cleanOrphans` is restricted to UUID-named files matching
    `MANAGED_FILE_PATTERN` so a stray non-app file in the same dir
    never gets nuked.
  - Bounded decompression: zip-bomb defense via `copyBoundedTo` /
    `readBytesBounded` with per-entry and total caps.
  - Zip-slip guard via `sanitizedBasename` on JSON-declared paths.
- **CSV export/import** (kept for spreadsheet analysis use case):
  flat session table with columns including `trip_name`,
  `vehicle_name`, `latitude`, `longitude`, `continues_previous`.
  Round-trips via `CsvIo`. Trips and vehicles are recreated by name
  on import.
  - **Formula injection defense**: any field that would start with
    `=`, `+`, `-`, `@`, tab, or CR is prefixed with `'` on export.
    Import strips a leading `'` only when followed by a trigger char,
    so the round-trip is asymmetric and never injects a real apostrophe.
- **Legacy XLSX importer**: one-shot for the original
  `DC Fast Charging.xlsx`. Auto-tags imported rows with the user's
  default vehicle (if one exists at import time). Apache POI parses
  Excel duration cells (fraction-of-a-day) and date cells. Hardened
  with `ZipSecureFile.setMaxFileCount(2_000L)` and bounded reads to
  defuse zip-bomb-style attacks.

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
  `backup_reminder` channel. The notifier itself only knows how to
  fire/clear a notification *right now* — scheduling lives one layer
  up in `BackupReminderScheduler`.
- **Background scheduling** (`BackupReminderScheduler` +
  `BackupReminderWorker`): WorkManager-driven check that wakes the
  app briefly when a backup is overdue, even with the app fully
  closed. The scheduler computes the next firing time
  (`lastBackupAt + thresholdDays`, or `now + 1 day` if already
  overdue, or "cancel" if reminders are disabled) and enqueues a
  single `OneTimeWorkRequest` under a unique-work name with
  `REPLACE` policy so a fresh `refresh()` always wins over a stale
  pending worker. Worker calls `scheduler.refresh()` (which posts/
  cancels the notification *and* re-enqueues the next check), so the
  daily nag chain self-perpetuates while overdue and self-terminates
  on the next backup. `MainActivity.onResume`, every reminder-pref
  toggle, and every backup write all go through `scheduler.refresh()`
  too — same idempotent entry point.
- **EvsctApplication implements `Configuration.Provider`** so
  `HiltWorkerFactory` can inject `BackupReminderScheduler` into the
  `@HiltWorker BackupReminderWorker`. AGP 9 + WorkManager 2.10 don't
  need the default initializer disabled when a custom Configuration
  is provided.
- Battery cost is essentially zero: WorkManager hands timing to the
  OS, which batches our brief check into the next Doze maintenance
  window. No service, no wake-lock, no foreground process. Trade-off:
  "due at exactly day 30" can fire 1–6 hours late depending on Doze.
  Acceptable for "back up soon" semantics.

### Theme

- Hand-tuned Material 3 palette (light + dark): deep emerald
  primary, sage secondary, blue-grey tertiary, with all container/
  on-container slots filled. Material You **disabled by default**
  so the look stays consistent regardless of wallpaper.
- Charging-type accents: amber (DC fast), blue (AC L2), purple
  (AC L1) used for row stripes, type badges, and the stats stacked bar.
- **Theme override** (`Settings → Theme`): segmented switch with
  System / Light / Dark. SYSTEM follows the OS dark-mode setting;
  LIGHT and DARK force the corresponding palette regardless of OS.
  Stored as `themeMode` in `AppPreferences`; read at the top of
  `MainActivity` and threaded into `EVSCTTheme` so the override
  applies everywhere including dialogs.

## Settings screen

Cards (top to bottom):
1. **Vehicles** — entry to the vehicle list.
2. **Units & currency** — Distance segmented switch (Kilometres /
   Miles), Default currency segmented switch (CAD / USD).
3. **Theme** — segmented switch (System / Light / Dark).
4. **Full backup** — Save backup file… / Share backup file… /
   Restore from backup….
5. **Backup reminder** — enable, threshold days field, Android
   notification toggle.
6. **Backup (CSV)** — Export to CSV.
7. **Import (CSV)** — Import CSV with replace-existing toggle.
8. **One-time XLSX import** — for the legacy log.

Wrapped in `verticalScroll` so the bottom card never falls off (a
bug we hit early when the list was too tall for some screens).

## App-wide preferences

`AppPreferences` (DataStore) exposes:
- `lastBackupAt: Long?`
- `reminderSettings: Flow<BackupReminderSettings>` (enabled, threshold
  days, notify enabled)
- `userUnits: Flow<UserUnits>` (useMiles, defaultCurrency)
- `mapType: Flow<String>` (NORMAL / SATELLITE / HYBRID / TERRAIN)
- `mapClusteringEnabled: Flow<Boolean>` (default true)
- `mapHeatmapEnabled: Flow<Boolean>` (default false)
- `mapPolylinesEnabled: Flow<Boolean>` (default false)
- `themeMode: Flow<String>` (SYSTEM / LIGHT / DARK)
- `lastMapBackfillAt(): Long?` (one-shot read for the throttle)
- `snapshot()` for one-shot reads (used by `BackupReminderNotifier`)

Units/currency are surfaced via a CompositionLocal
(`LocalUserUnits`) hosted by a tiny `UserPrefsViewModel` mounted at
`MainActivity`, so any composable can read them without ViewModel
plumbing. `Format` helpers (`distance`, `moneyRatePerDistance`) take
the unit pref as a parameter; aggregates pass the default-currency to
`Format.money`.

## Shared composables

- **`EmptyState`** — circle icon + title + body + optional action
  button. Used on every list/detail screen for first-launch guidance.
- **`ImageZoomDialog`** — pinch-zoom (1×–6×), pan, double-tap toggle,
  tap-to-dismiss-at-1×. Used by both the receipt preview and the
  vehicle profile photo viewer.
- **`MoneyStat`** — stacks multi-currency totals vertically when
  mixed; renders single-currency at `titleMedium`. Built on top of
  `CurrencyTotals` so callers don't have to format manually.
- **`MapTypeMenu`** — top-bar layer switcher reused by `MapScreen`
  and `MapPickerScreen`. Optional `heatmapEnabled` / `onToggleHeatmap`
  and `polylinesEnabled` / `onTogglePolylines` callback pairs surface
  display-mode rows beneath the basemap list; the picker passes nulls
  for both pairs and the rows hide. A `polylinesAvailable: Boolean`
  parameter (true by default) lets the screen gray the Trip-routes row
  out when heatmap mode owns the canvas.
- **`util/Tags.kt`** — comma-joined-string parsing for the new tags
  column. `parse` / `serialize` / `add` / `remove` / `sanitize` handle
  case-insensitive dedupe and strip the delimiter from user input so
  it can't break round-trip storage.

## Unit tests

`app/src/test/java/com/evsct/app/`:
- **`util/CurrencyTotalsTest.kt`** — single/mixed currency rendering,
  empty case, `singleCurrency` invariants.
- **`util/EfficiencyAnalysisTest.kt`** — SoC formula correctness,
  exclusion reasons, `continuesPrevious` gating, capacity-missing
  case, decoupled-from-trip pairing.
- **`data/csv/CsvTest.kt`** — formula-injection escape on export,
  trigger-char stripping on import, asymmetric round-trip.

Run from Android Studio (Right-click → Run 'tests') or
`./gradlew :app:testDebugUnitTest` from the terminal once
`JAVA_HOME` is set.

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
  pick a moment. When totals span multiple currencies they're shown
  side by side, never summed.
- **Distances are stored canonically in km**: km/mi pref is purely
  display + form input/output. Switching units never changes saved
  data (verified by the "no-op-save in miles mode" round-trip fix).
- **PDF receipts hand off externally**: rather than building an
  in-app PDF renderer, PDFs open via `ACTION_VIEW` through a
  FileProvider. Photos keep the inline pinch-to-zoom dialog.
- **Trip pin colors are mandatory but auto-assigned**: every trip
  ends up with a color so the map is always informative, but the
  user can override.
- **Multi-trip stops show as gray, not the latest trip's color**:
  better than implicitly lying about ownership.
- **Stops are grouped by `brand + address + city` only**: station/
  stall name is intentionally excluded from the key. Visits to the
  same physical charger that recorded different stall numbers each
  time still share one map pin.
- **Driving efficiency only uses SoC math**: kWh-delivered as a
  fallback would conflate "energy added" with "energy consumed", so
  legs without battery-end-on-A and battery-start-on-B are flagged
  as unmeasurable rather than approximated.
- **File deletes are deferred until save commits**: editing a session
  and tapping Back doesn't lose the existing receipt; only an explicit
  Save (or the form going out of scope after a Save) cleans the prior
  file.

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
  any custom marker bitmaps inside `GoogleMap`'s content lambda or
  inside a `MapEffect`, never in a top-level `remember`.
- **Maps Compose `Clustering(items, …)` overloads are restrictive**:
  there's a content-lambda variant, a renderer variant, and a
  cluster-manager variant — but no overload that takes both renderer
  and content. To get full algorithm + per-pin marker control,
  drop the convenience composable and drive a `ClusterManager`
  directly via `MapEffect(Unit)`.
- **`DefaultClusterRenderer` caches markers**: changing per-item
  state (e.g. trip color) and just calling `cluster()` won't
  re-invoke `onBeforeClusterItemRendered`. Force a rebuild with
  `clearItems()` + `addItems(items)`.
- **`combine()` is typed up to 5 flows**: bundle related prefs
  into intermediate Pair/data-class streams to fit the limit (we did
  this for `MapPrefs` to keep `MapViewModel.state` typed).
- **Forward geocoders return distant matches**: always validate the
  result against the typed city, and consider falling back to
  city-only with a country hint before accepting an arbitrary first
  hit.
- **`NavType.Double` doesn't exist out of the box**: pass lat/lng as
  Strings through nav args; `Float` would lose precision past a few
  decimals.
- **`SavedStateHandle` on `previousBackStackEntry` is the clean way
  to return values from a sub-screen**: the pick-on-map flow uses
  this rather than a global event bus.
- **Hilt-injected `SavedStateHandle` reads nav args directly**: the
  year-recap VM gets its `vehicleId` arg via `SavedStateHandle.get()`
  with the same key the route uses, no extra plumbing. Pair with a
  `-1L` sentinel so the nav arg can stay typed as primitive Long.
- **AGP 9 / Kotlin 2.2 toolchain coupling is tight**: bumping AGP
  from 8.13.2 to 9.2.1 also forced Gradle 9.4.1, JDK 17+, Kotlin
  2.2.10, KSP 2.3.2, Room 2.8.4, and Hilt 2.59.2. The Kotlin docs
  table claims KGP 2.2.x maxes out at Gradle 8.14, but Studio's
  Upgrade Assistant chose Gradle 9.4.1 anyway and it builds fine —
  with deprecation warnings. Trust the Assistant's pin set over the
  docs table.
- **Room < 2.7 crashes on Kotlin 2.2 metadata**: 2.6.1's KSP
  processor blew up with `IllegalStateException: unexpected jvm
  signature V` on a `@Query` returning `Unit`. Fixed by jumping
  straight to 2.8.4 (latest stable; 2.7.x is when KSP2 support
  landed).
- **Hilt 2.54 caps at Kotlin 2.1 metadata**: `hiltJavaCompileDebug`
  fails with `Provided Metadata instance has version 2.2.0, while
  maximum supported version is 2.1.0`. Hilt 2.59 explicitly added
  AGP 9 + Gradle 9.1+ support; 2.59.2 is the latest stable.
- **AGP 9 default-off flags worth knowing**: `BuildConfig` is now
  off by default (we don't use it, no impact); R8 is stricter about
  `-keepattributes` wildcards (we don't use any); manifest `package=`
  has been deprecated in favor of `namespace = ` in build.gradle
  (we already use namespace). Future-proofed by accident — not a lot
  to do here.
- **Share intent filename quirks**: Drive (and other receivers) read
  `EXTRA_SUBJECT` / `EXTRA_TITLE` as the *saved filename* and ignore
  the FileProvider's display name. Pass the actual filename — including
  the `.zip`/`.pdf` extension — on both extras to round-trip the name.
- **`compareBy(comparator, selector)` can't infer `T` from the lambda
  alone**: the two-arg overload needs both type params spelled out
  (e.g. `compareBy<ChargingSession, Double?>(nullsLast()) { it.totalCost }`)
  or the lambda body fails to type-check (`Unresolved reference 'totalCost'`).
  The single-arg `compareBy { it.x }` infers fine; only the comparator-
  taking variant trips this.
- **Descending sort with nulls-last needs `nullsFirst`**:
  `compareByDescending` swaps the operands before delegating to the
  inner comparator, so `nullsLast` (which orders null > non-null in
  ascending) ends up putting nulls *first* in descending. Use
  `nullsFirst` for descending-with-nulls-at-the-end.
- **AutoMirrored icons replace several `Icons.Default.*`**: `Sort`,
  `ArrowBack`, `Label`, `TrendingUp`, `TrendingDown` all moved to
  `Icons.AutoMirrored.Filled.*`. The non-mirrored versions are
  deprecated and emit warnings; one-line import swaps fix it.

## Audit closeouts

Mid-project we ran an end-to-end bug audit (15 findings) and a
security audit (M1 + M2 + Mn1–Mn5). Highlights of what got fixed:

- **Bug audit**:
  - Trip picker clipped trips off the right edge → horizontalScroll.
  - Date filter applied UTC bounds, dropping late-day sessions →
    converted to device local zone.
  - Cost/$/kWh aggregates summed CAD + USD as one number →
    `CurrencyTotals` + `MoneyStat`.
  - Map camera yanked back on every state tick → gated by
    `rememberSaveable` flag.
  - Editing a session's address didn't move its map pin → re-geocode
    on save + propagate to siblings sharing the stop key.
  - Odometer round-trip in miles mode wrote a converted km value back
    on no-op save → preserve original km when text unchanged.
  - Repeat backfill attempts on every cold start → throttle via
    `lastMapBackfillAt`.
  - Receipt/photo pickers crashed on edge-case URIs → snackbar +
    bounded copy + size-specific error path.
  - `ON DELETE SET NULL` cascade firing on trip edits → route
    existing-row saves through `@Update`.
- **Security audit**:
  - **M1**: backup zip-slip via JSON-declared paths → `sanitizedBasename`.
  - **M2**: backup decompression unbounded → `copyBoundedTo` per entry
    + total cap; XLSX gets `ZipSecureFile.setMaxFileCount`.
  - **Mn1**: receipt/photo image stores unbounded → 25 MB caps with
    `FileTooLargeException`; size-specific snackbars.
  - **Mn2**: `cleanOrphans` could nuke unrelated files → restricted
    to `UUID.{jpg,pdf}` pattern.
  - **Mn3**: CSV formula injection on export → `'`-prefix triggers;
    asymmetric strip on import.
  - **Mn4**: PASSIVE_PROVIDER could leak last-known-location to
    unrelated apps' usage → don't fall back to it for the manual
    autofill.
  - **Mn5**: cities with null province dropped from autocomplete →
    fixed.
  - Auto-backup rules pointed at obsolete sharedpref path → updated
    to `domain="file" path="datastore/"` and added device-transfer
    media rules.
- **AGP 9 upgrade** (Studio's AGP Upgrade Assistant did the heavy
  lift; we then chased version-incompatibility errors as they
  surfaced):
  - Toolchain: AGP 8.13.2 → 9.2.1, Gradle 8.13 → 9.4.1,
    Kotlin 2.1.0 → 2.2.10, KSP `2.1.0-1.0.29` → `2.3.2`
    (the new KSP2 semver scheme).
  - Companion bumps surfaced by build failures: Room 2.6.1 → 2.8.4,
    Hilt 2.54 → 2.59.2.
  - **Warning cleanup pass** trimmed the AGP-9 deprecation noise from
    14 → 7 warnings. `gradle.properties` ended up with five flags
    actually needed and one suppression:
    - `android.builtInKotlin=false` and `android.newDsl=false` —
      have to stay because Kotlin Gradle plugin 2.2.10 still casts
      the AGP extension to the legacy `BaseExtension`. Removing
      either fails the build (`extension already registered` /
      `cannot be cast to BaseExtension`). Latest Kotlin (2.3.21,
      April 2026) doesn't fix this; planning to revisit only when
      something else in the chain forces a Kotlin bump.
    - `android.uniquePackageNames=false`,
      `android.dependency.useConstraints=true`,
      `android.r8.strictFullModeForKeepRules=false` — pre-AGP-9
      transitive-dep / R8 behavior, not yet flagged as deprecated.
    - `android.generateSyncIssueWhenLibraryConstraintsAreEnabled=false`
      added to silence the AGP-9 nudge for an already-deprecated
      "very large projects" perf flag.
    - Removed flags: `resvalues=true`,
      `defaultTargetSdkToCompileSdkIfUnset=false`,
      `enableAppCompileTimeRClass=false`,
      `usesSdkInManifest.disallowed=false`,
      `r8.optimizedResourceShrinking=false`. These were noisy
      no-ops once the surrounding warnings settled.
  - **Kotlin 2.x annotation-target opt-in**: `app/build.gradle.kts`
    moved from AGP's deprecated `kotlinOptions {}` block to the Kotlin
    plugin's `kotlin { compilerOptions { ... } }` DSL, with
    `freeCompilerArgs.add("-Xannotation-default-target=param-property")`
    to silence 12 KT-73255 warnings on Hilt `@Inject` constructor
    params. Required `import org.jetbrains.kotlin.gradle.dsl.JvmTarget`
    plus `jvmTarget.set(JvmTarget.JVM_17)`.
  - **Room 2.8.4 fallbackToDestructiveMigration deprecation**: zero-arg
    overload is gone; switched to `.fallbackToDestructiveMigration(
    dropAllTables = true)` to preserve previous "wipe everything" semantics.
  - The remaining 7 warnings all trace to upstream pins (6 to Kotlin
    Gradle plugin 2.2.10 lagging AGP 9, 1 to AndroidX shipping
    unstrippable `.so` files). Nothing left to clean from our side.
  - **No app code changes were required** for the upgrade itself —
    only build files and version pins.

## Outstanding ideas (not yet built)

- Per-vehicle lifetime stats by month / year (rolling charts).
- Search through Stats / Trips, not just the log.
- Merge-mode restore (current is replace-only).
- "View on map" entry from the trip detail screen, filtered to that
  trip's pins (the data and the filter mechanism already exist; just
  needs a nav route + button).
- Reminders by location ("you're at a station you've been to;
  log a session?") — nice-to-have but adds geofencing complexity.
- **Configurable gas-card constants**: $/L, L/100 km, and the
  fallback km/kWh are currently hardcoded in `StatsViewModel`
  (BC pump-price defaults). Lift these into Settings so non-CAD
  users can tune them. The card and the recap's distance fallback
  share the same `FALLBACK_KM_PER_KWH` so any new pref should drive
  both.
- **Year recap polish**: include the scoped vehicle's name in the
  PDF title block and the screen subtitle (right now it only shows
  up in the filename); pro-rate cross-year trips for "longest trip"
  rather than overclaiming the whole-trip distance; multi-page PDF
  when top-brands or trips overflow.
- **Bump backup `SCHEMA_VERSION` past 5**: the post-v5 additive
  fields (`waitTimeMinutes`, `tags`) currently ride along under
  `schemaVersion = 5` because they're forward-compatible (older
  readers ignore unknown keys). A bump to 6 is the conventional
  marker — would mean only same-or-newer installs can restore
  newer backups. Worth doing the next time we touch `BackupIo.kt`.
- **Drop the last AGP-9 conservative-mode flags**: five flags remain
  in `gradle.properties` after the cleanup pass (see "Audit
  closeouts" → AGP 9 upgrade). Two are pinned by Kotlin Gradle
  plugin 2.2.10's BaseExtension cast and need a Kotlin bump to lift;
  the other three are pre-AGP-9 transitive-dep / R8 behaviors not
  yet flagged as deprecated. Revisit when we next bump Kotlin.

## Repo conventions

- Conventional, descriptive commit messages.
- Apache 2.0 LICENSE in the repo root (David Robson, 2025/2026).
- All commits include the Claude Code session URL in the trailer.
- Default branch: `main`. Earlier work happened on
  `claude/get-started-AEDLP`, which has since been merged. Both live
  on `r2205/evsct` on GitHub.
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
