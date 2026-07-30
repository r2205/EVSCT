<p align="center">
  <img src="docs/branding/evsct-banner.png" alt="EVSCT — EV Session & Charging Tracker" width="540">
</p>

# EVSCT — EV Session & Charging Tracker

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![minSdk](https://img.shields.io/badge/minSdk-30-green.svg)](#)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF.svg?logo=kotlin&logoColor=white)](#)

Personal Android app for logging EV charging sessions (DC fast on road trips,
AC at home/hotels). Stores everything locally; nothing leaves the phone unless
you hit Export. Built with Kotlin + Jetpack Compose + Room + Hilt.

## Screenshots

_Shown in dark mode — EVSCT also ships a hand-tuned light theme (see [Theming](#theming))._

<!--
  Two kinds of shots (stored as lossless WebP):
   1. The grid below — one single-screen thumbnail per screen, sized to 200 px
      here, so you don't need to resize the source. Cropping the status/nav
      bar looks cleaner. Remove a <td> for any screen you skip.
   2. The "Full-length captures" expanders — for screens that scroll past one
      screenful, an extended/long screenshot named "<screen>-full.webp".
      It only shows on click, at 320 px wide so the detail stays legible.
  See docs/screenshots/README.md for the full filename list.
-->

<table>
  <tr>
    <td align="center" width="25%"><img src="docs/screenshots/log.webp" alt="Charging log" width="200"><br><sub><b>Charging log</b></sub></td>
    <td align="center" width="25%"><img src="docs/screenshots/entry.webp" alt="Add a session" width="200"><br><sub><b>Add a session</b></sub></td>
    <td align="center" width="25%"><img src="docs/screenshots/map.webp" alt="Map view" width="200"><br><sub><b>Map</b></sub></td>
    <td align="center" width="25%"><img src="docs/screenshots/stats.webp" alt="Stats" width="200"><br><sub><b>Stats</b></sub></td>
  </tr>
  <tr>
    <td align="center" width="25%"><img src="docs/screenshots/vehicle.webp" alt="Vehicle detail" width="200"><br><sub><b>Vehicle detail</b></sub></td>
    <td align="center" width="25%"><img src="docs/screenshots/trips.webp" alt="Trips" width="200"><br><sub><b>Trips</b></sub></td>
    <td align="center" width="25%"><img src="docs/screenshots/recap.webp" alt="Year recap" width="200"><br><sub><b>Year recap</b></sub></td>
    <td align="center" width="25%"><img src="docs/screenshots/settings.webp" alt="Settings" width="200"><br><sub><b>Settings</b></sub></td>
  </tr>
</table>

**Full-length captures** — some screens scroll past one screenful; tap to expand the complete shot:

<details><summary>Add a session (full form)</summary><br><img src="docs/screenshots/entry-full.webp" alt="Add a session — full form" width="320"></details>

<details><summary>Stats (full screen)</summary><br><img src="docs/screenshots/stats-full.webp" alt="Stats — full screen" width="320"></details>

<details><summary>Vehicle detail (full screen)</summary><br><img src="docs/screenshots/vehicle-full.webp" alt="Vehicle detail — full screen" width="320"></details>

<details><summary>Year recap (full screen)</summary><br><img src="docs/screenshots/recap-full.webp" alt="Year recap — full screen" width="320"></details>

<details><summary>Settings (full screen)</summary><br><img src="docs/screenshots/settings-full.webp" alt="Settings — full screen" width="320"></details>

## Features

### Logging a session
- Date/time, charging type (DC Fast / AC L2 / AC L1), and pricing model
  (per-kWh, per-minute, flat, free, hybrid).
- **Track a charge live** — "Start charge" logs the session the moment
  you plug in and posts a persistent "Charging in progress" notification
  with a running stopwatch; tap it to jump back to the entry and fill in
  cost / kWh when you unplug. Save without typing a duration and the
  tracked elapsed time fills it in automatically. (Android 13+ asks for
  notification permission the first time; declining only hides the shade
  shortcut — tracking still works in-app.) If a tracked charge is ever
  left running 12+ hours, the log shows a gentle **"Still charging?"**
  banner that jumps straight to the session so you can finish or delete
  it — nothing expires or deletes automatically.
- Odometer, energy delivered (kWh), total cost, charging duration, battery
  start/end %.
- Optional **wait time** — how long you queued before the cable plugged
  in, entered the same way as charging duration and kept to the second.
  Doesn't affect kWh / cost; just lets the row show "1h 23m +10m 00s
  wait" so total stop time stays distinct from charge time.
- Per-session **currency chip** (CAD / USD) — defaults to your preferred
  currency, but a US road-trip session can be tagged USD even when your
  default is CAD.
- Posted vs. effective rates ($/kWh, time rate, max kW) so you can see
  when a station charges differently than advertised. The time rate has
  a **$/min ⇄ $/hr toggle** — enter whichever unit the station
  advertises and flipping the toggle converts the value in place.
- **Free-form tags** — type "work charge", "winter test", "kid's hockey
  trip"; press Enter or comma to commit. Tags appear as `#pill` chips on
  the row and become a filter chip in the log's Filter sheet.
- Notes and **multiple receipts** — any mix of photos (with tap-to-
  fullscreen + pinch-to-zoom) and PDFs (open in your system PDF
  viewer). A small "Photo / PDF" chooser appears when you tap "Add
  another"; useful when a station gives you a transaction receipt
  and the car's app shows a separate kWh / session summary. PDFs
  display their original filename on the tile (e.g.
  `expense-aug-2025.pdf`); a per-tile **Rename** button lets you
  fix that label for any receipt — handy for older attachments that
  never captured a name. Removing a tile is deferred until you save,
  so a wrong tap is recoverable by tapping Back.

### Smart entry helpers
- **Brand picker** — curated North-American networks (Tesla, Electrify
  America/Canada, ChargePoint, EVgo, FLO, BC Hydro, Ivy, Rivian, IONNA,
  etc.) plus free-text entry. Sorted by your own usage history.
- **Use a recent stop…** — pre-fills brand, city, prov/state, address, and
  station name from a previous visit.
- **GPS autofill** — one-tap reverse-geocoding for city/prov/address,
  also stores the coordinates so the session shows up on the map.
- **Province / state normalization** — typing `Saskatchewan` or `Quebec`
  lands as `SK` / `QC`; covers all CA provinces and US states.
- **Smart duration entry** — `25` becomes `0h 25m 0s`, `32:14` becomes
  `0h 32m 14s`, `1:25:00` stays exact, and a live `= …` preview under the
  field shows how the entry is read while you type. Phone-pad keyboard
  with an inline `:` insert button.
- **Data-validation hints** — gentle amber card at the top of the form when
  something looks off (odometer went backward, effective price wildly
  differs from posted, battery decreased during charge, etc.). Affected
  fields turn red so the offender is obvious.
- **Empty odometer warning** — confirmation prompt before saving without an
  odometer reading.

### The Charging log
- Cards per session with a colored leading bar (amber DC fast, blue AC L2,
  purple AC L1), brand, city, cost in primary green, eff. $/kWh, vehicle and
  trip pills, a receipt icon when a photo or PDF is attached, and a row of
  `#tag` chips when the session is tagged.
- **Add a charge** — an extended **Add session** button floats over the
  list and collapses to a bare `+` once you scroll. It opens a chooser to
  either track a charge live (a running timer plus a persistent
  notification) or backfill a charge you've already finished.
- **Vehicle tabs** — filter to a specific car, or All. New sessions started
  from a vehicle tab pre-select that vehicle.
- **Search** — free-text matching brand, city, prov, address, station,
  notes, and tag names.
- **Filter sheet** — by brand, by date range with quick presets (This
  month / Last 3 mo. / Last year / custom from-to via date picker), and by
  **tags** (multi-select chips, OR semantics, case-insensitive).
- **Sort** — small dropdown in the top bar with Date (newest, default),
  Cost (highest), Efficiency (cheapest $/kWh first), or Brand (A–Z).
  Date breaks ties on every option, and sessions missing the chosen
  field fall to the end.
- **Month headers** — sorted by date, the list buckets into months (e.g.
  "July 2026") under sticky headers that pin to the top edge as rows scroll
  beneath them. Only in date order — the cost, efficiency, and brand sorts
  interleave months, so the headers drop away.
- **Multi-select** — long-press a row, or tap the **Select** icon in the
  top bar, to enter selection mode; **Select all** grabs every row on
  screen. From the selection bar you can assign the chosen sessions to a
  trip, or delete them in bulk — a confirmation dialog first, then an Undo
  snackbar so a mistaken batch is reversible.
- Top bar entries: Search · **Sort** · **Select** · Settings. Log / Map / Stats /
  Trips switch via the **bottom navigation bar**, which preserves each
  tab's state (scroll position, camera, filters) across switches.
- **Undo delete** — removing sessions (one from its edit screen, or a whole
  multi-select batch) raises a "Session deleted" snackbar on the log with an
  **Undo** action that restores the rows and their receipt files. The offer
  lives on the log: leave the tab and the delete is committed.
- **Back to top** — once you've scrolled well down the log, a small
  up-arrow button appears and flings you back to the newest session in one
  tap.
- **Animated rows** — rows glide into place when a delete, undo, or re-sort
  moves them, instead of snapping to the new position.

### Map view
- Google Maps full-screen view with **one pin per distinct charging stop**
  (deduped by brand + address + city; stops with only GPS coordinates
  group by location instead of vanishing). Tap a pin for brand, address,
  and visit count.
- **My location** — a floating button (bottom-right) recenters the map on
  where you are, showing a spinner while it fetches a fix and requesting
  the location permission the first time you tap it (a snackbar explains
  if the permission or fix fails). While the permission is held the
  standard blue location dot renders on the map.
- **Trip-colored pins** — each trip gets a color from a 10-swatch palette,
  auto-assigned but customizable in the trip edit dialog. Stops visited
  across multiple trips render as a neutral gray "shared" pin so the
  visual stays honest.
- **On-map trip legend** — while trip colors are on, a compact card
  (bottom-left) lists each visible trip's color and name (plus
  "Untripped", and the gray "Multiple trips" swatch when a shared stop is
  showing), capped at six rows with a "+N more" line. Tap it to open the
  filter sheet and toggle those same trips. It hides when trip colors are
  off, the heatmap owns the canvas, or no colored trip is visible.
- **Filter sheet** — decides *which* stops show, not how the map looks
  (that moved to the Layers menu): scope the map to a single vehicle
  (chip row appears when you have ≥2 vehicles), hide individual trips and
  the untripped bucket with a checkbox list, or use **Show all** / **Hide
  all** to flip the trip selection in one tap when the trip list grows
  long. **Reset** clears every active filter at once.
- **Layers menu** — basemap switcher (Default / Satellite / Hybrid /
  Terrain) plus four display-mode toggles:
  - **Heatmap** — pins are replaced by a density overlay weighted by
    visit count (log-scaled), so your everyday home charger glows
    brightest without washing one-off road-trip stops off the map.
  - **Trip routes** — draws a colored polyline for each trip connecting
    its sessions in chronological order, using the trip's pin color.
    Lines are geodesic so cross-country routes curve naturally instead
    of looking like flat-Earth shortcuts. Suppressed (and grayed in the
    menu) while heatmap mode owns the canvas.
  - **Color pins by trip** — on by default; turn it off to drop the trip
    palette and render every pin in the default red. The choice persists
    across app restarts.
  - **Cluster nearby pins** — on by default; turn it off to render every
    pin individually regardless of zoom instead of folding nearby stops
    into a numbered count badge.
- **Tap a pin to drill in** — single-session pins still show the Maps
  tooltip (brand · address · "1 visit"); tap the tooltip to jump to
  that session's edit screen. Multi-session pins skip the tooltip and
  open a bottom sheet listing each session's date, energy, trip badge
  (or "Untripped"), and vehicle name; tap a row to open it. Useful
  for figuring out which session at a frequently-visited stop is the
  one you need to fix.
- **Address backfill** — every time you open the map, any stop with a
  textual address but no coordinates is reverse-geocoded and the resolved
  point saved back to those sessions so it appears as a pin. Once an
  address has been tried the pass is throttled to at most once a day, but
  a stop whose address was **added or edited since the last attempt** is
  always re-geocoded on the next open. Addresses that still can't be
  located raise a snackbar ("N addresses couldn't be located — those
  stops stay off the map"), pointing you to fix the address or open the
  session and use *Pick on map*.

### Vehicles
- Year, make, model, trim, battery capacity (kWh), nominal range
  (in your preferred distance unit), VIN, notes, and a profile photo.
- Default vehicle pre-selects on new sessions.
- **Per-vehicle detail screen** — lifetime stats (sessions, total cost,
  total energy, total distance, $/km or $/mi, $/kWh, avg power, top brand)
  plus highlight cards for fastest charge, cheapest $/kWh, most expensive
  $/kWh, and last charged. Recent sessions for the vehicle listed at the
  bottom.

### Trips
- Manual trip tagging (one trip per session, optional).
- Optional **start/end dates** per trip, chosen with date pickers in the
  trip editor. When set, they label the trip on its list row and detail
  header and sort the Trips list by start date (newest first).
- Optional start/end odometer per trip — when both are filled, distance =
  end − start. Otherwise distance is inferred from session odometer
  readings.
- Optional **start/end battery %** per trip — together with the odometer
  readings these anchor the efficiency legs no session pair can measure:
  the drive from home (charged to 100%) to your first stop, and the
  drive home from your last one.
- **Map pin color** picker per trip — ten color swatches plus an explicit
  **Auto** choice (the default), which lets the app assign the least-used
  color when the trip is saved.
- Trip detail shows total cost, energy, distance, $/km or $/mi, $/kWh,
  plus every session in the trip and a **driving efficiency** card with
  measured km/kWh per leg (and the reason whenever a leg can't be
  measured). A trip with no sessions yet shows a how-to empty state
  explaining how to tag sessions to it.

### Stats
- Headline card: sessions, total cost (one line per currency you've paid
  in), total energy, average effective $/kWh, average power.
- **vs gas this month** card — compares this month's charging cost to
  what an equivalent distance of driving would have cost in gas. Shows a
  big "Saved $X" headline with the underlying numbers below.
- Cost and energy trend charts with a **Last 12 months / All years**
  segmented selector above them; the charts and their titles ("Cost by
  month" vs "Cost by year") follow the window you choose.
- Top brands by total spend — **tap a brand row to jump to the Charging
  log, pre-filtered to that brand.**
- Charging-type split (DC Fast / AC L2 / AC L1) with percentages.
- **When you charge** — two 7×24 day-of-week × hour-of-day heatmaps,
  one for DC Fast and one for AC, so you can see road-trip patterns
  separately from home/commute charging. Each shows its peak day/hour
  ("Peak: Sat 2 pm"), a **Less → More** shading legend, and **tappable
  cells** — tap any square to read its exact session count.
- Vehicle filter mirrors the Charging log tabs.
- **Year recap** — tap the **Recap** button in the top bar to open a
  year-end recap (any year you pick): headline totals, an on-screen
  **charging map** (that year's stops and trip routes over a bundled
  North America outline, colored by trip), monthly trend, top brands,
  longest trip. (A current-year recap trims the monthly trend to the
  months elapsed so far.) A **PDF / HTML** segmented picker chooses the
  format, then one **Save** and one **Share** button export it — either
  a single-page PDF, or a self-contained **HTML** report: a richer,
  responsive page (full monthly cost+energy table, a Cost/Energy chart
  toggle, and the same charging map). The HTML opens offline in any
  browser with no external resources, so nothing leaves the phone.
  When you open it from a specific vehicle's tab, the recap and its
  exports are scoped to that vehicle (and the filename includes its name).

### Settings
- **Vehicles** — manage your EVs and pick a default for new sessions.
- **Units & currency** — Kilometres / Miles segmented switch, a CAD / USD
  default-currency switch, and a **Time rate on cards** switch (Off /
  $/min / $/hr) that shows each charge's cost per minute or hour on its
  card. Distances are stored canonically in km; the switch only affects
  display + form labels. Each session keeps the currency it was saved
  with — the default just seeds new sessions and tags totals on the
  dashboards.
- **Theme** — System / Light / Dark segmented switch. System follows your
  phone's dark-mode setting; Light and Dark force the corresponding
  palette.
- **Full backup** — **Save** a `.zip` to a folder you pick, **Share** it
  out via Drive / email / Messages / etc., or **Restore** from a backup
  zip. The card shows **when you last backed up**, and every restore or replace-import
  automatically snapshots your current data first — an **Undo last
  restore or import** button brings it back if you restored or imported the
  wrong file (and
  the undo is itself undoable). Note that Android's own automatic
  backup doesn't include photos or receipts — the in-app zip is the
  only complete copy.
- **Backup reminder** — enable/disable, set the threshold in days
  (1–365, default 30), and optionally toggle on Android system
  notifications. The reminder fires even when the app is closed (a
  lightweight background check wakes the OS at the right time;
  battery cost is essentially zero). If you've never backed up at all,
  it starts nudging once you have five sessions' worth of data.
- **Backup (CSV)** — **Save** every session to a flat CSV for Excel /
  Google Sheets analysis, or **Share** the CSV out via Drive / email /
  Messages / etc. Every logged field is included — tags and wait time
  round-trip too.
- **Import (CSV)** — Round-trips with the CSV export, with a "replace
  existing" toggle.
- **One-time XLSX import** — for the legacy `DC Fast Charging.xlsx`
  log; auto-tags imported rows with the default vehicle.

### Backup & export
- **Full backup** — single `.zip` containing `backup.json` + every vehicle
  profile photo + every session receipt (photos and PDFs, including the
  multi-receipt sessions added in v10). Schema-versioned. **Save** writes
  to a folder you pick on the device; **Share** hands the same zip to the
  Android share sheet so it can land in Drive, email, Messages, or any
  app that accepts files. Restore wipes the database and reinstalls
  inside one Room transaction; foreign keys are remapped to fresh primary
  keys so backups from another phone restore cleanly. Older backups (down
  to v1) still restore — the receipt JSON reader knows three historical
  shapes and falls back gracefully. Confirmation dialog gates the
  destructive action, a damaged or inconsistent backup is refused
  outright before anything is wiped, and a **pre-restore safety
  snapshot** is written automatically so any restore can be undone from
  Settings.
- **Backup reminder** — in-app banner on the Charging log when it's been
  longer than your threshold since the last backup, optionally pushed to
  the notification shade. The notification fires even when the app is
  closed.

### Theming
- Hand-tuned Material 3 EV-green palette (light + dark), with charging-
  type accents (amber / blue / purple) that are themselves theme-aware,
  re-toned for dark mode so they keep their pop instead of washing out on
  dark surfaces. Material You dynamic color is off by default so the look
  stays consistent regardless of wallpaper.

## Open in Android Studio

1. Open Android Studio (a recent version that supports AGP 9.x —
   Narwhal/2025.x or newer).
2. **File → Open…** → pick the `EVSCT` folder.
3. Wait for the initial Gradle sync (AGP 9.2.1, Gradle 9.4.1, Kotlin
   2.2, Compose, the Android SDK pieces it needs). Studio will fetch
   anything missing on first open.
4. Plug in your Pixel via USB with USB debugging enabled, select it as the
   target device, and hit **Run**.

The debug build installs as package `com.evsct.app.debug`.

## Google Maps API key

The Map screen uses the Google Maps SDK for Android. For native mobile use
the Maps SDK is free, but you do need an API key tied to your app's package
and signing certificate.

1. In **Google Cloud Console**, create or pick a project, enable the
   **Maps SDK for Android**, and **Credentials → Create credentials → API key**.
2. Restrict the key by **Android apps**, adding one entry per
   package + signing-certificate SHA-1 fingerprint. The simplest way to read
   your local fingerprints:
   - In Android Studio, press **Ctrl** twice to open Run Anything, type
     `gradle :app:signingReport`, hit **Enter**.
   - Copy the `SHA1` value from each `Variant:` block.

   You'll generally want these entries:
   - `com.evsct.app.debug` + the **debug** variant SHA-1 (local debug builds).
   - `com.evsct.app` + your **upload-key** SHA-1 (local release builds signed
     with `keystore.properties`).
3. Add a single line to **`local.properties`** at the repo root (this file
   is already gitignored):
   ```
   MAPS_API_KEY=AIza…your-key…
   ```
4. Re-run **Build → Make Project**. Without the key set, the app builds and
   runs but the Map screen renders a blank Google logo where the basemap
   would be — that's the signal to set the key.

> **Distributing via Google Play? Add the Play App Signing SHA-1.** When you
> upload an `.aab`, Google **re-signs** it with an *app signing key* whose
> SHA-1 differs from your upload key. The build your testers actually install
> is signed with that key, so the map stays **blank for everyone** unless you
> also add a Maps key restriction for `com.evsct.app` + the **App signing key
> certificate SHA-1** from **Play Console → Setup → App signing**. This is the
> most common "works on my device, blank for testers" trap — do it before your
> first internal-testing rollout.

## Releasing to Google Play

Play distributes **App Bundles** and rejects debug-signed uploads, so a real
release needs its own upload keystore. The build is wired to read one from a
gitignored `keystore.properties`; without that file the release build falls
back to debug signing (still fine for local `installRelease` and for CI, which
only needs R8 + release lint to run).

1. **Generate an upload keystore** once and keep it (and its passwords) backed
   up somewhere safe — losing them means you can't ship updates under the same
   upload key without a reset via Play support:
   ```
   keytool -genkeypair -v -keystore upload-keystore.jks \
     -keyalg RSA -keysize 2048 -validity 10000 -alias upload
   ```
2. **Copy `keystore.properties.template` to `keystore.properties`** (gitignored)
   and fill in the path and passwords. The `.jks` is gitignored too (`*.jks`),
   so neither secret gets committed.
3. **Build the bundle:**
   ```
   ./gradlew bundleRelease
   ```
   The artifact lands at `app/build/outputs/bundle/release/app-release.aab`.
4. **versionCode** is derived automatically from the git commit count, so every
   build off a new commit gets a unique, increasing code (Play rejects re-used
   codes). Override it for a specific upload with
   `./gradlew bundleRelease -PevsctVersionCode=42`. Bump `versionName` in
   `app/build.gradle.kts` by hand when you want a new user-visible version.
5. On first upload, enroll in **Play App Signing** (the default) and then add
   the app signing key's SHA-1 to your Maps API key — see the callout in the
   [Google Maps API key](#google-maps-api-key) section above.

## Importing an existing xlsx log

1. Drop your xlsx (e.g. `DC Fast Charging.xlsx`) somewhere reachable on the
   phone (Drive, local storage, etc.).
2. **Settings → Import legacy XLSX…** and pick the file.
3. Confirm — the importer is one-shot, so re-running it would create
   duplicates.

If the XLSX importer gives trouble (POI on Android can be finicky), export
the sheet to CSV from Google Sheets and use **Import CSV…** instead.

## Migrating to a new phone

1. On the old phone, **Settings → Full backup**. Either:
   - **Save backup file…** to a folder on the device, or
   - **Share backup file…** to send the same `evsct-backup-<timestamp>.zip`
     straight to Drive, email, Messages, or any other share target.
2. On the new phone: install the app, open it once so the database is
   initialized, then **Settings → Full backup → Restore from backup…** and
   pick the zip. Confirm the destructive restore.

Sessions, trips, vehicles, vehicle photos, and receipt files (photos and
PDFs) all come across.

## Stack

- Kotlin 2.2, Jetpack Compose, Material 3
- Room (SQLite) with hand-rolled migrations
- Hilt for DI, Navigation Compose for screens
- WorkManager for the background backup-reminder check
- Coil for image loading
- Google Maps SDK for Android + Maps Compose (incl. `HeatmapTileProvider`)
- `android.graphics.pdf.PdfDocument` for the year-recap PDF; the HTML
  recap is rendered as plain inline SVG (no chart/map libraries). Its map
  basemap is bundled from [Natural Earth](https://www.naturalearthdata.com/)
  1:50m admin-1 data (public domain), simplified to a small outline.
- Apache POI for the legacy XLSX importer (one-shot only)
- DataStore Preferences for cross-screen settings (units, currency,
  backup reminder, map prefs, last-backup timestamp)
- AGP 9.3.0, Gradle 9.6.1, minSdk 30, targetSdk 36

## License

Released under the [Apache License 2.0](LICENSE). Copyright © 2026 David Robson.

This is a personal hobby project; bug reports and small fixes are welcome via
GitHub issues, but I'm not actively soliciting feature contributions.

### Trademarks

EVSCT is an independent personal project. Charging network names that appear
in the app (Tesla Supercharger, ChargePoint, Electrify America, EVgo, FLO,
etc.) and any vehicle makes or models you enter are trademarks of their
respective owners. They are used only so you can label your own charging
sessions and vehicles. EVSCT is not affiliated with, endorsed by, or
sponsored by any charging network, automaker, or other company named in the
app or its documentation.
