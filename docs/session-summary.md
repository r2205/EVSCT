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
- **v9 → v10**: promoted receipts from a single column on
  `charging_sessions` into a proper many-to-one `session_receipts`
  table (with `ON DELETE CASCADE`). MIGRATION_9_10 creates the table
  and copies every non-null `receiptImagePath` over as a row. The
  legacy column stays in the schema (kept on the entity, written null
  on every save going forward) — avoids a heavy table rebuild for
  what is now dead data.
- **v10 → v11**: added `originalFileName` (TEXT, nullable) on
  `session_receipts`. Captures the SAF picker's display name at
  attach time so PDF tiles can show e.g. "expense-aug-2025.pdf"
  instead of a generic label. Pre-v11 rows stay null; the user can
  backfill via the in-app rename action.
- **v11 → v12**: added `startBatteryPct` / `endBatteryPct` (INTEGER,
  nullable) on `trips`. With the existing start/end odometer these
  anchor the trip's first and last efficiency legs (home → first
  charge, last charge → home) via virtual endpoints in
  `EfficiencyAnalysis`. Single-vehicle trips only.

`fallbackToDestructiveMigration` was **removed** in the 2026-07 bug
sweep (finding #3): the migration chain is complete, so the only paths
that could trigger the fallback are version downgrades or a forgotten
future migration — and in both cases a crash on open is recoverable
while a silent full-table wipe is not. Schema JSONs are exported to
`app/schemas/` and committed (11.json, 12.json) so future changes diff
in review.

## Feature inventory (organized by area)

### Session entry form (`SessionEditScreen`)

- Date/time, charging type (DC Fast / AC L2 / AC L1), pricing model
  (per-kWh, per-min, flat, free, hybrid).
- **Order**: Vehicle chips at top (under date/time), then Odometer,
  Energy, Cost, Currency chips, Duration, Battery start/end. Posted
  rates section below.
- **Smart duration entry**: `25` → `0h 25m 0s`; `32:14` → `0h 32m 14s`
  (two-part is m:s, the stopwatch reading — sub-hour charges dominate);
  `1:25:00` exact. While focused, supporting text previews the
  interpretation live (`= 0h 32m 14s`). Phone-pad keyboard with an
  inline `:` button that inserts at cursor (uses `TextFieldValue` for
  proper caret control). Format swaps between pretty and editable on
  focus change.
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
- **Wait time field**: optional "Wait time (optional)" duration field
  placed under the duration block. Captures queue time before charging
  started; shares `DurationField` with charging duration (same parser,
  live preview, `:` button) and stores exact seconds as
  `waitTimeSeconds: Long?` (migration 12→13 rebuilt the table from the
  old whole-minutes column, values × 60 — lossless). The digits-only
  parser plus a `>= 0` save guard keep negatives out of the DB.
- **Tags field**: a "Tags" section above Notes. Existing tags render
  as `InputChip`s with an X to remove; an "Add tag…" `OutlinedTextField`
  commits on Enter or on typing a comma — the comma path lets the
  user type "work, winter, fast" without reaching for Done. Dedupes
  case-insensitively via `Tags.add` so "Work" and "work" don't end
  up as two chips.
- **Receipt attachments** (multiple per session as of DB v10): photos
  and PDFs in any mix, picked from a small "Photo / PDF" bottom-sheet
  chooser. Photos use the Photo Picker; PDFs use `OpenDocument`
  filtered to `application/pdf`. Files are stored as
  `filesDir/receipts/<uuid>.{jpg,pdf}`; a separate `session_receipts`
  table links them to their parent session with `ON DELETE CASCADE`.
  25 MB cap enforced per file via a bounded copy; oversize attachments
  raise `FileTooLargeException` and surface a snackbar.
  - **Tile per receipt** stacked vertically — each renders the photo
    inline at 180dp (with pinch-to-zoom fullscreen via
    `ImageZoomDialog`) or the PDF as an icon tile that opens
    externally via `ACTION_VIEW` through a `FileProvider` pointed at
    `filesDir/receipts/`. Below the stack sits an "Add another"
    button that re-opens the chooser sheet.
  - **PDF filename display** — at attach time we capture the SAF
    picker's `OpenableColumns.DISPLAY_NAME` and store it as
    `SessionReceipt.originalFileName`. The PDF tile labels itself
    with that name (truncated to 2 lines with ellipsis) instead of
    a generic "PDF receipt". The on-disk file is still UUID-named —
    `originalFileName` is purely a UI label.
  - **Rename action** on each PDF tile (sibling to Remove) — opens a
    small `AlertDialog` with a text field so the user can backfill
    or correct the label. Auto-appends `.pdf` (case-insensitive
    check) when the typed value doesn't already end in it. Clearing
    the field reverts the tile to the generic "PDF receipt" caption.
    Photos hide the rename action since they don't display a filename.
  - **Per-tile Remove** drops a single attachment immediately from the
    in-memory list; no confirmation dialog because the on-disk file
    isn't actually deleted until save() commits, so a wrong tap is
    reversible by tapping Back.
- **File-deletion deferral** (generalised from one path to a set):
  `originalReceiptPaths: Set<String>` captures everything the DB
  pointed at when the screen loaded; `touchedReceiptPaths` accumulates
  every path touched during the edit (originals + speculative copies
  + later-removed-from-the-list). save() diffs the in-memory list
  against the DB — inserts new rows, deletes removed rows, applies
  per-id `updateName` for renames where the label changed without
  bumping unrelated metadata. Then `reconcileReceiptFiles(finalPaths)`
  deletes every touched path that didn't survive into the saved set.
  `onCleared()` handles the back-out case (the user just leaves) via
  the `@AppScope` scope since `viewModelScope` is already cancelled.
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
  pill, vehicle pill (only on All tab), trip pill, and a paperclip
  icon when the session carries one or more receipts (read from
  `sessionsWithReceipts: Set<Long>` in the UI state, derived from
  `SessionReceiptDao.observeCountsBySession()`). The duration line
  appends `+Xm wait` when the session carries a wait time; a
  wrapping FlowRow of `#tag` pills sits below the meta line when
  tags are set.
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
- **Sticky month headers** — when the log is in Date order the rows are
  bucketed into "MMMM yyyy" groups (`monthGroups`) and each month's
  label rides the top edge as a `stickyHeader` while its rows scroll
  beneath it. Only the Date sort gets headers; cost / efficiency / brand
  orders interleave months, so they render a flat, header-less list.
- **Multi-select**: long-press a row to enter selection mode, or tap the
  Checklist icon in the top bar (shown once there's at least one
  session); tap rows to toggle. Selection-mode top bar shows count +
  clear / select-all / assign trip / **delete**. Bulk-assign trip uses a
  single SQL UPDATE. Bulk delete pops a red "Delete N sessions?"
  confirmation, then reads each session's receipt rows before the delete
  cascades them, removes the rows in one transaction, and parks the whole
  batch on the undo holder (see "Undo after delete").
- **System back** in selection mode → clears selection; with active
  filters → clears them.
- **Undo after delete** — a delete (single, from the edit screen's
  Delete button, or a multi-select batch) parks the removed rows on a
  shared `DeletedSessionUndoHolder` and raises a snackbar on the log —
  "Session deleted" / "N sessions deleted" with an **Undo** action. Undo
  re-inserts the rows and re-links their receipt files (which stay on
  disk until the offer resolves); dismissing it — or leaving the log —
  finalizes the delete and reclaims the files. The offer lives on the
  log screen, and its teardown only forfeits the offer it was showing so
  a replacement offer isn't killed.
- **Start-charge quick-track** — the extended "Add session" FAB (an
  `ExtendedFloatingActionButton` that shows its label at the top of the
  list and collapses to a bare + once the user scrolls) opens a chooser
  ("Track a charge now" / "Log a past charge"). Track-now persists a fresh
  session immediately (vehicle from the active tab or the default,
  currency from prefs, every other field null) and posts a persistent
  "Charging in progress" notification (`InProgressChargeNotifier`,
  LOW-importance channel, live chronometer via `setUsesChronometer`)
  that deep-links back to the session's edit screen — including from
  the Android Auto shade. The edit screen seeds an empty duration
  field with the live elapsed time, and save() falls back to elapsed
  when the duration is blank. On Android 13+ the flow requests
  `POST_NOTIFICATIONS` on first use and proceeds regardless of the
  answer — the permission only gates the shade entry, not the in-app
  tracking (the notifier records the tracked id before the permission
  check). The tracked id is mirrored to DataStore so process death
  doesn't orphan the ongoing notification.
- **Back-to-top FAB** — a `SmallFloatingActionButton` (up-chevron) fades
  in above the Add button once the list is scrolled past ~12 rows
  (`firstVisibleItemIndex > 12`) and animates the list back to the top.
  Available in selection mode too, since long lists are exactly where
  multi-select happens.
- **Backup nudge banner** (tertiary-tone): respects the user's
  configurable threshold (default 30 days) and the master enable
  toggle. State stored in `AppPreferences` DataStore (`last_backup_at`
  + reminder prefs).
- **Empty state**: shared `EmptyState` composable on first launch with
  a "Add session" call to action. Same composable used on Vehicles,
  Trips, Stats, and Map for consistent zero-data guidance.
- **Row animations** — list rows carry `Modifier.animateItem()`, so a
  delete, an undo re-insert, or a re-sort glides the affected rows into
  place instead of teleporting.
- Top bar: Search · Sort · Select · Settings. Log / Map / Stats / Trips are
  bottom-navigation tabs (July 2026): the bar shows only on those four
  top-level screens, tab switches use save/restore-state navigation so
  each tab keeps its scroll/camera/filter state, and system back from
  any tab lands on the Log. Predictive back is opted in via
  `enableOnBackInvokedCallback`.

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
- **On-map legend** — a compact translucent card in the bottom-left
  lists the color→trip mapping whenever trip coloring is on and pins are
  visible: one row per visible trip, plus an "Untripped" (red) row and a
  "Multiple trips" (gray) row when those pins are present, capped at six
  rows with a "+N more…" line. Tapping it opens the filter sheet.
- **Filter sheet** (filter icon in top bar):
  - (The "Color pins by trip" and "Cluster nearby pins" toggles no
    longer live here — they moved to the Layers menu. This sheet now
    decides only *which* stops show, not how the map looks.)
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
- **"Color pins by trip"** and **"Cluster nearby pins"** toggles also
  live in the Layers menu (moved here from the filter sheet — Layers is
  "how the map looks", the filter sheet is "which stops show"). "Color
  pins by trip" (off → all-red mode) forces a `clearItems() +
  addItems()` on the cluster manager so the `DefaultClusterRenderer`
  re-runs `onBeforeClusterItemRendered` instead of returning cached
  markers, and it now **persists across restarts** via
  `AppPreferences.mapColorByTrip` (default on). "Cluster nearby pins"
  disables clustering entirely so every pin renders individually
  regardless of zoom, and likewise persists via
  `AppPreferences.mapClusteringEnabled`.
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
- **Tap a pin to drill into its sessions**:
  - 1-session pin → tap shows the Maps default info window (brand,
    address, "1 visit") just like before; tapping the info window
    navigates to that session's edit screen via the screen's new
    `onEditSession: (Long) -> Unit` callback (wired by `EvsctNavGraph`
    to `Routes.sessionEdit(id)`).
  - N-session pin → tap consumes the click (returns `true` from
    `setOnClusterItemClickListener`, suppressing the info window)
    and opens a custom `ModalBottomSheet`. The sheet lists each
    session at the stop with date, kWh, a Trip / Untripped badge,
    and the vehicle name (when ≥2 vehicles). Tap a row to jump to
    its edit screen.
  - The selected stop lives on a `MutableStateFlow<MapStop?>` in
    `MapViewModel` bundled with `filters` into `filtersAndSelection`
    so the outer 5-arg combine still fits. On every emission the VM
    re-resolves the selected stop against the currently-visible
    stops list, so the sheet auto-closes when a filter change hides
    its pin.
- **First-open backfill**: when the screen opens, every distinct stop
  that has only a textual address is geocoded and the coordinates
  written back to all sessions sharing that address. Progress shows in a
  banner. Stops that fail to geocode are reported as "N unlocated" in
  the subtitle **and raise a one-time snackbar** once the pass completes
  ("N addresses couldn't be located — those stops stay off the map…"),
  so a silently-missing pin gets explained instead of just vanishing.
  Throttled via `lastMapBackfillAt` in `AppPreferences` so cold starts
  don't re-attempt the same unresolvable addresses every launch — but
  the throttle only suppresses **already-attempted** retries: a stop
  whose address was created or edited since the last attempt
  (`updatedAt > lastMapBackfillAt`) is always geocoded on the next open,
  so fresh input is never held back for up to a day.
- Camera auto-frames around the visible pins on first arrival (gated
  by a `rememberSaveable` flag so subsequent state ticks don't yank
  the camera back).
- **My-location button** — a bottom-right FAB centers the camera on the
  device's current fix (zoom 14). The Maps SDK's own button is disabled
  so there's a single control: it requests the location permission when
  it isn't held yet, shows an in-button spinner while the fix is
  fetched, and snackbars on a failed fix or a denied permission. The
  blue-dot "my location" layer (`isMyLocationEnabled`) renders whenever
  the permission is held.
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

A bottom-right my-location FAB mirrors the charging map's: it centers
the camera on the device's fix — requesting the location permission
first when needed, with an in-button spinner and a snackbar on a failed
fix — and the blue-dot layer (`isMyLocationEnabled`) shows whenever the
permission is held. When the picker opens with **no prior coordinates**
it seeds at a continent view and then, if the permission is already
held, jumps to the device's current location (only while the camera is
still at that zoomed-out default, so a user who has begun panning isn't
yanked away) — most manual picks are for the charger right in front of
you.

### Driving efficiency

A bottom-of-page collapsible card on the trip detail screen showing
measured km/kWh between consecutive charging sessions for the same
vehicle on the same trip.

- **Formula** (`util/EfficiencyAnalysis.kt`): for adjacent sessions A
  → B,
  `energy_used_kWh = (battery_end[A] − battery_start[B]) × capacity / 100`.
  Distance comes from `odometer[B] − odometer[A]`. Both sides are
  required, plus the vehicle's battery capacity, plus continuity: same
  non-null trip, or `continuesPrevious = true` on B. We **don't** fall
  back to "kWh delivered to A" — that isn't what the car used between
  stops.
- **Interleave guard** (sweep finding #6): trip-scoped analysis also
  receives the vehicle's full session timeline; a pair with an
  out-of-trip charge between its timestamps is excluded (the battery
  delta would be distorted by whatever that charge added) with a
  user-facing reason instead of silently producing a wrong leg. The
  `continuesPrevious` flag also self-heals (finding #7): deleting or
  moving the session it attested against clears it via `@Transaction`
  DAO methods.
- **Trip anchors** (July 2026): the trip's optional start/end battery %
  + odometer form virtual endpoints (`TripAnchor`, synthetic session
  ids −100/−101 rendered as "Trip start"/"Trip end") so the drive to
  the first charge and home from the last one produce legs. Both
  anchors with zero sessions = one whole-trip leg. Applied only when
  the trip's sessions are single-vehicle; same measurement rules and
  interleave protection as real pairs.
- **Reporting**: `EfficiencyReport` exposes measured legs and excluded
  pairs with user-facing reason strings. The UI surfaces both: average
  km/kWh + rows for unmeasurable legs so the user can see why a number
  is missing.
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

- **List**: rows show name, an optional date-range label (the trip's
  start/end dates), sessions, total $, energy, distance ($/km or $/mi
  based on pref). Rows carry `Modifier.animateItem()` so they glide to
  their new slot when a date edit re-sorts the list (newest first) or a
  trip is deleted. + FAB opens shared edit dialog.
- **Detail screen**: stats card (an optional date-range label in its
  header, sessions, total $, energy, distance, total charge time with
  missing-duration flag, average km/kWh from the efficiency analysis
  when available) + sessions list — or, when the trip has no sessions
  yet, a how-to empty state ("No sessions in this trip yet" with
  tag-from-the-Log instructions) — + driving efficiency card + pencil in
  top bar for edit dialog.
- **Edit dialog** (`TripEditDialog`, used for both create and edit):
  Name, optional Start/End **date** pickers (M3 `DatePicker` dialogs
  stored at local midnight — they label the trip and sort the list, with
  a "Clear dates" action and a start-after-end guard), Start/End
  odometer (unit-aware label, converts to km on save), optional
  Start/End battery % (feeds the trip-anchor drive legs), and Notes. The
  "Map pin color" button shows the current choice ("Map pin color:
  <name>", or "Auto") and opens a 5×2 swatch picker that also carries an
  **"Auto"** row — pick it to let the app assign the least-used color on
  save. When both start/end odo are filled, distance = `end − start`;
  otherwise distance falls back to spread of session odometer readings.
- **Auto-color**: a trip with no explicit color — a new trip, or an
  existing one reset to "Auto" in the picker — gets the least-used
  palette color on save; the trip's own row is excluded from the usage
  count so a re-pick spreads across the palette instead of counting
  itself.
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
- Cost and Energy trend — a segmented **"Last 12 months / All years"**
  window selector (`ChartWindowSelector`, backed by `StatsChartWindow`)
  sits above the two horizontal bar charts and drives both what they
  cover and their titles: "Cost by month" / "Energy by month" for the
  rolling 12-month window, "Cost by year" / "Energy by year" for the
  all-years window (year buckets zero-filled from the first data year,
  capped at 20). Bars are normalized to the largest bucket and animate
  into place, re-flowing when you flip months ↔ years. Cost uses the
  default-currency series; energy spans every currency.
- Top brands by spend (top 8) — each row is **tappable**: it grows a
  trailing chevron and, when tapped, jumps to the Log pre-filtered to
  that brand (`onOpenLogForBrand` → the log's `applyBrandDrilldown`,
  which swaps in a brand-only filter for the current vehicle scope). A
  "Tap a brand to see its sessions in the Log" subtitle advertises it.
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
  Below each grid a **Less → More** legend maps the alpha ramp to
  intensity, and cells are **tappable**: tapping a day strip selects the
  hour column under the finger (drawn with an outline) and prints its
  exact count below the legend ("Sat 5 pm–6 pm · 3 sessions"); tapping
  the same cell clears it. The card carries a "Rows are days, columns are
  hours — tap a square for its count" subtitle, and each grid also
  exposes a one-line TalkBack summary (total + busiest cell) in place of
  its 168 individually unreadable squares. Empty buckets hide their grid
  entirely.
- **Year recap entry** — a labeled **"Recap"** button in the Stats top
  bar (a `TextButton` with a `Summarize` icon + text, not a bare PDF
  glyph — the icon alone read as "some PDF button" in testing) carries
  the currently-selected `vehicleFilterId` into the `YearRecapScreen`
  (see below). The button is always present (an unconditional top-bar
  action, even before the first session is logged).
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

End-of-year-style recap reachable from the Stats top-bar "Recap" button (a labeled icon+text action, not a bare PDF glyph).
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
  - Charging map — an on-screen card plotting the year's distinct
    located stops (trip-colored like the live map, with a shared-stop
    gray and untripped green) and a route line per trip with 2+ located
    visits, over a bundled North America coastline, plus a color legend.
    Uses the same `RecapMapProjection` as the HTML export so both frame
    the map identically; hidden when no in-year session has coordinates.
  - Monthly cost — horizontal bars built with the shared
    `com.evsct.app.ui.BarList` composable. Past years show all 12
    months; the **current year trims to the elapsed months**
    (`recapMonthCount`) so, e.g., July's recap isn't padded with five
    empty rows (a future-dated session extends the window).
  - Top 8 brands by spend.
  - Longest trip (whole-trip distance from
    `TripRepository.observeAllWithStats()`, picked among trips with
    at least one session in the selected year — slight overclaim
    when a trip spans years, accepted for v1).
- **Export controls** at the bottom are a **PDF / HTML segmented
  picker** feeding a single **Save** and a single **Share** button (this
  replaced the earlier four-button stack); each shows an in-button
  spinner while its export runs, and the picker disables while busy so
  the running op always matches the selected format. Success reports via
  a snackbar, failure via a titled dialog. Save uses SAF `CreateDocument`
  (`application/pdf` or `text/html`); Share writes to `cacheDir/recap-
  share/` and fires `ACTION_SEND` through the existing FileProvider
  (cache path declared in `file_paths.xml`). Filenames go through
  `defaultRecapFilename(year, vehicleName, ext)` — `evsct-recap-2024.pdf`
  when scope is All, `evsct-recap-2024-Tesla-Model-3.pdf` when scoped,
  slugified; the HTML export uses the same names with a `.html`
  extension.
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
    and `tags` ride alongside. `receipts` evolved through three forms
    (handled by a single reader): an array of `{ file, originalName }`
    objects (current), an array of plain basename strings
    (intermediate), and the v5 legacy `receiptFile` single-receipt
    field — each older form is read into the new in-memory shape with
    `originalName = null` so old backups still restore cleanly. The
    writer always emits the current object form, plus a populated
    `receiptFile` field (set to the first receipt's basename) so an
    older build restoring a newer backup still recovers one
    attachment per session. v5 readers ignore unknown keys, and a
    future version bump can collapse all this into a "v6 added wait
    time + tags + multi-receipt + receipt names" entry without code
    changes. Pending decision; tracked in Outstanding ideas.
  - All receipt files in the zip are still flat `receipts/<uuid>.ext`;
    the new shape just lets one session reference several of them.
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
  `vehicle_name`, `latitude`, `longitude`, `continues_previous`,
  `wait_seconds`, and `tags` (older exports carried `wait_minutes`;
  import accepts both, converting minutes × 60; exports without either
  still import unchanged). Round-trips via `CsvIo`. Trips and
  vehicles are recreated by name on import. The multi-line parser
  (`Csv.parseAll`) tracks quoted state with lookahead escaped-quote
  handling — the earlier trailing-quote parity heuristic merged rows
  when a field's content began with a quote char.
  - **Save / Share parity with Full backup**: the CSV card has the
    same two buttons. **Export to CSV…** is the SAF
    `CreateDocument("text/csv")` flow. **Share CSV file…** routes
    through `CsvIo.prepareShareFile`, which writes the same CSV to
    `cacheDir/csv-share/` (clearing prior share files first) and the
    screen wraps it with FileProvider and fires `ACTION_SEND`. Both
    paths use the same private `writeCsvTo(Writer)` helper so the
    output can't drift. The shared `LaunchedEffect` on `pendingShareFile`
    picks the MIME type (`text/csv` vs `application/zip`) and chooser
    title from the file extension, so one effect handles both the CSV
    and the backup-zip share flows.
  - **Import atomicity**: `import()` wraps `deleteAll()` plus the per-
    row upsert loop in `database.withTransaction { … }`, so a killed
    process or single bad row after the wipe rolls back instead of
    leaving a half-populated database. Trip/vehicle name lookups are
    read outside the transaction (Flow `.first()`) to match the
    `BackupIo.restore` pattern.
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
  up in `BackupReminderScheduler`. The notifier mirrors the banner's
  never-backed-up rule (nudges once the user has ≥5 sessions and no
  recorded backup) — before the third-pass sweep it required a prior
  backup, so that cohort never got the OS notification at all.
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
  Theme-scoped now, not a single fixed set — `LightEvAccents` /
  `DarkEvAccents` in `Color.kt` re-tone each hue for its surface (the
  light trios washed out / seared on dark), and `EvsctTheme` provides
  the matching `EvAccentPalette` through the `LocalEvAccents`
  CompositionLocal; a screen picks a type's trio with
  `EvAccentPalette.forType(type)` (`ChargingTypeAccents.kt`).
- **Theme override** (`Settings → Theme`): segmented switch with
  System / Light / Dark. SYSTEM follows the OS dark-mode setting;
  LIGHT and DARK force the corresponding palette regardless of OS.
  Stored as `themeMode` in `AppPreferences`; read at the top of
  `MainActivity` and threaded into `EvsctTheme` so the override
  applies everywhere including dialogs.

## Settings screen

Cards (top to bottom):
1. **Vehicles** — entry to the vehicle list.
2. **Units & currency** — Distance segmented switch (Kilometres /
   Miles), Default currency segmented switch (CAD / USD), and a Time
   rate on cards segmented switch (Off / $/min / $/hr).
3. **Theme** — segmented switch (System / Light / Dark).
4. **Full backup** — Save backup file… / Share backup file… /
   Restore from backup…, plus a conditional Undo last restore or
   import… button that appears once a pre-restore snapshot exists.
5. **Backup reminder** — enable, threshold days field, Android
   notification toggle. Both switch rows are single whole-row toggle
   targets (`Modifier.toggleable` with `Role.Switch`; the `Switch`
   itself inert), so a tap anywhere on the row flips it and TalkBack
   announces one labeled switch.
6. **Backup (CSV)** — Export to CSV… / Share CSV file….
7. **Import (CSV)** — Import CSV with replace-existing toggle.
8. **One-time XLSX import** — for the legacy log.
9. **About** — running build version + git commit (the commit line
   links to the matching GitHub commit on clean builds), plus View on
   GitHub and Privacy-policy links.

Wrapped in `verticalScroll` so the bottom card never falls off (a
bug we hit early when the list was too tall for some screens).

## App-wide preferences

`AppPreferences` (DataStore) exposes:
- `lastBackupAt: Long?`
- `reminderSettings: Flow<BackupReminderSettings>` (enabled, threshold
  days, notify enabled)
- `userUnits: Flow<UserUnits>` (useMiles, defaultCurrency, cardTimeRate)
- `mapType: Flow<String>` (NORMAL / SATELLITE / HYBRID / TERRAIN)
- `mapClusteringEnabled: Flow<Boolean>` (default true)
- `mapHeatmapEnabled: Flow<Boolean>` (default false)
- `mapPolylinesEnabled: Flow<Boolean>` (default false)
- `mapColorByTrip: Flow<Boolean>` (default true)
- `themeMode: Flow<String>` (SYSTEM / LIGHT / DARK)
- `lastMapBackfillAt(): Long?` (one-shot read for the throttle)
- `trackedChargeSessionId(): Long?` + setter — mirrors the
  in-progress charge notifier's tracked session id across process
  death so the ongoing notification can still be cancelled (and the
  edit screen's live tracking resumed) after Android kills the app
  mid-charge
- `trackedChargeSessionIdFlow: Flow<Long?>` — reactive variant of the
  above so UI can react when tracking starts or ends (the
  stale-tracking nudge on the log)
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
- **`BarList`** — horizontal `[label] [bar] [value]` list shared by the
  Stats screen and the Year Recap. Bars normalize against the largest
  value, with a `MIN_BAR_FRACTION` floor so a small-but-real entry still
  paints a visible sliver next to a big outlier, and each fill animates
  into place (re-flowing when the data set changes, e.g. months ↔
  years). An optional `onRowClick` makes rows tappable and grows a
  trailing chevron (how Stats' brand drill-down advertises itself).
- **`MapTypeMenu`** — top-bar layer switcher reused by `MapScreen`
  and `MapPickerScreen`. Optional `heatmapEnabled` / `onToggleHeatmap`,
  `polylinesEnabled` / `onTogglePolylines`,
  `colorByTripEnabled` / `onToggleColorByTrip`, and
  `clusteringEnabled` / `onToggleClustering` callback pairs each surface
  a display-mode row beneath the basemap list (Heatmap, Trip routes,
  Color pins by trip, Cluster nearby pins) — how the map LOOKS lives
  here, while the filter sheet decides what SHOWS; the picker passes
  nulls for all four pairs and the rows hide. A `polylinesAvailable: Boolean`
  parameter (true by default) lets the screen gray the Trip-routes row
  out when heatmap mode owns the canvas.
- **`util/Tags.kt`** — comma-joined-string parsing for the new tags
  column. `parse` / `serialize` / `add` / `remove` / `sanitize` handle
  case-insensitive dedupe and strip the delimiter from user input so
  it can't break round-trip storage.

## Unit tests

`app/src/test/java/com/evsct/app/`:
- **`util/CurrencyTotalsTest.kt`** — single/mixed currency rendering,
  empty case, `singleCurrency` invariants, and free-session handling
  (zero costs don't open buckets or flip `isMixed`).
- **`util/DurationFormatTest.kt`** — bare-integer / colon / pretty
  parse forms, negative-input rejection, garbage rejection.
- **`util/EfficiencyAnalysisTest.kt`** — SoC formula correctness,
  exclusion reasons, `continuesPrevious` gating, capacity-missing
  case, decoupled-from-trip pairing.
- **`data/csv/CsvTest.kt`** — formula-injection escape on export,
  trigger-char stripping on import, asymmetric round-trip, and
  (third-pass sweep) leading-quote field content: rows must stay
  separate when a field's value starts with `"` or *is* a lone `"`,
  plus a multi-row encode→parseAll round-trip.
- **`util/FormatParseDecimalTest.kt`** — `Format.parseDecimal`
  accepts dot and comma decimal separators, treats comma+dot as
  thousands ("1,234.5"), trims whitespace, and returns null on
  blank/garbage. Extended in the 2026-07 sweep: European
  dot-thousands+comma-decimal, strict comma-grouping ("1,234" reads
  as thousands, "12,5" as a decimal).
- **`util/FormatLocaleTest.kt`** (2026-07 sweep) — number rendering is
  pinned to US separators regardless of device locale; checks run on
  fresh threads under de/fr/us defaults so the ThreadLocal formatters
  are created under the foreign locale.
- **`util/StopKeyTest.kt`** (sweep #12) — text stop keys, trim/case
  folding, station-name exclusion, and the geo-bucket fallback for
  coordinate-only sessions.
- **`util/OdometerDistanceTest.kt`** (sweep #15) — time-overlap
  proration: boundary intervals split across buckets, long gaps credit
  only their slice, adjacent windows tile to the exact total,
  per-vehicle walks.
- **`util/BrandSpendTest.kt`** (sweep #18) — case-insensitive brand
  merging, most-frequent-casing labels, non-positive-cost exclusion.
- **`data/csv/ImportSanitizerTest.kt`** (sweep #8/#16) — the import
  range gate (impossible battery/negative values nulled, refunds kept),
  the XLSX percent heuristic, and the time-of-day / duration cell
  interpreters (datetime-serial overflow case included).
- **`data/db/ChargingSessionDaoContinuityTest.kt`** (sweep #7) — the
  DAO's default `@Transaction` methods that clear a stale
  `continuesPrevious` when its attested predecessor is deleted or
  moved; runs against an in-memory fake implementing the abstract DAO
  methods.
- **`data/backup/BackupZipTest.kt`** (sweep #10) — the bounded zip
  scanner with in-memory zips and tiny caps: unknown-entry metering,
  data-carrying directory entries, duplicate basenames, zip-slip
  confinement.
- **`util/FormatThreadSafetyTest.kt`** — concurrent formatter use.
- **`util/EfficiencyAnalysisTest.kt`** also grew interleave-exclusion
  cases (sweep #6) and the trip-anchor suite (start/end/whole-trip
  legs, anchor interleave guards).

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
- **`SimpleDateFormat` and `DecimalFormat` are not thread-safe**:
  `Format` originally held shared static instances of both. The PDF
  generator runs on `Dispatchers.IO` and calls `Format.money/kwh/distance`
  concurrently with the UI thread — concurrent `.format()` can throw
  or silently produce garbled strings. Fix: switch dates to
  `DateTimeFormatter` (thread-safe by design, native since API 26 so
  no desugaring at minSdk 30), and wrap `DecimalFormat` in
  `ThreadLocal` so each thread gets its own instance. A JVM stress
  test (`FormatThreadSafetyTest`) hammers every formatter from N×4
  threads and asserts the output matches a single-threaded baseline —
  reliably fails against the old implementation, passes against the
  new one.

- **Geocoder's API-33 listener overloads are a SAM trap**: passing a
  lambda to `getFromLocationName` / `getFromLocation` implements only
  `GeocodeListener.onGeocode`; `onError` stays a default no-op, so a
  geocoder failure (no network, backend error — routine) never resumes
  a wrapping `suspendCancellableCoroutine` and the call hangs forever.
  Always implement the full listener and resume `onError` with an
  exception that matches the pre-33 synchronous contract.
- **Navigation calls during transitions corrupt the NavHost**: a
  `NavBackStackEntry` leaves RESUMED the moment its exit transition
  starts, and the incoming entry only reaches RESUMED when the
  transition settles. A tap landing mid-transition (double-tap on
  Save, or a tap hitting the incoming screen's control at the same
  coordinates — our repro was Save-then-Settings-gear, which line up
  exactly) fires `navigate()`/`popBackStack()` from an unsettled state
  and can blank the NavHost. Guard every navigation lambda with an
  is-RESUMED check (`dropUnlessResumed`, or a hand-rolled equivalent
  for lambdas with parameters).
- **A coroutine-completion-reset guard re-arms too early**: a
  `commitInFlight`-style flag cleared in `invokeOnCompletion` re-arms
  as soon as the save coroutine finishes — which is *before* the exit
  transition ends, so a late tap can start a second commit on a screen
  that's already leaving. Latch a separate never-reset flag once
  navigation-out has been requested; only failed commits leave it
  unset so retries still work.
- **Auto Backup `<include>` rules are exclusive — and Room runs WAL**:
  listing only `evsct.db` backs up a stale snapshot because committed
  writes sit in `evsct.db-wal` until a checkpoint (~4 MB or a clean
  close that never happens when Android kills the process). Include
  `-wal` and `-shm` alongside the db in `backup_rules.xml` and both
  `data_extraction_rules.xml` sections.
- **Kotlin's `String.format` uses the default locale, and
  `KeyboardType.Decimal` surfaces the locale's separator**: seeding a
  text field with `"%.1f".format(x)` renders "62,1" on comma-decimal
  devices while `toDoubleOrNull()` only accepts dots — so the value
  silently nulls on the next save. Pin field seeding to `Locale.US`
  and parse user decimals comma-tolerantly (`Format.parseDecimal`).
- **Work that must outlive a popped screen can't run in
  `viewModelScope`**: `popBackStack()` clears the ViewModel within the
  exit transition, cancelling in-flight coroutines — our post-save
  re-geocode almost never landed. Use the injected `@AppScope` scope
  for post-navigation side effects (the same scope `onCleared()`
  already used for file cleanup).
- **Validate input shape, not parsed values**: `toLongOrNull` accepts
  a sign and silently drops it for "-0", so a negativity check on the
  *parsed* number lets "-0:11:00" through. When rejecting malformed
  user input, test the raw string (digits-only) rather than properties
  of the parsed result — the parse can normalize away exactly the
  evidence you're checking for.

## Audit closeouts

Mid-project we ran an end-to-end bug audit (15 findings), a security
audit (M1 + M2 + Mn1–Mn5), and a later second-pass bug audit. In June
2026 a third-pass sweep reviewed every layer in parallel (22 findings:
5 high, 8 medium, 9 low) and closed out everything high and medium.
In July 2026 a fourth-pass pre-release sweep (`BUG_HUNT_REPORT.md`, 29
findings) closed out the entire report. A follow-on **UI/UX polish
sweep** (Phases 1–7) then reworked the app's surface for correctness,
feedback, navigation, motion, and accessibility. Highlights of what got
fixed and built (newest first):

- **UI/UX polish sweep** (July 2026; Phases 1-7, PRs #36-#41 on branch `claude/ui-ux-suggestions-otvkm1`):
  - **Phase 1 — correctness quick wins** (#36): real loading states so
    lists and detail screens no longer flash blank/empty on the way in
    (and a blank-insert save race is gone); CSV replace-import gained a
    confirm dialog + an automatic safety snapshot; filter and edit sheets
    scroll instead of clipping their bottoms; the filtered-empty state
    reads honestly ("no matches" vs "no data"); the map-picker pin tip
    aligns with the true target; Map/Trips bottom icons swapped to match
    their labels.
  - **Phase 2 — form ergonomics** (#37): IME Next/Done focus chains walk
    the fields in order; an unsaved-changes discard guard catches Back
    mid-edit; a pending tag draft commits on save instead of vanishing;
    FilterChip rows stopped clipping their selected checkmarks; the
    odometer dialog grabs focus on open.
  - **Phase 3 — bottom navigation** (#38): a Log/Map/Stats/Trips bottom
    bar with save/restore-state tab switches (each tab keeps its scroll/
    camera/filter state) and predictive back opted in via
    `enableOnBackInvokedCallback`.
  - **Phase 4 — feedback & undo** (#39): undo-after-delete for single and
    bulk deletes via an app-scoped `DeletedSessionUndoHolder` + a
    count-aware snackbar; bulk delete from multi-select; per-op on-button
    progress; honest snackbar-success / dialog-failure feedback in
    Settings and the Year recap; haptics on destructive confirms.
  - **Phase 5 — stats & recap depth** (#40): tappable brand rows drill
    into the Log pre-filtered to that brand; a Last-12-months / All-years
    chart-window selector; a multi-currency `MoneyStat` headline; a
    Less → More heatmap legend with tappable cells; an on-screen recap
    map preview sharing `RecapMapProjection` with the HTML export; a
    2-button (not 4) PDF/HTML export picker; current-year month trimming;
    theme-aware (dark-mode) charging-type accents; a shared `BarList`
    with a minimum bar width.
  - **Phase 6 — map & trips** (#41, part 1): trip start/end date pickers
    with date labels; a pin-color Auto option + swatch a11y; a
    trip-detail empty state; a my-location button and dot on the map and
    picker; picker location seeding; an on-map trip legend;
    Layers/Filters regrouping (colorByTrip + clustering moved under a
    Layers menu) with colorByTrip persistence; a geocode-failure snackbar.
  - **Phase 7 — accessibility & motion** (#41, part 2): sticky month
    headers; a back-to-top FAB; an extended Add FAB; `animateItem` on
    lists; animated chart bars; a sliding bottom bar; single-target
    toggleable switch rows; chart/heatmap/selection TalkBack semantics.
  - **On-device testing by the owner** surfaced real fixes that review
    alone missed: phantom checkmarks on selected chips (now rendered
    explicitly); a bulk delete that lacked the single-delete Undo offer;
    a brand drill-down that computed the filter but never applied it to
    the Log; three color choices invisible in one theme (recap-map
    coastlines in dark, zero-count heatmap cells and bar tracks in light);
    a throttled geocoder that deferred fresh/edited addresses to the
    next-day backfill (the 24h throttle now only suppresses
    already-attempted retries); and a ghost "Session deleted" snackbar
    left behind on leaving the log (the unresolved undo offer is now
    forfeited on exit).
  - **Verification pattern** (matched the fourth-pass sweep): Gradle
    couldn't run in the cloud container (distribution download blocked),
    so CI served as the first real compile and every change was
    verified by review against the source; the owner's on-device testing
    then caught the runtime-only regressions above that no static review
    would have surfaced.

- **Fourth-pass pre-release sweep** (July 2026; all 29 findings
  resolved across PRs #31–#34 on branch `claude/evsct-bug-hunt-faxgag`;
  the full report with per-finding detail lives in
  `BUG_HUNT_REPORT.md` at the repo root):
  - **Parsing/units** (#1–#2): `parseDecimal` handles comma decimals
    and strict thousands-grouping; miles-mode display↔km round-trips
    stopped drifting stored values.
  - **Room hardening** (#3): destructive-migration fallback removed
    (crash-on-downgrade is recoverable, a wipe is not); schema export
    enabled, baselines committed (`app/schemas/`).
  - **Backup/restore** (#4, #5, #10, #14, #23, #26): exports stage
    locally so failures can't truncate the old file; restore refuses
    damaged/inconsistent backups outright with human-locatable errors
    ("entry 2 of 312 in sessions (id 238)"); every zip entry is
    metered during restore (unknown names, data-carrying directory
    entries, dropped duplicates — the skip-inflation zip-bomb bypass);
    shared receipt basenames no longer crash export or delete files
    out from under sibling rows; "backed up" is recorded only when a
    share target is actually picked (chosen-component IntentSender +
    `BackupShareChosenReceiver`); the 9→10 migration skips blank
    receipt paths.
  - **Efficiency correctness** (#6, #7): interleaved out-of-trip
    charges exclude a pair instead of silently distorting it;
    `continuesPrevious` self-heals when its attested predecessor is
    deleted or moved (DAO `@Transaction` default methods).
  - **Import gating** (#8, #16, #25): `ImportSanitizer` nulls
    physically impossible CSV/XLSX values at the boundary (negative
    cost deliberately survives as a refund); the XLSX battery scale
    reads the cell's %-format instead of an unconditional ×100; a
    datetime serial in a time cell no longer overflows Int into
    decades-old timestamps; CSV export failure reports failure.
  - **CSV fidelity** (#9, #17): plain negative numbers export without
    the formula-defusing apostrophe (longitude arrives numeric in
    Excel; the decoder still strips old exports); trips/vehicles match
    case-insensitively on import.
  - **UI state traps** (#11, #13, #24, #29): the `exitRequested`
    latch re-arms via ON_RESUME when a post-save pop is dropped
    (recovered re-saves update instead of duplicating); map-pick
    sibling propagation deferred to save, address-keyed, and announced
    up front; the notification deep link survives task restore after
    process death (process-scoped flag + tracked-charge gate); stale
    quick-tracked charges get a "Still charging?" banner after 12 h.
  - **Display/aggregation consistency** (#12, #15, #18, #20, #27):
    coordinate-only sessions render on both maps (shared `StopKey`
    geo-bucket fallback); Stats and Recap share one odometer-distance
    walk with time-overlap proration (shared `OdometerDistance`);
    brand spend merges case variants (shared `BrandSpend`); live
    tracking stopped rewriting DataStore per keystroke; number
    rendering pinned to US separators (no "$1.234,56 CAD" hybrids).
  - **Hygiene** (#19, #21, #22): dead list-level delete paths removed
    (one would have leaked receipt files); session upsert + receipt
    reconciliation are one transaction; `MissingMediaSweeper` drops DB
    references to media a cloud auto-restore never carried.
  - **No-action** (#28): double-precision money drift measured at
    ~1e-9 across tens of thousands of amounts — informational only.
  - **Features that grew out of the sweep**: trip start/end battery %
    anchors (schema v12) for first/last-leg efficiency; pre-restore
    safety snapshot + "Undo last restore" (undo is itself undoable);
    "Last backed up" line and Android-auto-backup caveat in Settings;
    log-scaled heatmap weights; $/min ⇄ $/hr posted-rate entry toggle
    (storage stays canonical $/min).
  - **Verification pattern**: Gradle couldn't run in the cloud
    container (distribution download blocked), so every logic change
    was verified with standalone kotlinc harnesses against the real
    source files (100+ checks across the sweep; the harnesses caught
    two real bugs pre-commit), mirrored into the JUnit suites listed
    under "Unit tests" for local runs.

- **Third-pass bug sweep** (June 2026; all 22 findings are fixed and
  merged to `main` — the 5 high + 8 medium batches landed via PR #22
  (branch `claude/adoring-hopper-57uot7`, which also carried the
  navigation-transition fix), and the 9 low findings via PR #23
  (branch `claude/low-severity-sweep-fixes`) — see the low-severity
  batch at the end of this list):
  - **`Csv.parseAll` merged rows when a field's content started with a
    quote char**: the cross-line quote tracker counted a field's
    *opening* quote into its trailing-run parity check, so a note like
    `"broken stall` desynced the quoted state, swallowed the
    row-terminating newline, and corrupted the import (data loss under
    "replace existing"). Rewritten with lookahead-based escaped-quote
    handling; covered by three new `CsvTest` cases and verified
    against 20k randomized encode→parse round-trips.
  - **Geocoder calls could hang forever on Android 13+**: the
    TIRAMISU+ paths passed a lambda, which SAM-implements only
    `GeocodeListener.onGeocode` — `onError` stayed a default no-op and
    any geocoder failure left the `suspendCancellableCoroutine`
    suspended forever (GPS autofill spinner of doom). `onError` now
    resumes with `IOException`, matching the pre-33 contract every
    caller already catches.
  - **Double-tap on Save inserted duplicate rows**: no re-entry guard
    existed anywhere in the save path. Added `commitInFlight` (reset
    via `invokeOnCompletion`) plus an `exitRequested` latch to
    `SessionEditViewModel` and `VehicleEditViewModel` — the latch
    matters because the save coroutine can finish (re-arming the
    in-flight flag) before the exit transition does. `MapPickerScreen`
    got a fire-once guard on its three exit buttons.
  - **Mid-transition taps corrupted the NavHost into a blank screen**:
    found in on-device testing — save a session, then immediately tap
    where the list's Settings gear sits (same coordinates as the edit
    screen's Save checkmark) and `navigate()` fired during the pop
    transition, blanking the app. Every navigation lambda in
    `EvsctNavGraph` is now wrapped in an `ifResumed` guard on its
    `NavBackStackEntry` (the `dropUnlessResumed` pattern generalized
    to any lambda arity), so taps landing during enter/exit
    transitions are dropped.
  - **Android Auto Backup silently rolled restores back**: the backup
    rules included only `evsct.db`, but Room defaults to WAL mode, so
    recent commits live in `evsct.db-wal` until a checkpoint that may
    never happen before process death. Both rule files now include
    `evsct.db-wal` and `evsct.db-shm` (the in-app zip backup was never
    affected — it serializes through DAOs).
  - **Comma-decimal locales silently wiped values**: field seeding
    used default-locale `"%.1f".format(...)` (→ "62,1" on fr/de/es
    devices) while every parse was dot-only `toDoubleOrNull()`, so
    editing an odometer — or even saving a trip rename — nulled stored
    values. Added `Format.parseDecimal` (accepts comma decimal
    separators; comma+dot treated as thousands), switched all UI
    decimal parse sites to it, pinned field seeding to `Locale.US`,
    and gave `TripEditDialog` the same untouched-field short-circuit
    the session form already had (also killing its lossy km↔mi
    drift on no-op saves). New `FormatParseDecimalTest`.
  - **Charge tracking was dead on Android 13+ without notification
    permission**: `InProgressChargeNotifier.post()` returned before
    recording the tracked id, so the in-app features (live elapsed
    chip, elapsed-time fallback into the duration on save) died with
    the denied permission — which nothing in the quick-track flow ever
    requested. Now tracks first / notifies second, and the
    Start-charge flow requests `POST_NOTIFICATIONS` (proceeding
    regardless of the answer).
  - **Process death orphaned the ongoing notification**: the tracked
    id lived only in memory while every cancel path was conditional
    (`cancelIfFor`), so kill-app-mid-charge left the `setOngoing`
    entry stuck in the shade. The id is now mirrored to DataStore
    (`trackedChargeSessionId`) and restored best-effort at next start.
  - **Bulk "Assign trip" applied to invisibly-stale selections**: the
    top bar displayed `selected ∩ visible` but the action used the raw
    set, so rows hidden by a search/filter applied after selecting got
    silently reassigned. The action now uses the displayed set.
  - **CSV silently dropped `waitTimeMinutes` and `tags`**: the format
    was never updated for the two newer fields. Added `wait_minutes`
    and `tags` columns; older exports import unchanged (absent columns
    read null).
  - **Backup reminder never fired for never-backed-up users**: the
    scheduler explicitly armed a check for that cohort but the
    notifier unconditionally required a prior backup, so the worker
    chain re-armed forever without posting. The notifier now mirrors
    the in-app banner's rule (≥ `BACKUP_NUDGE_MIN_SESSIONS`).
  - **Post-save re-geocode almost never landed**: it ran in
    `viewModelScope` *after* `popBackStack()`, so the VM clear
    cancelled it mid-flight and edited addresses lost their pin until
    the 24h-throttled map backfill. Moved to the `@AppScope` scope.
  - **Fast save after a map pick dropped the picked coordinates**:
    `applyPickedLocation` only wrote lat/lng to form state after the
    reverse-geocode returned. Coordinates now apply synchronously;
    only the address backfill stays async.
  - **Edits overwrote `createdAt`**: both edit ViewModels rebuilt the
    entity from form state, stamping the data-class "now" default
    through every update (corrupting creation dates, which round-trip
    into backups). Both now carry the loaded row's `createdAt`.
  - **Low-severity batch** (follow-up branch):
    - Free ($0.00) sessions no longer open currency buckets in
      `CurrencyTotals.from` — one free USD-tagged row used to flip
      `isMixed` and suppress $/kWh / $/km for an all-CAD log,
      contradicting the class KDoc. Tests added.
    - `AutofillResult.NoProvider` is reachable: `fetch()` resolves the
      provider itself, so location-services-off shows "turn on
      location" instead of "couldn't get a fix". `FUSED_PROVIDER` is
      also only preferred on API 31+ — undocumented pre-S, it could
      appear enabled on Android 11 yet never compute a fix.
    - `DurationFormat.parse` rejects negative inputs ("-5", "-1:30");
      new `DurationFormatTest`. The first cut checked the *parsed*
      values for `< 0` and shipped with a hole the test caught on a
      real run: `"-0".toLongOrNull()` drops the sign and returns 0,
      letting "-0:11:00" through. The final fix requires colon parts
      and the bare-minutes form to be pure digits.
    - `LocationAutofill.geocode`'s country-qualified retry no longer
      clobbers step-1 candidates with an empty list, so the
      documented last-resort fallback actually fires.
    - `CsvFormat` date/time moved from shared `SimpleDateFormat`s
      (not thread-safe; timezone frozen at class load) to
      `DateTimeFormatter` with a per-call zone. The parse pattern
      accepts unpadded hand-edited values ("2024-1-5 9:30:00");
      strict resolution now rejects impossible dates as malformed
      rows instead of silently rolling them over.
    - `CsvIo.import` no longer collects a DAO Flow inside
      `withTransaction`: pin colors are pre-read outside and assigned
      explicitly, which also gives several new trips in one import
      distinct colors instead of duplicates.
    - The git-info build script honors its documented "unknown"
      fallback when the `git` binary is missing (`runCatching` at the
      `.get()` sites — `Provider.orElse` can't catch exec failures).
    - `XlsxImportResult`'s never-populated `errors` field removed.

- **Second-pass bug audit** (post-AGP-9, after the docs settled):
  - **CSV import was non-atomic**: `import()` did `deleteAll()` then
    upserted row-by-row outside any transaction. A process kill, single
    bad row, or storage-full mid-loop left the database half-wiped with
    no rollback. Wrapped the destructive part in
    `database.withTransaction { … }`; trip/vehicle name lookups still
    read outside the transaction to match `BackupIo.restore`.
  - **`Format` was not thread-safe**: shared static `SimpleDateFormat` /
    `DecimalFormat` instances raced between the UI thread and the
    `Dispatchers.IO`-bound PDF generator. Switched dates to
    `DateTimeFormatter` (thread-safe) and wrapped `DecimalFormat` in
    `ThreadLocal`. Added `FormatThreadSafetyTest` as a regression guard.
  - **XLSX battery percentage truncated instead of rounding**:
    `(it * 100).toInt()` turned `0.856` into `85` instead of `86`,
    losing up to a percentage point per imported field. Switched to
    `.roundToInt()`.
  - **`YearRecapPdf` could leak the native `PdfDocument`**: `close()`
    only ran on a clean success. Wrapped the entire render in
    `try { … } finally { doc.close() }` so the document always closes.
  - **`BackupIo.extractInto` could clobber files on duplicate basenames**:
    two zip entries reducing to the same basename used to overwrite
    each other in the temp dir — and corrupted the first if the second
    errored mid-write. The DB only references one file per basename, so
    keep the first occurrence and skip later duplicates.
  - **Date filter accepted `from > to` with no feedback**: picking
    `From = today, To = yesterday` made the predicate impossible to
    satisfy and the list silently went empty. Added inline error text
    and disabled the Apply button when the range is inverted.


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

- ~~Per-vehicle lifetime stats by month / year (rolling charts).~~ — **shipped** (Phase 5): the Stats tab scopes to a single vehicle via the All / per-vehicle tab row, and a **Last 12 months / All years** window selector above the cost/energy charts drives them as rolling per-month buckets or per-year buckets across every year with data.
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
- ~~Bump backup `SCHEMA_VERSION` past 5~~ — **decided against**
  (2026-07 sweep): we touched `BackupIo.kt` repeatedly (findings #4,
  #5, #10, #14, #23, trip battery anchors, pre-restore snapshot) and
  deliberately kept `schemaVersion = 5` each time. All post-v5 fields
  are additive and optional; bumping would make older installs refuse
  newer backups outright, which costs real users (restore-on-old-build
  after a bad update) and buys only a version-marker convention.
  Revisit only if a future format change is genuinely breaking.
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
  `claude/get-started-AEDLP`, which has since been merged. The
  third-pass bug sweep landed through `claude/adoring-hopper-57uot7`
  (PR #22) and `claude/low-severity-sweep-fixes` (PR #23), both
  merged. The fourth-pass sweep reused one branch
  (`claude/evsct-bug-hunt-faxgag`) restarted from `main` between
  batches: PRs #31 (#1–#5), #32 (restore error messages), #33
  (#6–#17 + features), #34 (#18–#29 + rate toggle). Everything lives
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
