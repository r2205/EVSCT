# EVSCT Full-App Bug Hunt — Findings Report

Read-only audit of `app/src/main` (all 78 Kotlin files + manifest/XML resources reviewed).
Findings ranked most-severe first. Severity is estimated impact-if-triggered weighted by
likelihood; confidence is how sure I am the described behavior actually occurs.

---

## High severity

### 1. `parseDecimal` misparses European-formatted numbers by ~1000×
- **File:** `app/src/main/java/com/evsct/app/util/Format.kt:89-93`
- **Summary:** When input contains both `,` and `.`, every comma is stripped on the assumption the format is US-style (`1,234.5`). European-style input (dot-thousands, comma-decimal) is silently mangled.
- **Failure scenario:** User pastes or types `1.234,56` (e.g. odometer copied from a car app on a German/French-locale device). `t.replace(",", "")` yields `"1.23456"` → stored as **1.23456 km** instead of 1234.56. No error, no hint — the value silently lands in the DB and skews trip distance, efficiency legs, and cost/km. Affects every numeric field routed through `parseDecimal`: cost, energy, odometer, posted rates (SessionEdit), battery kWh & range (VehicleEdit), trip start/end odometer (TripEditDialog).
- **Severity:** High (silent data corruption of stored values)
- **Confidence:** High on behavior; medium on how often users produce this input (paste is the realistic path — `KeyboardType.Decimal` keyboards rarely offer both separators).

### 2. `parseDecimal` treats a lone comma as a decimal point — `"1,234"` becomes 1.234
- **File:** `app/src/main/java/com/evsct/app/util/Format.kt:91`
- **Summary:** A comma with no dot is always interpreted as the decimal separator, so a US-style thousands-grouped value without decimals is divided by ~1000.
- **Failure scenario:** User pastes odometer `12,345` (US thousands formatting) → stored as **12.345 km**. Trips computed against neighboring sessions produce negative/absurd distances; "Odometer went backward" hint fires against the *next* session but the corrupted value is already saveable and savable silently if the user ignores the advisory hint.
- **Severity:** High-medium (silent, hard to notice until stats look wrong)
- **Confidence:** High (behavior is certain; the ambiguity is inherent to the dual-separator design and acknowledged in the docstring — but there is no guard such as "comma with exactly 3 trailing digits is suspicious").

### 3. Destructive migration fallback also fires on **downgrade**, and `exportSchema = false` prevents migration verification
- **Files:** `app/src/main/java/com/evsct/app/di/AppModule.kt:53`, `app/src/main/java/com/evsct/app/data/db/EvsctDatabase.kt:16`
- **Summary:** `fallbackToDestructiveMigration(dropAllTables = true)` covers *any* missing migration path, including version downgrades; combined with `android:allowBackup="true"` + cloud auto-backup of `evsct.db`, a real user can hit it.
- **Failure scenario:** (a) User sideloads or is rolled back to an older APK (e.g. reinstalling an old release, or a beta → stable downgrade) while the on-device DB is at version 11 → Room drops all tables, entire session history silently wiped. (b) Play auto-restore delivers a v11 database onto a device that still has an older app build installed → same wipe. The upgrade chain 1→11 itself is complete and correct (verified each migration; indices recreated in 1→2; `session_receipts` schema matches the entity), so a normal sequential upgrader never hits fallback — the exposure is downgrade/restore paths only. Additionally `exportSchema = false` means there are no schema JSONs, so no `MigrationTestHelper` test can ever verify the chain and future schema drift will be caught only in production.
- **Severity:** High impact, low likelihood → net medium-high
- **Confidence:** High on mechanism.

### 4. Backup/CSV export opens the destination with `"wt"` — a mid-write failure destroys the previous good backup
- **Files:** `app/src/main/java/com/evsct/app/data/backup/BackupIo.kt:92`, `app/src/main/java/com/evsct/app/data/csv/CsvIo.kt:144`
- **Summary:** Export truncates the user-picked file before writing. There is no write-to-temp-then-rename, and no cleanup of a partially-written file on failure.
- **Failure scenario:** User overwrites their existing `evsct-backup.zip` on Drive/SD. `writeBackupZip` throws halfway (device storage full, SAF provider hiccup, process death). The old backup is already truncated → the user now has **zero** valid backups, while the app reports "Backup failed" and the reminder state still counts the previous backup as recent. This is precisely the artifact the app exists to protect.
- **Severity:** High-medium (backup destruction on the failure path)
- **Confidence:** High on mechanism; failure-mid-write is uncommon but not exotic.

### 5. Restore silently drops malformed rows and silently nulls dangling FK references
- **File:** `app/src/main/java/com/evsct/app/data/backup/BackupIo.kt:812-815` (`mapObjects`), `287-288` (remap)
- **Summary:** `mapObjects` swallows any row whose parse throws (e.g. missing `name`/`sessionStart`); `tripId?.let(tripIdMap::get)` / `vehicleId?.let(vehicleIdMap::get)` silently map unknown ids to `null`.
- **Failure scenario:** A backup with one corrupted vehicle row (truncated upload, bit rot, hand edit) restores "successfully": the vehicle vanishes, and **every session that referenced it silently becomes unassigned** — per-vehicle stats, efficiency analysis, and default-vehicle behavior all silently degrade. Dropped *session* rows disappear from the user's history with no report; the success dialog just shows the surviving counts, which the user has no baseline to compare against.
- **Severity:** Medium-high (silent partial data loss, only on damaged backups)
- **Confidence:** High on mechanism.

---

## Medium severity

### 6. Trip-scoped efficiency pairs sessions across an interleaved untripped charge — silently wrong legs
- **Files:** `app/src/main/java/com/evsct/app/util/EfficiencyAnalysis.kt:117-120`, `app/src/main/java/com/evsct/app/ui/trips/TripDetailViewModel.kt:58-61`
- **Summary:** `isContinuous` treats any two *adjacent-in-the-passed-list* same-trip sessions as continuous. TripDetail passes only the trip's sessions (`observeForTrip`), so a same-vehicle session that happened between them but isn't in the trip (e.g. a home charge mid-trip) is invisible.
- **Failure scenario:** Trip A has S1 and S3; the user charged at home (untripped S2) between them. TripDetail forms leg S1→S3: distance includes S2-era driving, and energy = `S1.batteryEndPct − S3.batteryStartPct` is understated because S2 recharged the pack → km/kWh silently inflated. VehicleDetail (which analyzes the full per-vehicle list) computes different, more correct legs — the two screens contradict each other with no indication.
- **Severity:** Medium (silently wrong stats, no crash)
- **Confidence:** Medium-high.

### 7. `continuesPrevious` attests continuity with a session that can later change
- **File:** `app/src/main/java/com/evsct/app/util/EfficiencyAnalysis.kt:71-74`
- **Summary:** The flag is consumed against whatever session is *adjacent at analysis time*, not the session the user attested against. Nothing clears or re-validates the flag when a session is inserted between (backfill) or the predecessor is deleted.
- **Failure scenario:** S_B has `continuesPrevious=true` attesting "nothing untracked since S_A". User later backfills forgotten session S_X between them (without odometer/battery data, or with it). Analysis now pairs S_X→S_B under S_B's flag — an attestation the user never made — producing a silently wrong (or wrongly-excluded) leg. There is no "one side of the pair" inconsistency (the flag lives only on the later session), but the pairing target drifts silently.
- **Severity:** Medium-low
- **Confidence:** High on behavior; this is a design gap rather than a coding slip.

### 8. No blocking validation anywhere — contradictory/absurd sessions reach the DB from all three write paths
- **Files:** `app/src/main/java/com/evsct/app/ui/sessions/SessionEditViewModel.kt:642-649`, `app/src/main/java/com/evsct/app/data/csv/CsvFormat.kt:122-147`, `app/src/main/java/com/evsct/app/data/csv/XlsxImporter.kt:110-111`
- **Summary:** All validation is advisory (`hints`); save never blocks. Battery % has no 0–100 clamp on any path, and there isn't even a *hint* for >100 or negative values. Cost/energy/odometer accept negatives (`parseDecimal("-5")` → −5.0). CSV import applies zero range checks. The XLSX importer's percent heuristic multiplies by 100 unconditionally, so a sheet storing battery as plain `85` (not %-formatted) imports as **8500%**.
- **Failure scenario:** XLSX import of a sheet with plain-number battery columns fills the DB with 4-digit percentages; `batteryDeltaPct`, efficiency legs, and the recap silently consume them. Negative duration can't happen (parser is digits-only) but negative cost/energy/odometer can, and `CurrencyTotals` will happily aggregate negative costs into totals.
- **Severity:** Medium (by-design leniency on the edit screen, but the import paths have no user watching hints at all)
- **Confidence:** High.

### 9. CSV formula-injection guard apostrophe-prefixes every negative number — including all North-American longitudes
- **Files:** `app/src/main/java/com/evsct/app/data/csv/CsvIo.kt:31-43`, `app/src/main/java/com/evsct/app/data/csv/CsvFormat.kt:87-88`
- **Summary:** `-` is in `FORMULA_TRIGGERS`, so `encodeField` prefixes any negative value with `'`. Longitude is negative for the entire western hemisphere, so effectively every located session exports `longitude` as `'-79.38`.
- **Failure scenario:** User opens the export in Excel/Sheets to chart or geo-analyze their data: longitude (and any negative cost adjustments) arrive as **text**, breaking numeric operations; other tools ingesting the CSV see a literal `'-79.38`. EVSCT's own re-import round-trips correctly (`decodeField` strips it), so the bug is invisible in-app. Note the standard mitigation for `-` is to only escape when the *rest* of the field is non-numeric.
- **Severity:** Medium (universal data-quality defect in exports; no in-app loss)
- **Confidence:** High.
- Also verified: XLSX **import** does not reintroduce the injection class — imported strings are only ever re-exported through `encodeField`, and the HTML recap escapes via `esc()`. No export path bypasses `encodeField`.

### 10. Zip-bomb caps bypassed for entries with unrecognized names
- **File:** `app/src/main/java/com/evsct/app/data/backup/BackupIo.kt:490-496`
- **Summary:** Entries not matching `backup.json`, `vehicles/`, or `receipts/` contribute `0` bytes to `totalBytes`, but `zip.closeEntry()` still **inflates the entire entry** to advance the stream — outside all caps.
- **Failure scenario:** A malicious/corrupt backup contains `junk/bomb` deflating from ~100 KB to tens of GB. Restore decompresses-and-discards it with no byte cap: minutes of pegged CPU and a hung restore UI (no OOM/disk fill, since bytes are discarded). The documented caps (`MAX_ENTRY_BYTES`, `MAX_TOTAL_BYTES`) never trip. The caps themselves are otherwise correct — no off-by-one (`> limit` after累积 is fine), and many-small-entries are correctly bounded by `MAX_BACKUP_ENTRIES` before the aggregate cap matters.
- **Severity:** Medium-low (DoS-shaped, needs a hostile file the user chose to restore)
- **Confidence:** Medium (relies on `ZipInputStream.closeEntry()` inflating to skip, which is its documented behavior for streamed zips).

### 11. `exitRequested` latch can permanently brick the edit screen's Save/Delete if the pop is dropped
- **Files:** `app/src/main/java/com/evsct/app/ui/sessions/SessionEditViewModel.kt:196-200, 714-715`, `app/src/main/java/com/evsct/app/ui/navigation/EvsctNavGraph.kt:87-89, 159` (same pattern in `VehicleEditViewModel.kt:80-85`)
- **Summary:** `save()` latches `exitRequested = true` *before* calling `onSaved()`, and `onSaved` → `ifResumed { popBackStack() }` silently drops the pop when the entry isn't RESUMED. `exitRequested` is never reset.
- **Failure scenario:** Save is triggered while the entry is momentarily not RESUMED (dialog transition, fast back-and-tap, the exact mid-transition taps `ifResumed` exists to swallow). The row *is* saved, but the pop is dropped → the screen stays visible and every subsequent Save/Delete tap is a no-op (`if (commitInFlight || exitRequested) return`). User must back out manually; if they had edited further, those edits are silently unsaveable.
- **Severity:** Medium-low (state machine trap; data already committed once)
- **Confidence:** Medium — the window is narrow, but both guards exist because these races were observed before.

### 12. Sessions with coordinates but no brand/address/city silently vanish from both maps
- **Files:** `app/src/main/java/com/evsct/app/ui/map/MapViewModel.kt:177`, `app/src/main/java/com/evsct/app/ui/stats/YearRecapViewModel.kt:387`
- **Summary:** Stops are grouped by `stopKey` (brand|address|city) and blank keys are filtered out — even for sessions that *have* lat/lng.
- **Failure scenario:** GPS autofill gets a fix but the reverse-geocode returns nothing (`reverseGeocodeAt` deliberately returns coords with null address fields), or the user picks a point on the map without filling any text field. The session has valid coordinates yet never renders a pin, isn't in `totalDistinct`, and the map shows "No locations to map yet." Same session is also missing from the year-recap SVG map.
- **Severity:** Medium-low (silent display omission, data intact)
- **Confidence:** High.

### 13. Map-picker location propagates to sibling sessions immediately — Cancel doesn't undo, and a brand-only key over-propagates
- **File:** `app/src/main/java/com/evsct/app/ui/sessions/SessionEditViewModel.kt:826-838`
- **Summary:** `applyPickedLocation` writes the picked coordinates to *other* DB rows (same stopKey) before the user saves the session being edited.
- **Failure scenario:** (a) User drops a pin, sees the reverse-geocoded address is wrong, backs out without saving — the current session is untouched but N sibling sessions have already been rewritten with the abandoned coordinates. (b) With address and city blank, `stopKey` degenerates to brand alone: picking a location on a "FLO" session with no address stamps those coordinates onto **every other address-less FLO session** in the log, potentially collapsing distinct physical stops onto one point.
- **Severity:** Medium-low (documented-intent feature with an unconsented write and an over-broad key)
- **Confidence:** High on behavior.

---

## Low severity

### 14. Share-backup records a successful backup even if the user cancels the share sheet
- **File:** `app/src/main/java/com/evsct/app/data/backup/BackupIo.kt:114-126` (comment acknowledges it); `restore()` also calls `recordBackup()` at line 338.
- **Scenario:** User taps "Share backup file…", the chooser opens, they cancel. `lastBackupAt` = now → reminder banner/notification stay quiet for another full threshold period although no backup left the device (the zip sits only in app cache, which the next share wipes). Deliberate trade-off per the comment, but it weakens the one safety net the reminder provides.
- **Severity:** Low · **Confidence:** High.

### 15. Stats month distance uses currency-filtered sessions; recap uses all — inconsistent, and gap-spanning deltas land in one bucket
- **File:** `app/src/main/java/com/evsct/app/ui/stats/StatsViewModel.kt:195` (passes `costSessions` to `odometerDistanceForMonth`); `YearRecapViewModel.kt:327` passes all scoped sessions.
- **Scenario:** A cross-border trip: USD sessions are invisible to the Stats month-distance walk, so odometer deltas spanning them are attributed oddly or dropped, while the Year Recap counts them — the two screens disagree. Separately, in both implementations a delta between a session months ago and the first session of this month/year is credited entirely to the current bucket (documented for the boundary case, but unbounded — a 6-month logging gap dumps 10,000 km into one month's gas-savings card).
- **Severity:** Low · **Confidence:** High.

### 16. XLSX `combineDateTime` can overflow `Int` when the time cell holds a full datetime serial
- **File:** `app/src/main/java/com/evsct/app/data/csv/XlsxImporter.kt:188-190`
- **Scenario:** A time cell accidentally containing a full Excel datetime (e.g. `45123.5` instead of `0.5`): `frac * 86400 ≈ 3.9e9` → `.toInt()` overflows negative → `cal.add(SECOND, negative)` shifts the session **backward** by ~50 years-worth of seconds modulo 2³². Rows import with garbage timestamps instead of being skipped.
- **Severity:** Low · **Confidence:** Medium.

### 17. CSV import matches trips/vehicles by exact (case-sensitive) name
- **File:** `app/src/main/java/com/evsct/app/data/csv/CsvIo.kt:213-220, 236-250`
- **Scenario:** Existing trip "summer 2025" + CSV row "Summer 2025" → duplicate trip created. Conversely, two *distinct* trips that share a name collapse into one on round-trip (export writes only the name; import maps by name). Silent structure drift on import.
- **Severity:** Low · **Confidence:** High.

### 18. Brand aggregation is case-sensitive in stats but case-insensitive everywhere else
- **Files:** `app/src/main/java/com/evsct/app/ui/stats/StatsViewModel.kt:267-271`, `YearRecapViewModel.kt:330-336`
- **Scenario:** Sessions tagged "FLO" and "Flo" (possible via free-form entry pre-dating the picker) appear as one brand in the picker/filter (`observeBrands` is `COLLATE NOCASE`, list filter uses `equals(ignoreCase)`) but as two separate bars in "Top brands by spend" and the recap.
- **Severity:** Low · **Confidence:** High.

### 19. Dead delete paths, one of which would orphan receipt files if ever wired up
- **File:** `app/src/main/java/com/evsct/app/ui/sessions/SessionListViewModel.kt:250-252`
- **Scenario:** `SessionListViewModel.delete()` calls only `sessionRepository.delete` — the CASCADE removes `session_receipts` rows but nothing deletes the files on disk. Currently **no UI calls it** (the only reachable delete is the edit screen's `deleteAndExit`, which cleans files correctly), so today this is dead code — but it's a loaded footgun: the first person to add swipe-to-delete gets permanent receipt-file leaks (only a future restore's `cleanOrphans` would ever reclaim them). `VehicleListViewModel.delete` is similarly unreferenced (though it does clean its image).
- **Severity:** Low (latent) · **Confidence:** High.

### 20. While live-tracking, every keystroke triggers a DataStore write and a notification re-post
- **Files:** `app/src/main/java/com/evsct/app/ui/sessions/SessionEditViewModel.kt:230-232`, `app/src/main/java/com/evsct/app/util/InProgressChargeNotifier.kt:66-81, 99-101`
- **Scenario:** The `_state.collect` hook calls `updateIfTracking` → `post()` on **every** form keystroke; `post()` unconditionally calls `persistTrackedId(sessionId)` (a disk write via `dataStore.edit`) and re-posts the notification. Typing a 40-character note during a tracked charge = 40 DataStore commits + 40 notification updates. Functionally correct (`setOnlyAlertOnce`), just wasteful; the persist is only needed when the id *changes*.
- **Severity:** Low (perf/battery) · **Confidence:** High.

### 21. Session upsert and receipt-table reconciliation are not in one transaction
- **File:** `app/src/main/java/com/evsct/app/ui/sessions/SessionEditViewModel.kt:670-703`
- **Scenario:** Process death between `sessionRepository.upsert` and `sessionReceiptRepository.insertAll` → the session saves but newly attached receipts never get rows; their files remain on disk untracked (and `reconcileReceiptFiles` never ran, so they aren't cleaned either). Very narrow window.
- **Severity:** Low · **Confidence:** High on mechanism.

### 22. Cloud auto-backup restores a DB that references media files that were never backed up
- **File:** `app/src/main/res/xml/data_extraction_rules.xml:6-13`
- **Scenario:** New phone via cloud restore (not device-to-device): DB + prefs arrive, `receipts/` and `vehicles/` don't (documented 25 MB trade-off). Sessions show paperclip icons and receipt tiles for files that don't exist; vehicle photos blank. UI degrades gracefully (checked: `openReceiptExternally` no-ops, `AsyncImage` renders empty) but the dead rows persist indefinitely with no "missing file" indication or cleanup.
- **Severity:** Low · **Confidence:** High.

### 23. Backup export crashes with `ZipException: duplicate entry` if two receipt rows share a file basename
- **File:** `app/src/main/java/com/evsct/app/data/backup/BackupIo.kt:172-179`
- **Scenario:** Restore dedupes receipts by basename into a shared path (`plannedReceipts` is a `Set`), so a crafted/merged backup where two sessions reference the same file yields two rows → next **export** writes the same zip entry name twice and the whole export fails. Requires an unusual prior state (normal attach flow always makes UUID-unique copies), hence low. Related: with shared paths, removing the receipt from one session deletes the file out from under the other (`reconcileReceiptFiles`).
- **Severity:** Low · **Confidence:** Medium (mechanism certain, precondition rare).

### 24. Notification deep link may be dropped when the activity is recreated after process death
- **File:** `app/src/main/java/com/evsct/app/MainActivity.kt:49`
- **Scenario:** `consumeIntentExtras(intent)` runs in `onCreate` only when `savedInstanceState == null` (to survive rotation). If the user taps the in-progress notification and the system restores the task from a dead process, the activity can be recreated with a non-null saved bundle *and* the notification intent as `getIntent()` — the session-id extra is never consumed and the tap lands on the session list instead of the tracked session's edit screen.
- **Severity:** Low · **Confidence:** Low-medium (depends on OEM/task-restore specifics; the rotation guard is otherwise correct).

### 25. `CsvIo.export` reports success with count 0 when the output stream can't be opened
- **File:** `app/src/main/java/com/evsct/app/data/csv/CsvIo.kt:142-150`
- **Scenario:** `openOutputStream` returns null (revoked SAF grant) → no exception, `export` returns 0 → Settings shows "Exported 0 sessions to CSV." as a success dialog; the user believes an (empty) export happened. `BackupIo.export` handles the same case correctly with a Failure.
- **Severity:** Low · **Confidence:** High.

### 26. MIGRATION_9_10 promotes empty-string `receiptImagePath` values into receipt rows
- **File:** `app/src/main/java/com/evsct/app/data/db/EvsctDatabase.kt:202-209`
- **Scenario:** `WHERE receiptImagePath IS NOT NULL` passes `''`. If any legacy row ever stored an empty string instead of NULL (no evidence in current code, but v3-era write paths aren't visible anymore), migration creates a `session_receipts` row with `filePath = ''` → permanent phantom receipt tile pointing at `filesDir` itself. Speculative.
- **Severity:** Very low · **Confidence:** Low.

### 27. Money display hardcodes the `$` symbol and mixes locale conventions
- **File:** `app/src/main/java/com/evsct/app/util/Format.kt:33-47`
- **Scenario:** `Format.money` always renders `$` + locale-default `DecimalFormat` grouping. Today both supported currencies (CAD/USD) use `$`, so this is future-proofing debt; on a German-locale device the app shows the hybrid `"$1.234,56 CAD"`. Display-only.
- **Severity:** Very low · **Confidence:** High.

### 28. Double-precision drift in money totals — measured as negligible
- **File:** `app/src/main/java/com/evsct/app/util/CurrencyTotals.kt:42` (and every `sumOf { totalCost }` aggregation)
- Cost totals sum `Double`s; across even tens of thousands of 2-decimal amounts the accumulated binary error stays ~1e-9, far below the 2-decimal display rounding (`DecimalFormat` HALF_EVEN). **No user-visible defect found** — noting it only because the brief asked explicitly.
- **Severity:** Informational · **Confidence:** High.

### 29. Quick-track abandonment leaves an empty session row and a persistent notification
- **File:** `app/src/main/java/com/evsct/app/ui/sessions/SessionListViewModel.kt:265-285`
- **Scenario:** "Track a charge now" inserts a DB row immediately. If the user backs out of the edit screen and never returns, a near-empty session stays in the log and the ongoing notification keeps tracking until they eventually save or delete that session. Design choice, but there's no expiry/cleanup path.
- **Severity:** Low (UX) · **Confidence:** High.

---

## Explicitly-checked areas with no findings

Stating these per the brief's "say so explicitly" requirement:

- **Room migrations 1→11:** each verified individually — column lists, FK actions, and index names in the 1→2 table rebuild exactly match the current entities; `session_receipts` created in 9→10 + 10→11 matches `SessionReceipt`. No migration drops an index/constraint a later one needs. (Findings #3 and #26 are the only migration-adjacent items.)
- **DAO/repository REPLACE hazard:** `SessionRepository.upsert`, `TripRepository.upsert`, `VehicleRepository.upsert` all correctly route updates through `@Update`, avoiding the REPLACE→CASCADE/SET NULL trap the comments describe. CSV import correctly pre-picks pin colors to avoid collecting a Flow inside `withTransaction`.
- **Backup size caps:** no off-by-one; per-entry, aggregate, JSON, and entry-count caps all enforce correctly for recognized entries (see #10 for the unrecognized-entry gap). Zip-slip is defended twice (entry names and JSON-supplied names via `sanitizedBasename`).
- **CSV parser (`Csv.parseAll`/`parseLine`):** quoted fields, escaped quotes, embedded newlines, and the quote-desync edge called out in comments all behave correctly. The lenient date pattern `yyyy-M-d H:m:s` is `Locale.US`-pinned and strict-resolved — `"2024-1-5"` and `"2024-01-05"` parse identically regardless of device locale; impossible dates are skipped, not rolled over.
- **Hardcoded-locale audit (the suspected known gap):** the audit found the *inverse* of the suspicion — every `Locale.US` use is intentional and correct (CSV machine format, SVG/CSS numbers in `YearRecapHtml.fmt`, filename timestamps, odometer text seeding that must survive `parseDecimal`). UI display formatting correctly follows the device locale. The real locale defect is in `parseDecimal` itself (findings #1/#2).
- **Formatter thread-safety:** `DateTimeFormatter` statics + per-thread `DecimalFormat` via `ThreadLocal` — correct; the ad-hoc `SimpleDateFormat` instances in StatsViewModel/CsvIo/BackupIo are method-local, so no shared-state regression.
- **DI (`AppModule`):** scoping is correct — DB/prefs/stores/`@AppScope` are `@Singleton`, DAOs correctly unscoped delegating to the singleton DB. The `@AppScope` `SupervisorJob()+Dispatchers.IO` scope is used exactly where viewModelScope cancellation would lose work (post-nav geocode, onCleared cleanup) — appropriate.
- **Navigation:** the `ifResumed` guard consistently wraps every navigate/pop; map-picker result via `previousBackStackEntry.savedStateHandle` + one-shot consumption is correct; deep-link single-top handling avoids the duplicate-VM data-loss case (see #24 for the one edge).
- **ViewModel/coroutine hygiene:** all `stateIn(WhileSubscribed(5s))`, no leaked collectors found; `SessionEditViewModel`'s file-lifecycle tracking (touched-paths / original-paths / onCleared rollback) is coherent in all back-out paths; `VehicleEditViewModel` mirrors it correctly including delete-with-image.
- **LocationAutofill:** unusually defensive and correct — GeocodeListener onError handling (avoids permanently suspended coroutines), FUSED-provider API-31 gating, PASSIVE-provider avoidance, CancellationSignal cleanup, permission checks that degrade to typed results rather than crashing. No findings.
- **Notifications/workers:** channel creation, POST_NOTIFICATIONS gating on 33+, `SecurityException` swallow on the check-post race, WorkManager unique-work REPLACE chain, and Hilt worker factory wiring (incl. the manifest initializer removal) are all correct. (See #20 for the keystroke-frequency nit.)
- **YearRecap HTML/PDF:** all user-controlled strings pass through `esc()` in HTML; SVG/CSS numbers are `Locale.US`; `PdfDocument` is closed in `finally`. Palette hexes are enum-sourced, not user input. No injection path found.
- **Theme/Color/EmptyState/ImageZoomDialog/MoneyStat/UserPrefs/Tags/DurationFormat/RegionCodes/Brands/TripPinPalette/MapPicker/MapTypeControls:** reviewed; no findings beyond items above. `DurationFormat`'s digits-only guards (the `"-0:11:00"` case) are correct.
- **FileProvider config:** all four shared dirs (`receipts` files-path, three cache-paths) are declared in `file_paths.xml` and match the code's authorities/paths.
