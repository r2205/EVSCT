# EVSCT — EV Session & Charging Tracker

Personal Android app for logging EV charging sessions (DC fast on road trips,
AC at home/hotels). Stores everything locally; nothing leaves the phone unless
you hit Export. Built with Kotlin + Jetpack Compose + Room + Hilt.

## Features

### Logging a session
- Date/time, charging type (DC Fast / AC L2 / AC L1), and pricing model
  (per-kWh, per-minute, flat, free, hybrid).
- Odometer, energy delivered (kWh), total cost, charging duration, battery
  start/end %.
- Per-session **currency chip** (CAD / USD) — defaults to your preferred
  currency, but a US road-trip session can be tagged USD even when your
  default is CAD.
- Posted vs. effective rates ($/kWh, $/min, max kW) so you can see when a
  station charges differently than advertised.
- Notes and an optional **receipt** — either a **photo** (with tap-to-
  fullscreen + pinch-to-zoom) or a **PDF** (opens in your system PDF
  viewer). A small "Photo / PDF" chooser appears when you tap to attach.

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
- **Smart duration entry** — `25` becomes `0h 25m 0s`, `1:25` becomes
  `1h 25m 0s`, `0:11:00` stays exact. Phone-pad keyboard with an inline `:`
  insert button.
- **Data-validation hints** — gentle amber card at the top of the form when
  something looks off (odometer went backward, effective price wildly
  differs from posted, battery decreased during charge, etc.). Affected
  fields turn red so the offender is obvious.
- **Empty odometer warning** — confirmation prompt before saving without an
  odometer reading.
- **Remove confirmation** — tapping Remove on an attached receipt asks
  before discarding it.

### The Charging log
- Cards per session with a colored leading bar (amber DC fast, blue AC L2,
  purple AC L1), brand, city, cost in primary green, eff. $/kWh, vehicle and
  trip pills, and a receipt icon when a photo or PDF is attached.
- **Vehicle tabs** — filter to a specific car, or All. New sessions started
  from a vehicle tab pre-select that vehicle.
- **Search** — free-text matching brand, city, prov, address, station,
  notes.
- **Filter sheet** — by brand, by date range with quick presets (This
  month / Last 3 mo. / Last year / custom from-to via date picker).
- **Multi-select** — long-press to enter selection mode, then bulk-assign
  selected sessions to a trip.
- Top bar entries: Search · Stats · **Map** · Trips · Settings.

### Map view
- Google Maps full-screen view with **one pin per distinct charging stop**
  (deduped by brand + address + city). Tap a pin for brand, address, and
  visit count.
- **Trip-colored pins** — each trip gets a color from a 10-swatch palette,
  auto-assigned but customizable in the trip edit dialog. Stops visited
  across multiple trips render as a neutral gray "shared" pin so the
  visual stays honest.
- **Filter sheet** — toggle "Color pins by trip" off to render every pin
  in red, hide individual trips with a checkbox list, or hide untripped
  sessions.
- **First-open backfill** — the first time you open the screen, every
  stop with only a textual address is reverse-geocoded and the resolved
  coordinates saved back to those sessions. After that, the map opens
  instantly.

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
- Optional start/end odometer per trip — when both are filled, distance =
  end − start. Otherwise distance is inferred from session odometer
  readings.
- **Map pin color** picker per trip (10 swatches, auto-assigned by default).
- Trip detail shows total cost, energy, distance, $/km or $/mi, $/kWh,
  plus every session in the trip.

### Stats
- Headline card: sessions, total cost, total energy, average effective
  $/kWh, average power.
- 12-month rolling charts of cost and energy.
- Top brands by total spend.
- Charging-type split (DC Fast / AC L2 / AC L1) with percentages.
- Vehicle filter mirrors the Charging log tabs.

### Settings
- **Vehicles** — manage your EVs and pick a default for new sessions.
- **Units & currency** — Kilometres / Miles segmented switch and CAD / USD
  default-currency switch. Distances are stored canonically in km; the
  switch only affects display + form labels. Each session keeps the
  currency it was saved with — the default just seeds new sessions and
  tags totals on the dashboards.
- **Full backup** — Export a `.zip` containing everything, or Restore.
- **Backup reminder** — enable/disable, set the threshold in days
  (1–365, default 30), and optionally toggle on Android system
  notifications (requests `POST_NOTIFICATIONS` on Android 13+).
- **Backup (CSV)** — Export every session to a flat CSV for Excel /
  Google Sheets analysis.
- **Import (CSV)** — Round-trips with the CSV export, with a "replace
  existing" toggle.
- **One-time XLSX import** — for the legacy `DC Fast Charging.xlsx`
  log; auto-tags imported rows with the default vehicle.

### Backup & export
- **Full backup** — single `.zip` containing `backup.json` + every vehicle
  profile photo + every session receipt (photos and PDFs). Schema-
  versioned. Restore wipes the database and reinstalls inside one Room
  transaction; foreign keys are remapped to fresh primary keys so backups
  from another phone restore cleanly. Confirmation dialog gates the
  destructive action.
- **Backup reminder** — in-app banner on the Charging log when it's been
  longer than your threshold since the last backup, optionally pushed to
  the notification shade for nag-while-away coverage.

### Theming
- Hand-tuned Material 3 EV-green palette (light + dark), with charging-
  type accents (amber / blue / purple). Material You dynamic color is off
  by default so the look stays consistent regardless of wallpaper.

## Open in Android Studio

1. Open Android Studio (Hedgehog or newer).
2. **File → Open…** → pick the `EVSCT` folder.
3. Wait for the initial Gradle sync (AGP 8.13, Kotlin 2.1, Compose,
   the Android SDK pieces it needs).
4. Plug in your Pixel via USB with USB debugging enabled, select it as the
   target device, and hit **Run**.

The debug build installs as package `com.evsct.app.debug`.

## Google Maps API key

The Map screen uses the Google Maps SDK for Android. For native mobile use
the Maps SDK is free, but you do need an API key tied to your app's package
and signing certificate.

1. In **Google Cloud Console**, create or pick a project, enable the
   **Maps SDK for Android**, and **Credentials → Create credentials → API key**.
2. Restrict the key by **Android apps**, adding both the debug and release
   package + signing-certificate SHA-1 fingerprints. The simplest way to
   read your debug fingerprint:
   - In Android Studio, press **Ctrl** twice to open Run Anything, type
     `gradle :app:signingReport`, hit **Enter**.
   - Copy the `SHA1` value from the `Variant: debug` block.
3. Add a single line to **`local.properties`** at the repo root (this file
   is already gitignored):
   ```
   MAPS_API_KEY=AIza…your-key…
   ```
4. Re-run **Build → Make Project**. Without the key set, the app builds and
   runs but the Map screen renders a blank Google logo where the basemap
   would be — that's the signal to set the key.

## Importing an existing xlsx log

1. Drop your xlsx (e.g. `DC Fast Charging.xlsx`) somewhere reachable on the
   phone (Drive, local storage, etc.).
2. **Settings → Import legacy XLSX…** and pick the file.
3. Confirm — the importer is one-shot, so re-running it would create
   duplicates.

If the XLSX importer gives trouble (POI on Android can be finicky), export
the sheet to CSV from Google Sheets and use **Import CSV…** instead.

## Migrating to a new phone

1. On the old phone: **Settings → Full backup → Export backup file…** and
   save the resulting `evsct-backup-<timestamp>.zip` somewhere you can
   reach from the new phone (Drive, Files, etc.).
2. On the new phone: install the app, open it once so the database is
   initialized, then **Settings → Full backup → Restore from backup…** and
   pick the zip. Confirm the destructive restore.

Sessions, trips, vehicles, vehicle photos, and receipt files (photos and
PDFs) all come across.

## Stack

- Kotlin 2.1, Jetpack Compose, Material 3
- Room (SQLite) with hand-rolled migrations
- Hilt for DI, Navigation Compose for screens
- Coil for image loading
- Google Maps SDK for Android + Maps Compose
- Apache POI for the legacy XLSX importer (one-shot only)
- DataStore Preferences for cross-screen settings (units, currency,
  backup reminder, last-backup timestamp)
- AGP 8.13.2, minSdk 30, targetSdk 35
