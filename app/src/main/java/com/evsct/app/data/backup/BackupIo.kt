package com.evsct.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.evsct.app.data.db.EvsctDatabase
import com.evsct.app.data.prefs.AppPreferences
import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.data.entity.ChargingType
import com.evsct.app.data.entity.PricingModel
import com.evsct.app.data.entity.SessionReceipt
import com.evsct.app.data.entity.Trip
import com.evsct.app.data.entity.Vehicle
import com.evsct.app.util.BackupReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val SCHEMA_VERSION = 5
private const val BACKUP_JSON = "backup.json"
private const val IMAGE_DIR_IN_ZIP = "vehicles/"
private const val IMAGE_DIR_IN_FILES = "vehicles"
private const val RECEIPT_DIR_IN_ZIP = "receipts/"
private const val RECEIPT_DIR_IN_FILES = "receipts"
/** Subdirectory under cacheDir where prepare-for-share zips land. Mirrored
 *  in res/xml/file_paths.xml so FileProvider can hand out content:// URIs. */
private const val SHARE_DIR_IN_CACHE = "backup-share"

// Decompression caps. A real backup of a heavy user is comfortably under
// these — they exist to short-circuit zip-bombs that decompress a few KB
// of input into gigabytes of output.
private const val MAX_JSON_BYTES: Long = 10L * 1024 * 1024
private const val MAX_ENTRY_BYTES: Long = 25L * 1024 * 1024
private const val MAX_TOTAL_BYTES: Long = 100L * 1024 * 1024
private const val MAX_BACKUP_ENTRIES: Int = 5_000

sealed interface BackupResult {
    data class ExportSuccess(val sessions: Int, val trips: Int, val vehicles: Int) : BackupResult
    data class RestoreSuccess(val sessions: Int, val trips: Int, val vehicles: Int) : BackupResult
    data class Failure(val message: String) : BackupResult
}

/** Successful prepare-for-share. The zip lives in [file], and [counts]
 *  drives the same status message the Save flow surfaces. */
data class PreparedShareBackup(
    val file: File,
    val sessions: Int,
    val trips: Int,
    val vehicles: Int,
)

sealed interface PrepareShareResult {
    data class Success(val prepared: PreparedShareBackup) : PrepareShareResult
    data class Failure(val message: String) : PrepareShareResult
}

/**
 * Reads/writes a single .zip bundle containing every record in the app:
 *   /backup.json     – schema-versioned JSON for vehicles, trips, sessions, settings
 *   /vehicles/<uuid>.jpg – profile images referenced from JSON by filename
 *
 * Restore wipes the existing database and replaces it with the bundle's contents
 * inside a single Room transaction. Foreign keys are remapped to fresh primary
 * keys on the destination device so importing a bundle from another phone works
 * even if the source IDs already exist locally.
 */
@Singleton
class BackupIo @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: EvsctDatabase,
    private val appPreferences: AppPreferences,
    private val backupReminderScheduler: BackupReminderScheduler,
) {

    suspend fun export(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        try {
            val out = context.contentResolver.openOutputStream(uri, "wt")
                ?: return@withContext BackupResult.Failure("Could not open output for writing.")
            val counts = out.use { writeBackupZip(it) }
            appPreferences.recordBackup()
            backupReminderScheduler.refresh()
            BackupResult.ExportSuccess(counts.sessions, counts.trips, counts.vehicles)
        } catch (e: Exception) {
            BackupResult.Failure(e.message ?: "Export failed")
        }
    }

    /**
     * Build the same zip [export] writes, but into a dedicated cache
     * subdirectory and return the resulting [File] so the caller can hand
     * it to an Android share-sheet via FileProvider. Older share files are
     * cleared first so the cache doesn't accumulate after repeated shares.
     *
     * Treats handing the file off as the user's intent to back up — records
     * the timestamp and clears the reminder, matching the Save flow. The
     * user can still cancel the chooser, but the file does exist on disk
     * and the data is captured in a self-contained zip.
     */
    suspend fun prepareShareFile(filenamePrefix: String = "evsct-backup"): PrepareShareResult =
        withContext(Dispatchers.IO) {
            try {
                val shareDir = File(context.cacheDir, SHARE_DIR_IN_CACHE).apply {
                    mkdirs()
                    listFiles()?.forEach { it.delete() }
                }
                val ts = java.text.SimpleDateFormat("yyyy-MM-dd-HHmm", java.util.Locale.US)
                    .format(java.util.Date())
                val target = File(shareDir, "$filenamePrefix-$ts.zip")
                val counts = target.outputStream().use { writeBackupZip(it) }
                appPreferences.recordBackup()
                backupReminderScheduler.refresh()
                PrepareShareResult.Success(
                    PreparedShareBackup(
                        file = target,
                        sessions = counts.sessions,
                        trips = counts.trips,
                        vehicles = counts.vehicles,
                    ),
                )
            } catch (e: Exception) {
                PrepareShareResult.Failure(e.message ?: "Could not prepare backup for share")
            }
        }

    /** Stream every backed-up byte (json + vehicle photos + receipts) into
     *  [out] and return the row counts so callers can surface them. The
     *  caller owns the OutputStream and is expected to close it. */
    private suspend fun writeBackupZip(out: OutputStream): BackupCounts {
        val sessionDao = database.sessionDao()
        val tripDao = database.tripDao()
        val vehicleDao = database.vehicleDao()
        val receiptDao = database.sessionReceiptDao()

        val vehicles = vehicleDao.observeAll().first()
        val trips = tripDao.observeAll().first()
        val sessions = sessionDao.observeAll().first()
        val receipts = receiptDao.findAll()
        val receiptsBySession = receipts.groupBy { it.sessionId }

        val json = buildBackupJson(vehicles, trips, sessions, receiptsBySession)
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(BACKUP_JSON))
            zip.write(json.toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            vehicles.forEach { v ->
                val rel = v.imagePath ?: return@forEach
                val file = File(context.filesDir, rel)
                if (!file.exists()) return@forEach
                val nameInZip = IMAGE_DIR_IN_ZIP + file.name
                zip.putNextEntry(ZipEntry(nameInZip))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }

            // Write every attached receipt file, not just one per session.
            receipts.forEach { r ->
                val file = File(context.filesDir, r.filePath)
                if (!file.exists()) return@forEach
                val nameInZip = RECEIPT_DIR_IN_ZIP + file.name
                zip.putNextEntry(ZipEntry(nameInZip))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return BackupCounts(sessions = sessions.size, trips = trips.size, vehicles = vehicles.size)
    }

    private data class BackupCounts(val sessions: Int, val trips: Int, val vehicles: Int)

    suspend fun restore(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "backup-restore-${UUID.randomUUID()}")
        try {
            tempDir.mkdirs()
            val backupJson = readZipToTemp(uri, tempDir)
                ?: return@withContext BackupResult.Failure("Backup file is missing $BACKUP_JSON.")

            val payload = parsePayload(backupJson)
                ?: return@withContext BackupResult.Failure("Backup is malformed or unsupported.")

            // Plan the relative paths the DB rows will reference, but DON'T
            // touch filesDir yet. If the transaction below throws, the user's
            // existing photos and receipts must stay untouched — copying
            // them in first risks clobbering an existing UUID-collision file
            // that the failed transaction will never get to reference.
            val plannedImages: Map<String, String> = payload.vehicles
                .mapNotNull { v -> v.imageFile?.let(::sanitizedBasename) }
                .associateWith { name -> "$IMAGE_DIR_IN_FILES/$name" }
            val plannedReceipts: Map<String, String> = payload.sessions
                .asSequence()
                .flatMap { it.receipts.asSequence() }
                .mapNotNull { sanitizedBasename(it.file) }
                .toSet()
                .associateWith { name -> "$RECEIPT_DIR_IN_FILES/$name" }

            val sessionDao = database.sessionDao()
            val sessionReceiptDao = database.sessionReceiptDao()
            val tripDao = database.tripDao()
            val vehicleDao = database.vehicleDao()

            val vehicleIdMap = mutableMapOf<Long, Long>()
            val tripIdMap = mutableMapOf<Long, Long>()

            database.withTransaction {
                // Wipe in FK-safe order. ON DELETE SET NULL fires harmlessly here
                // since sessions are about to be removed anyway.
                sessionDao.deleteAll()
                tripDao.deleteAll()
                vehicleDao.deleteAll()

                payload.vehicles.forEach { raw ->
                    val newId = vehicleDao.insert(
                        Vehicle(
                            id = 0,
                            name = raw.name,
                            year = raw.year,
                            make = raw.make,
                            model = raw.model,
                            trim = raw.trim,
                            batteryCapacityKwh = raw.batteryCapacityKwh,
                            nominalRangeKm = raw.nominalRangeKm,
                            vin = raw.vin,
                            notes = raw.notes,
                            imagePath = raw.imageFile?.let(::sanitizedBasename)?.let { plannedImages[it] },
                            isDefault = raw.isDefault,
                            createdAt = raw.createdAt,
                            updatedAt = raw.updatedAt,
                        )
                    )
                    vehicleIdMap[raw.id] = newId
                }

                payload.trips.forEach { raw ->
                    val newId = tripDao.insert(
                        Trip(
                            id = 0,
                            name = raw.name,
                            startDate = raw.startDate,
                            endDate = raw.endDate,
                            startOdometerKm = raw.startOdometerKm,
                            endOdometerKm = raw.endOdometerKm,
                            notes = raw.notes,
                            pinColor = raw.pinColor,
                            createdAt = raw.createdAt,
                        )
                    )
                    tripIdMap[raw.id] = newId
                }

                val sessionEntities = payload.sessions.map { raw ->
                    ChargingSession(
                        id = 0,
                        sessionStart = raw.sessionStart,
                        durationSeconds = raw.durationSeconds,
                        odometerKm = raw.odometerKm,
                        energyKwh = raw.energyKwh,
                        totalCost = raw.totalCost,
                        currency = raw.currency,
                        postedEnergyPricePerKwh = raw.postedEnergyPricePerKwh,
                        postedTimeRatePerMin = raw.postedTimeRatePerMin,
                        postedMaxPowerKw = raw.postedMaxPowerKw,
                        batteryStartPct = raw.batteryStartPct,
                        batteryEndPct = raw.batteryEndPct,
                        chargingType = raw.chargingType,
                        pricingModel = raw.pricingModel,
                        brand = raw.brand,
                        locationCity = raw.locationCity,
                        locationProvince = raw.locationProvince,
                        locationAddress = raw.locationAddress,
                        stationName = raw.stationName,
                        stallName = raw.stallName,
                        tripId = raw.tripId?.let(tripIdMap::get),
                        vehicleId = raw.vehicleId?.let(vehicleIdMap::get),
                        notes = raw.notes,
                        tags = raw.tags,
                        // Legacy column — receipts now live in session_receipts.
                        receiptImagePath = null,
                        latitude = raw.latitude,
                        longitude = raw.longitude,
                        continuesPrevious = raw.continuesPrevious,
                        waitTimeMinutes = raw.waitTimeMinutes,
                        createdAt = raw.createdAt,
                        updatedAt = raw.updatedAt,
                    )
                }
                // insertAll returns the new auto-generated ids in input
                // order — zip back against the raw list so we can attach
                // each session's receipts to its fresh id.
                val newSessionIds = sessionDao.insertAll(sessionEntities)
                val receiptRows = payload.sessions
                    .zip(newSessionIds)
                    .flatMap { (raw, newSessionId) ->
                        raw.receipts.mapNotNull { entry ->
                            val basename = sanitizedBasename(entry.file) ?: return@mapNotNull null
                            val resolvedPath = plannedReceipts[basename] ?: return@mapNotNull null
                            SessionReceipt(
                                sessionId = newSessionId,
                                filePath = resolvedPath,
                                originalFileName = entry.originalName,
                            )
                        }
                    }
                if (receiptRows.isNotEmpty()) sessionReceiptDao.insertAll(receiptRows)
            }

            // Transaction committed — now (and only now) mutate filesDir.
            // A copy failure here just leaves an individual image missing;
            // the database is already internally consistent.
            installFiles(File(tempDir, IMAGE_DIR_IN_FILES), IMAGE_DIR_IN_FILES, plannedImages.keys)
            installFiles(File(tempDir, RECEIPT_DIR_IN_FILES), RECEIPT_DIR_IN_FILES, plannedReceipts.keys)

            // Best-effort: drop any image files in filesDir that weren't
            // referenced by the restored set. Old pre-restore images are now
            // orphaned and safe to remove.
            cleanOrphans(IMAGE_DIR_IN_FILES, plannedImages.values.toSet())
            cleanOrphans(RECEIPT_DIR_IN_FILES, plannedReceipts.values.toSet())

            // The restored data already lives in a backup file the user
            // pointed us at, so treat this moment as a fresh successful
            // backup — otherwise the reminder banner/notification fires
            // immediately after restore against a stale (or null)
            // lastBackupAt timestamp.
            appPreferences.recordBackup()
            backupReminderScheduler.refresh()

            BackupResult.RestoreSuccess(
                sessions = payload.sessions.size,
                trips = payload.trips.size,
                vehicles = payload.vehicles.size,
            )
        } catch (e: Exception) {
            BackupResult.Failure(e.message ?: "Restore failed")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /* --- export helpers --- */

    private fun buildBackupJson(
        vehicles: List<Vehicle>,
        trips: List<Trip>,
        sessions: List<ChargingSession>,
        receiptsBySession: Map<Long, List<SessionReceipt>>,
    ): JSONObject = JSONObject().apply {
        put("schemaVersion", SCHEMA_VERSION)
        put("exportedAt", System.currentTimeMillis())
        put("settings", JSONObject())  // reserved for future preferences

        put("vehicles", JSONArray().also { arr -> vehicles.forEach { arr.put(it.toJson()) } })
        put("trips", JSONArray().also { arr -> trips.forEach { arr.put(it.toJson()) } })
        put(
            "sessions",
            JSONArray().also { arr ->
                sessions.forEach { s ->
                    arr.put(s.toJson(receiptsBySession[s.id].orEmpty()))
                }
            },
        )
    }

    private fun Vehicle.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        putOptLong("year", year?.toLong())
        putOptString("make", make)
        putOptString("model", model)
        putOptString("trim", trim)
        putOptDouble("batteryCapacityKwh", batteryCapacityKwh)
        putOptLong("nominalRangeKm", nominalRangeKm?.toLong())
        putOptString("vin", vin)
        putOptString("notes", notes)
        putOptString("imageFile", imagePath?.removePrefix("$IMAGE_DIR_IN_FILES/"))
        put("isDefault", isDefault)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }

    private fun Trip.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        putOptLong("startDate", startDate)
        putOptLong("endDate", endDate)
        putOptDouble("startOdometerKm", startOdometerKm)
        putOptDouble("endOdometerKm", endOdometerKm)
        putOptString("notes", notes)
        putOptString("pinColor", pinColor)
        put("createdAt", createdAt)
    }

    private fun ChargingSession.toJson(receipts: List<SessionReceipt>): JSONObject = JSONObject().apply {
        put("id", id)
        put("sessionStart", sessionStart)
        putOptLong("durationSeconds", durationSeconds)
        putOptLong("waitTimeMinutes", waitTimeMinutes?.toLong())
        putOptDouble("odometerKm", odometerKm)
        putOptDouble("energyKwh", energyKwh)
        putOptDouble("totalCost", totalCost)
        put("currency", currency)
        putOptDouble("postedEnergyPricePerKwh", postedEnergyPricePerKwh)
        putOptDouble("postedTimeRatePerMin", postedTimeRatePerMin)
        putOptDouble("postedMaxPowerKw", postedMaxPowerKw)
        putOptLong("batteryStartPct", batteryStartPct?.toLong())
        putOptLong("batteryEndPct", batteryEndPct?.toLong())
        put("chargingType", chargingType.name)
        put("pricingModel", pricingModel.name)
        putOptString("brand", brand)
        putOptString("locationCity", locationCity)
        putOptString("locationProvince", locationProvince)
        putOptString("locationAddress", locationAddress)
        putOptString("stationName", stationName)
        putOptString("stallName", stallName)
        putOptLong("tripId", tripId)
        putOptLong("vehicleId", vehicleId)
        putOptString("notes", notes)
        putOptString("tags", tags)
        // Canonical form: an object per receipt with { file, originalName }.
        // file is the basename stripped of the implicit "receipts/" prefix.
        // originalName is whatever the SAF picker reported (may be absent).
        put(
            "receipts",
            JSONArray().also { arr ->
                receipts.forEach { r ->
                    arr.put(
                        JSONObject().apply {
                            put("file", r.filePath.removePrefix("$RECEIPT_DIR_IN_FILES/"))
                            putOptString("originalName", r.originalFileName)
                        },
                    )
                }
            },
        )
        // Legacy single-receipt field, kept so older builds restoring a
        // newer backup can still pick up at least one file per session.
        // The new reader path below prefers the [receipts] array.
        putOptString(
            "receiptFile",
            receipts.firstOrNull()?.filePath?.removePrefix("$RECEIPT_DIR_IN_FILES/"),
        )
        putOptDouble("latitude", latitude)
        putOptDouble("longitude", longitude)
        put("continuesPrevious", continuesPrevious)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }

    /* --- restore helpers --- */

    private fun readZipToTemp(uri: Uri, tempDir: File): JSONObject? {
        var json: JSONObject? = null
        var totalBytes = 0L
        var entryCount = 0
        context.contentResolver.openInputStream(uri).use { stream ->
            requireNotNull(stream) { "Could not open backup file." }
            ZipInputStream(BufferedInputStream(stream)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) {
                        zip.closeEntry()
                        continue
                    }
                    if (++entryCount > MAX_BACKUP_ENTRIES) {
                        throw IOException("Backup contains too many entries (>$MAX_BACKUP_ENTRIES).")
                    }
                    val written: Long = when {
                        entry.name == BACKUP_JSON -> {
                            val bytes = zip.readBytesBounded(MAX_JSON_BYTES)
                            json = JSONObject(String(bytes, Charsets.UTF_8))
                            bytes.size.toLong()
                        }
                        entry.name.startsWith(IMAGE_DIR_IN_ZIP) ->
                            extractInto(zip, entry.name, IMAGE_DIR_IN_ZIP, File(tempDir, IMAGE_DIR_IN_FILES))
                        entry.name.startsWith(RECEIPT_DIR_IN_ZIP) ->
                            extractInto(zip, entry.name, RECEIPT_DIR_IN_ZIP, File(tempDir, RECEIPT_DIR_IN_FILES))
                        else -> 0L
                    }
                    totalBytes += written
                    if (totalBytes > MAX_TOTAL_BYTES) {
                        throw IOException("Backup exceeds the $MAX_TOTAL_BYTES byte total decompression cap.")
                    }
                    zip.closeEntry()
                }
            }
        }
        return json
    }

    private fun parsePayload(json: JSONObject): BackupPayload? {
        val schemaVersion = json.optInt("schemaVersion", -1)
        if (schemaVersion <= 0 || schemaVersion > SCHEMA_VERSION) return null

        // Every legitimate backup contains all three arrays (even if empty).
        // Reject payloads that are missing any of them — restore() would
        // otherwise call deleteAll() on every table and then insert zero rows
        // from a "{ \"schemaVersion\": 5 }" payload, silently wiping the
        // user's database.
        val vehiclesArr = json.optJSONArray("vehicles") ?: return null
        val tripsArr = json.optJSONArray("trips") ?: return null
        val sessionsArr = json.optJSONArray("sessions") ?: return null

        return BackupPayload(
            vehicles = vehiclesArr.mapObjects { v ->
                RawVehicle(
                    id = v.getLong("id"),
                    name = v.getString("name"),
                    year = v.optLongOrNull("year")?.toInt(),
                    make = v.optStringOrNull("make"),
                    model = v.optStringOrNull("model"),
                    trim = v.optStringOrNull("trim"),
                    batteryCapacityKwh = v.optDoubleOrNull("batteryCapacityKwh"),
                    nominalRangeKm = v.optLongOrNull("nominalRangeKm")?.toInt(),
                    vin = v.optStringOrNull("vin"),
                    notes = v.optStringOrNull("notes"),
                    imageFile = v.optStringOrNull("imageFile"),
                    isDefault = v.optBoolean("isDefault", false),
                    createdAt = v.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = v.optLong("updatedAt", System.currentTimeMillis()),
                )
            },
            trips = tripsArr.mapObjects { t ->
                RawTrip(
                    id = t.getLong("id"),
                    name = t.getString("name"),
                    startDate = t.optLongOrNull("startDate"),
                    endDate = t.optLongOrNull("endDate"),
                    startOdometerKm = t.optDoubleOrNull("startOdometerKm"),
                    endOdometerKm = t.optDoubleOrNull("endOdometerKm"),
                    notes = t.optStringOrNull("notes"),
                    pinColor = t.optStringOrNull("pinColor"),
                    createdAt = t.optLong("createdAt", System.currentTimeMillis()),
                )
            },
            sessions = sessionsArr.mapObjects { s ->
                RawSession(
                    id = s.getLong("id"),
                    sessionStart = s.getLong("sessionStart"),
                    durationSeconds = s.optLongOrNull("durationSeconds"),
                    waitTimeMinutes = s.optLongOrNull("waitTimeMinutes")?.toInt(),
                    odometerKm = s.optDoubleOrNull("odometerKm"),
                    energyKwh = s.optDoubleOrNull("energyKwh"),
                    totalCost = s.optDoubleOrNull("totalCost"),
                    currency = s.optString("currency", "CAD"),
                    postedEnergyPricePerKwh = s.optDoubleOrNull("postedEnergyPricePerKwh"),
                    postedTimeRatePerMin = s.optDoubleOrNull("postedTimeRatePerMin"),
                    postedMaxPowerKw = s.optDoubleOrNull("postedMaxPowerKw"),
                    batteryStartPct = s.optLongOrNull("batteryStartPct")?.toInt(),
                    batteryEndPct = s.optLongOrNull("batteryEndPct")?.toInt(),
                    chargingType = runCatching {
                        ChargingType.valueOf(s.optString("chargingType", "DC_FAST"))
                    }.getOrDefault(ChargingType.DC_FAST),
                    pricingModel = runCatching {
                        PricingModel.valueOf(s.optString("pricingModel", "PER_KWH"))
                    }.getOrDefault(PricingModel.PER_KWH),
                    brand = s.optStringOrNull("brand"),
                    locationCity = s.optStringOrNull("locationCity"),
                    locationProvince = s.optStringOrNull("locationProvince"),
                    locationAddress = s.optStringOrNull("locationAddress"),
                    stationName = s.optStringOrNull("stationName"),
                    stallName = s.optStringOrNull("stallName"),
                    tripId = s.optLongOrNull("tripId"),
                    vehicleId = s.optLongOrNull("vehicleId"),
                    notes = s.optStringOrNull("notes"),
                    tags = s.optStringOrNull("tags"),
                    // Receipt parsing handles three historical forms:
                    //  1) JSON array of { file, originalName? } objects — current.
                    //  2) JSON array of plain basename strings — intermediate.
                    //  3) Legacy "receiptFile" field for v5 backups.
                    receipts = s.optJSONArray("receipts")?.let { arr ->
                        (0 until arr.length()).mapNotNull { i ->
                            val obj = arr.optJSONObject(i)
                            if (obj != null) {
                                val file = obj.optString("file").takeIf { it.isNotBlank() }
                                    ?: return@mapNotNull null
                                RawReceipt(file = file, originalName = obj.optStringOrNull("originalName"))
                            } else {
                                arr.optString(i).takeIf { it.isNotBlank() }
                                    ?.let { RawReceipt(file = it, originalName = null) }
                            }
                        }
                    } ?: listOfNotNull(
                        s.optStringOrNull("receiptFile")
                            ?.let { RawReceipt(file = it, originalName = null) },
                    ),
                    latitude = s.optDoubleOrNull("latitude"),
                    longitude = s.optDoubleOrNull("longitude"),
                    continuesPrevious = s.optBoolean("continuesPrevious", false),
                    createdAt = s.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = s.optLong("updatedAt", System.currentTimeMillis()),
                )
            },
        )
    }

    /** Extract one zip entry to [targetDir] under its basename, returning the
     *  bytes written. Caps the per-entry size; throws if exceeded so the
     *  outer transaction never sees a partial bomb. */
    private fun extractInto(
        zip: ZipInputStream,
        entryName: String,
        prefix: String,
        targetDir: File,
    ): Long {
        targetDir.mkdirs()
        val rel = entryName.removePrefix(prefix)
        // Guard against zip-slip — keep only the basename.
        val safeName = File(rel).name
        if (safeName.isBlank()) return 0L
        val out = File(targetDir, safeName)
        // Two entries that reduce to the same basename (e.g. `vehicles/foo.jpg`
        // and `vehicles/sub/foo.jpg`) would otherwise clobber each other on
        // disk — and corrupt the first mid-write if the second errors out.
        // The DB only ever references one file per basename, so keep the
        // first and silently drop later duplicates.
        if (out.exists()) return 0L
        return out.outputStream().use { os -> zip.copyBoundedTo(os, MAX_ENTRY_BYTES) }
    }

    /** Copy [this] into [out] until EOF or [limit] bytes, whichever comes
     *  first. Throws when [limit] is exceeded so a single zip-bomb entry
     *  can't OOM the process or fill cacheDir. */
    private fun InputStream.copyBoundedTo(out: OutputStream, limit: Long): Long {
        val buf = ByteArray(8 * 1024)
        var total = 0L
        while (true) {
            val n = read(buf)
            if (n < 0) break
            total += n
            if (total > limit) {
                throw IOException("Zip entry exceeds the $limit byte size cap.")
            }
            out.write(buf, 0, n)
        }
        return total
    }

    /** Read [this] fully into a ByteArray, but no more than [limit] bytes. */
    private fun InputStream.readBytesBounded(limit: Long): ByteArray {
        val buf = ByteArrayOutputStream()
        copyBoundedTo(buf, limit)
        return buf.toByteArray()
    }

    /** Copy each [name] from [tempSubdir] into filesDir/[targetSubdir]/.
     *  Called only after the DB transaction commits, so a partial failure
     *  here at worst leaves a single image missing. */
    private fun installFiles(tempSubdir: File, targetSubdir: String, names: Set<String>) {
        if (names.isEmpty()) return
        val targetDir = File(context.filesDir, targetSubdir).apply { mkdirs() }
        names.forEach { name ->
            val src = File(tempSubdir, name)
            if (!src.exists()) return@forEach
            val dest = File(targetDir, name)
            try {
                src.inputStream().use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
            } catch (_: IOException) {
                // Skip silently – row just won't have a media file after restore.
            }
        }
    }

    private fun cleanOrphans(subdir: String, referencedPaths: Set<String>) {
        val dir = File(context.filesDir, subdir)
        if (!dir.isDirectory) return
        val keepNames = referencedPaths
            .map { it.removePrefix("$subdir/") }
            .toSet()
        dir.listFiles()?.forEach { file ->
            // Only delete files that match the UUID-name pattern we use for
            // receipts (.jpg/.pdf) and vehicle photos (.jpg). If a future
            // feature ever drops differently-named files in these
            // directories, this leaves them alone instead of silently
            // wiping them on every restore.
            if (MANAGED_FILE_PATTERN.matches(file.name) && file.name !in keepNames) {
                file.delete()
            }
        }
    }
}

private val MANAGED_FILE_PATTERN = Regex(
    "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.(jpg|pdf)\$"
)

/** Strip any path components from a JSON-supplied filename and return the
 *  basename. Returns null if the result is blank. Same defense extractInto
 *  applies to zip entry names — guards against zip-slip via the JSON. */
private fun sanitizedBasename(raw: String): String? =
    File(raw).name.takeIf { it.isNotBlank() }

/* --- raw payload + JSON helpers --- */

private data class BackupPayload(
    val vehicles: List<RawVehicle>,
    val trips: List<RawTrip>,
    val sessions: List<RawSession>,
)

private data class RawVehicle(
    val id: Long,
    val name: String,
    val year: Int?,
    val make: String?,
    val model: String?,
    val trim: String?,
    val batteryCapacityKwh: Double?,
    val nominalRangeKm: Int?,
    val vin: String?,
    val notes: String?,
    val imageFile: String?,
    val isDefault: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

private data class RawTrip(
    val id: Long,
    val name: String,
    val startDate: Long?,
    val endDate: Long?,
    val startOdometerKm: Double?,
    val endOdometerKm: Double?,
    val notes: String?,
    val pinColor: String?,
    val createdAt: Long,
)

/** Receipt entry parsed from backup JSON. file = basename only (no
 *  "receipts/" prefix); originalName is null when older formats didn't
 *  carry it. */
private data class RawReceipt(val file: String, val originalName: String?)

private data class RawSession(
    val id: Long,
    val sessionStart: Long,
    val durationSeconds: Long?,
    val waitTimeMinutes: Int?,
    val odometerKm: Double?,
    val energyKwh: Double?,
    val totalCost: Double?,
    val currency: String,
    val postedEnergyPricePerKwh: Double?,
    val postedTimeRatePerMin: Double?,
    val postedMaxPowerKw: Double?,
    val batteryStartPct: Int?,
    val batteryEndPct: Int?,
    val chargingType: ChargingType,
    val pricingModel: PricingModel,
    val brand: String?,
    val locationCity: String?,
    val locationProvince: String?,
    val locationAddress: String?,
    val stationName: String?,
    val stallName: String?,
    val tripId: Long?,
    val vehicleId: Long?,
    val notes: String?,
    val tags: String?,
    /** Receipts attached to this session, parsed from one of three JSON
     *  forms (see fromJson). Empty when the session has no attachments. */
    val receipts: List<RawReceipt>,
    val latitude: Double?,
    val longitude: Double?,
    val continuesPrevious: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

private fun JSONObject.putOptString(key: String, value: String?) {
    if (value == null) put(key, JSONObject.NULL) else put(key, value)
}

private fun JSONObject.putOptLong(key: String, value: Long?) {
    if (value == null) put(key, JSONObject.NULL) else put(key, value)
}

private fun JSONObject.putOptDouble(key: String, value: Double?) {
    if (value == null) put(key, JSONObject.NULL) else put(key, value)
}

private fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key) || !has(key)) null else optString(key, "").takeIf { it.isNotEmpty() }

private fun JSONObject.optLongOrNull(key: String): Long? =
    if (isNull(key) || !has(key)) null else optLong(key)

private fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (isNull(key) || !has(key)) null else optDouble(key).takeIf { !it.isNaN() }

/** Map JSON objects to [T], silently skipping any row whose [transform] throws.
 *  A single malformed row in a 100-session backup shouldn't fail the whole
 *  restore — we'd rather drop the row and continue. (The destructive
 *  deleteAll runs after parsePayload returns, so a parse-time throw inside
 *  any non-skipping caller would still abort safely; this helper just
 *  trades that abort for partial-success behavior.) */
private inline fun <T : Any> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
    (0 until length()).mapNotNull { idx ->
        runCatching { transform(getJSONObject(idx)) }.getOrNull()
    }
