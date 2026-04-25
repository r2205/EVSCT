# EVSCT — EV Session & Charging Tracker

Personal Android app for logging EV charging sessions (mostly DC fast charging
on road trips). Stores everything locally; backs up to CSV.

## Open in Android Studio

1. Open Android Studio (Hedgehog or newer).
2. **File → Open…** → pick the `EVSCT` folder.
3. Wait for the initial Gradle sync (it will install AGP 8.7, Kotlin 2.1,
   Compose, and the Android SDK pieces it needs).
4. Plug in your Pixel via USB with USB debugging enabled, select it as the
   target device, and hit **Run**.

The first run installs the debug build with package `com.evsct.app.debug`.

## Importing your existing log

1. Drop `DC Fast Charging.xlsx` somewhere reachable on the phone (Drive, local
   storage, etc.).
2. In the app: **Settings → Import legacy XLSX…** and pick the file.
3. Confirm — the importer is one-shot, so re-running it will create duplicates.

If the XLSX importer ever gives you trouble, export the sheet to CSV from
Google Sheets and use **Import CSV…** instead.

## Backing up

**Settings → Export to CSV…** writes every session to a CSV file you can open
in Excel or Google Sheets. The CSV format round-trips through **Import CSV…**.
