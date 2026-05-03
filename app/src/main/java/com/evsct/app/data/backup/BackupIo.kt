package com.evsct.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.evsct.app.data.db.EvsctDatabase
import com.evsct.app.data.prefs.AppPreferences
import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.data.entity.ChargingType
import com.evsct.app.data.entity.PricingModel
import com.evsct.app.data.entity.Trip
import com.evsct.app.data.entity.Vehicle
import com.evsct.app.util.BackupReminderNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
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

private const val SCHEMA_VERSION = 4
private const val BACKUP_JSON = "backup.json"
private const val IMAGE_DIR_IN_ZIP = "vehicles/"
private const val IMAGE_DIR_IN_FILES = "vehicles"
private const val RECEIPT_DIR_IN_ZIP = "receipts/"
private const val RECEIPT_DIR_IN_FILES = "receipts"

sealed interface BackupResult {
    data class ExportSuccess(val sessions: Int, val trips: Int, val vehicles: Int) : BackupResult
    data class RestoreSuccess(val sessions: Int, val trips: Int, val vehicles: Int) : BackupResult
    data class Failure(val message: String) : BackupResult
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
    private val backupReminderNotifier: BackupReminderNotifier,
) {

    suspend fun export(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        try {
            val sessionDao = database.sessionDao()
            val tripDao = database.tripDao()
            val vehicleDao = database.vehicleDao()

            val vehicles = vehicleDao.observeAll().first()
            val trips = tripDao.observeAll().first()
            val sessions = sessionDao.observeAll().first()

            val json = buildBackupJson(vehicles, trips, sessions)
            val out = context.contentResolver.openOutputStream(uri, "wt")
                ?: return@withContext BackupResult.Failure("Could not open output for writing.")

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

                sessions.forEach { s ->
                    val rel = s.receiptImagePath ?: return@forEach
                    val file = File(context.filesDir, rel)
                    if (!file.exists()) return@forEach
                    val nameInZip = RECEIPT_DIR_IN_ZIP + file.name
                    zip.putNextEntry(ZipEntry(nameInZip))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            appPreferences.recordBackup()
            backupReminderNotifier.cancel()
            BackupResult.ExportSuccess(sessions.size, trips.size, vehicles.size)
        } catch (e: Exception) {
            BackupResult.Failure(e.message ?: "Export failed")
        }
    }

    suspend fun restore(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "backup-restore-${UUID.randomUUID()}")
        try {
            tempDir.mkdirs()
            val backupJson = readZipToTemp(uri, tempDir)
                ?: return@withContext BackupResult.Failure("Backup file is missing $BACKUP_JSON.")

            val payload = parsePayload(backupJson)
                ?: return@withContext BackupResult.Failure("Backup is malformed or unsupported.")

            // Move images from the temp extraction into the app's filesDir
            // before the DB swap so the new image paths are valid by the time
            // the new vehicle rows are inserted.
            val installedImages = installImages(tempDir, payload.vehicles)
            val installedReceipts = installReceipts(tempDir, payload.sessions)

            val sessionDao = database.sessionDao()
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
                            imagePath = raw.imageFile?.let { installedImages[it] },
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
                        receiptImagePath = raw.receiptFile?.let { installedReceipts[it] },
                        latitude = raw.latitude,
                        longitude = raw.longitude,
                        createdAt = raw.createdAt,
                        updatedAt = raw.updatedAt,
                    )
                }
                sessionDao.insertAll(sessionEntities)
            }

            // Best-effort: drop any image files in filesDir that weren't
            // referenced by the restored set. Old pre-restore images are now
            // orphaned and safe to remove.
            cleanOrphans(IMAGE_DIR_IN_FILES, installedImages.values.toSet())
            cleanOrphans(RECEIPT_DIR_IN_FILES, installedReceipts.values.toSet())

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
    ): JSONObject = JSONObject().apply {
        put("schemaVersion", SCHEMA_VERSION)
        put("exportedAt", System.currentTimeMillis())
        put("settings", JSONObject())  // reserved for future preferences

        put("vehicles", JSONArray().also { arr -> vehicles.forEach { arr.put(it.toJson()) } })
        put("trips", JSONArray().also { arr -> trips.forEach { arr.put(it.toJson()) } })
        put("sessions", JSONArray().also { arr -> sessions.forEach { arr.put(it.toJson()) } })
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

    private fun ChargingSession.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("sessionStart", sessionStart)
        putOptLong("durationSeconds", durationSeconds)
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
        // Strip the "receipts/" prefix; the directory is implicit in the zip.
        putOptString("receiptFile", receiptImagePath?.removePrefix("$RECEIPT_DIR_IN_FILES/"))
        putOptDouble("latitude", latitude)
        putOptDouble("longitude", longitude)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }

    /* --- restore helpers --- */

    private fun readZipToTemp(uri: Uri, tempDir: File): JSONObject? {
        var json: JSONObject? = null
        context.contentResolver.openInputStream(uri).use { stream ->
            requireNotNull(stream) { "Could not open backup file." }
            ZipInputStream(BufferedInputStream(stream)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) {
                        zip.closeEntry()
                        continue
                    }
                    when {
                        entry.name == BACKUP_JSON -> {
                            val bytes = zip.readBytes()
                            json = JSONObject(String(bytes, Charsets.UTF_8))
                        }
                        entry.name.startsWith(IMAGE_DIR_IN_ZIP) -> {
                            extractInto(zip, entry.name, IMAGE_DIR_IN_ZIP, File(tempDir, IMAGE_DIR_IN_FILES))
                        }
                        entry.name.startsWith(RECEIPT_DIR_IN_ZIP) -> {
                            extractInto(zip, entry.name, RECEIPT_DIR_IN_ZIP, File(tempDir, RECEIPT_DIR_IN_FILES))
                        }
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

        return BackupPayload(
            vehicles = json.optJSONArray("vehicles").orEmpty().mapObjects { v ->
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
            trips = json.optJSONArray("trips").orEmpty().mapObjects { t ->
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
            sessions = json.optJSONArray("sessions").orEmpty().mapObjects { s ->
                RawSession(
                    id = s.getLong("id"),
                    sessionStart = s.getLong("sessionStart"),
                    durationSeconds = s.optLongOrNull("durationSeconds"),
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
                    receiptFile = s.optStringOrNull("receiptFile"),
                    latitude = s.optDoubleOrNull("latitude"),
                    longitude = s.optDoubleOrNull("longitude"),
                    createdAt = s.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = s.optLong("updatedAt", System.currentTimeMillis()),
                )
            },
        )
    }

    /** Copies extracted images into filesDir/vehicles/, returns map<imageFile -> imagePath>. */
    private fun extractInto(
        zip: ZipInputStream,
        entryName: String,
        prefix: String,
        targetDir: File,
    ) {
        targetDir.mkdirs()
        val rel = entryName.removePrefix(prefix)
        // Guard against zip-slip — keep only the basename.
        val safeName = File(rel).name
        if (safeName.isBlank()) return
        val out = File(targetDir, safeName)
        out.outputStream().use { os -> zip.copyTo(os) }
    }

    private fun installImages(tempDir: File, vehicles: List<RawVehicle>): Map<String, String> {
        val tempImageDir = File(tempDir, IMAGE_DIR_IN_FILES)
        val targetDir = File(context.filesDir, IMAGE_DIR_IN_FILES).apply { mkdirs() }
        val map = mutableMapOf<String, String>()
        vehicles.forEach { v ->
            val name = v.imageFile ?: return@forEach
            val src = File(tempImageDir, name)
            if (!src.exists()) return@forEach
            val dest = File(targetDir, name)
            try {
                src.inputStream().use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                map[name] = "$IMAGE_DIR_IN_FILES/$name"
            } catch (_: IOException) {
                // Skip silently – vehicle just won't have an image after restore.
            }
        }
        return map
    }

    private fun installReceipts(tempDir: File, sessions: List<RawSession>): Map<String, String> {
        val tempReceiptDir = File(tempDir, RECEIPT_DIR_IN_FILES)
        val targetDir = File(context.filesDir, RECEIPT_DIR_IN_FILES).apply { mkdirs() }
        val map = mutableMapOf<String, String>()
        sessions.forEach { s ->
            val name = s.receiptFile ?: return@forEach
            val src = File(tempReceiptDir, name)
            if (!src.exists()) return@forEach
            val dest = File(targetDir, name)
            try {
                src.inputStream().use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                map[name] = "$RECEIPT_DIR_IN_FILES/$name"
            } catch (_: IOException) {
                // Skip silently – session just won't have a receipt after restore.
            }
        }
        return map
    }

    private fun cleanOrphans(subdir: String, referencedPaths: Set<String>) {
        val dir = File(context.filesDir, subdir)
        if (!dir.isDirectory) return
        val keepNames = referencedPaths
            .map { it.removePrefix("$subdir/") }
            .toSet()
        dir.listFiles()?.forEach { file ->
            if (file.name !in keepNames) file.delete()
        }
    }
}

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

private data class RawSession(
    val id: Long,
    val sessionStart: Long,
    val durationSeconds: Long?,
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
    val receiptFile: String?,
    val latitude: Double?,
    val longitude: Double?,
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

private fun JSONArray?.orEmpty(): JSONArray = this ?: JSONArray()

private inline fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
    (0 until length()).map { transform(getJSONObject(it)) }
