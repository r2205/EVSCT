package com.evsct.app.data.csv

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.evsct.app.data.db.EvsctDatabase
import com.evsct.app.data.entity.Trip
import com.evsct.app.data.entity.Vehicle
import com.evsct.app.data.repository.SessionRepository
import com.evsct.app.data.repository.TripRepository
import com.evsct.app.data.repository.VehicleRepository
import com.evsct.app.ui.map.TripPinColor
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.Writer
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Minimal RFC-4180-ish CSV writer/reader. Handles quoting of fields containing comma, quote, or newline. */
object Csv {
    /** Leading characters that some spreadsheet apps treat as the start of a
     *  formula. A field beginning with one of these — exported by EVSCT and
     *  reopened in Excel/Sheets/Numbers — could execute attacker-controlled
     *  formulas (e.g., a notes field starting with `=cmd|...`). */
    private val FORMULA_TRIGGERS = setOf('=', '+', '-', '@', '\t', '\r')

    fun encodeField(value: String?): String {
        if (value == null) return ""
        // Defuse formula injection by prefixing dangerous leading chars with
        // a single quote — spreadsheet apps strip that prefix and treat the
        // rest as literal text. decodeField mirrors this on import so EVSCT
        // round-trips the user's original value losslessly.
        val sanitized = if (value.isNotEmpty() && value[0] in FORMULA_TRIGGERS) "'$value" else value
        val needsQuote = sanitized.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        val escaped = sanitized.replace("\"", "\"\"")
        return if (needsQuote) "\"$escaped\"" else escaped
    }

    /** Reverse of the [encodeField] formula-injection prefix: strip a leading
     *  `'` only when it sits in front of a known formula trigger. A user's
     *  legitimate `'Tesla` brand stays intact (the second char isn't a
     *  trigger). */
    private fun decodeField(value: String): String =
        if (value.length >= 2 && value[0] == '\'' && value[1] in FORMULA_TRIGGERS)
            value.substring(1)
        else value

    fun encodeRow(fields: List<String?>): String =
        fields.joinToString(",") { encodeField(it) }

    fun parseLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes -> when {
                    c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                        cur.append('"'); i++
                    }
                    c == '"' -> inQuotes = false
                    else -> cur.append(c)
                }
                c == '"' -> inQuotes = true
                c == ',' -> { out += decodeField(cur.toString()); cur.clear() }
                else -> cur.append(c)
            }
            i++
        }
        out += decodeField(cur.toString())
        return out
    }

    /** Reads CSV from text, accounting for embedded newlines inside quoted fields. */
    fun parseAll(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val cur = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes -> {
                    cur.append(c)
                    if (c == '"') {
                        if (i + 1 < text.length && text[i + 1] == '"') {
                            // Escaped quote — consume both chars, stay quoted.
                            // Lookahead (rather than counting trailing quotes)
                            // so a field whose content *starts* with a quote
                            // can't desync the quoted state and swallow the
                            // row-terminating newline.
                            cur.append('"')
                            i++
                        } else {
                            inQuotes = false
                        }
                    }
                }
                c == '"' -> { cur.append(c); inQuotes = true }
                c == '\n' -> { rows += parseLine(cur.toString().trimEnd('\r')); cur.clear() }
                else -> cur.append(c)
            }
            i++
        }
        if (cur.isNotEmpty()) rows += parseLine(cur.toString().trimEnd('\r'))
        return rows
    }
}

data class CsvImportResult(val imported: Int, val skipped: Int)

/** Successful prepare-for-share. The CSV lives in [file], and [sessions] is
 *  the count just written so the screen can surface the same status text as
 *  Save does. */
data class PreparedShareCsv(val file: File, val sessions: Int)

sealed interface PrepareCsvShareResult {
    data class Success(val prepared: PreparedShareCsv) : PrepareCsvShareResult
    data class Failure(val message: String) : PrepareCsvShareResult
}

/** Subdirectory under cacheDir where prepare-for-share CSVs land. Mirrored
 *  in res/xml/file_paths.xml so FileProvider can hand out content:// URIs. */
private const val CSV_SHARE_DIR_IN_CACHE = "csv-share"

@Singleton
class CsvIo @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: EvsctDatabase,
    private val sessionRepository: SessionRepository,
    private val tripRepository: TripRepository,
    private val vehicleRepository: VehicleRepository,
) {
    suspend fun export(uri: Uri): Int = withContext(Dispatchers.IO) {
        // Stage in cacheDir before touching the destination — mirrors
        // BackupIo.export. "wt" truncates an existing file the user is
        // overwriting before a single row is read, so any failure while
        // walking the DB used to destroy the previous export. A null
        // output stream now surfaces as an error instead of silently
        // reporting "Exported 0 sessions".
        val staging = File(context.cacheDir, "csv-staging-${UUID.randomUUID()}.csv")
        val count = try {
            staging.outputStream().use { os ->
                OutputStreamWriter(os, Charsets.UTF_8).use { w -> writeCsvTo(w) }
            }
        } catch (e: Exception) {
            staging.delete()
            throw e
        }
        val out = context.contentResolver.openOutputStream(uri, "wt")
            ?: run {
                staging.delete()
                throw IOException("Could not open output for writing.")
            }
        try {
            out.use { output -> staging.inputStream().use { it.copyTo(output) } }
            count
        } catch (e: Exception) {
            throw IOException(
                "Could not write to the chosen location — the destination file may be " +
                    "incomplete. Export again before relying on it." +
                    (e.message?.let { " ($it)" } ?: ""),
                e,
            )
        } finally {
            staging.delete()
        }
    }

    /**
     * Build the same CSV [export] writes, but into a dedicated cache
     * subdirectory and return the resulting [File] so the caller can hand
     * it to an Android share-sheet via FileProvider. Older share files are
     * cleared first so the cache doesn't accumulate after repeated shares.
     */
    suspend fun prepareShareFile(filenamePrefix: String = "evsct-export"): PrepareCsvShareResult =
        withContext(Dispatchers.IO) {
            try {
                val shareDir = File(context.cacheDir, CSV_SHARE_DIR_IN_CACHE).apply {
                    mkdirs()
                    listFiles()?.forEach { it.delete() }
                }
                val ts = java.text.SimpleDateFormat("yyyy-MM-dd-HHmm", java.util.Locale.US)
                    .format(java.util.Date())
                val target = File(shareDir, "$filenamePrefix-$ts.csv")
                val count = target.outputStream().use { os ->
                    OutputStreamWriter(os, Charsets.UTF_8).use { w -> writeCsvTo(w) }
                }
                PrepareCsvShareResult.Success(PreparedShareCsv(file = target, sessions = count))
            } catch (e: Exception) {
                PrepareCsvShareResult.Failure(e.message ?: "Could not prepare CSV for share")
            }
        }

    /** Stream the CSV header + every session row into [writer] and return the
     *  row count so callers can surface it. Trip/vehicle names are joined in
     *  via the existing repositories so the format is identical regardless of
     *  whether the destination is a SAF URI or a cacheDir file. */
    private suspend fun writeCsvTo(writer: Writer): Int {
        val sessions = sessionRepository.observeAll().first()
        val trips = tripRepository.observeAll().first().associate { it.id to it.name }
        val vehicles = vehicleRepository.observeAll().first().associate { it.id to it.name }
        writer.appendLine(Csv.encodeRow(CsvFormat.HEADERS))
        for (s in sessions) {
            writer.appendLine(
                Csv.encodeRow(
                    CsvFormat.toRow(
                        session = s,
                        tripName = trips[s.tripId],
                        vehicleName = vehicles[s.vehicleId],
                    )
                )
            )
        }
        return sessions.size
    }

    suspend fun import(uri: Uri, replaceExisting: Boolean): CsvImportResult = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { inp ->
            BufferedReader(InputStreamReader(inp, Charsets.UTF_8)).readText()
        } ?: return@withContext CsvImportResult(0, 0)

        val rows = Csv.parseAll(text)
        if (rows.isEmpty()) return@withContext CsvImportResult(0, 0)
        val headers = rows.first().map { it.trim() }

        // Read existing trip/vehicle name lookups outside the transaction so
        // the Flow .first() collectors don't run on the transaction connection
        // (mirrors the pattern in BackupIo.restore). Pin colors are pre-read
        // for the same reason — see the trip-creation comment below.
        val tripIdByName = mutableMapOf<String, Long>()
        val usedPinColors = mutableListOf<String>()
        tripRepository.observeAll().first().forEach { trip ->
            tripIdByName[trip.name] = trip.id
            trip.pinColor?.let { usedPinColors += it }
        }
        val vehicleIdByName = mutableMapOf<String, Long>()
        vehicleRepository.observeAll().first().forEach { vehicleIdByName[it.name] = it.id }

        var imported = 0
        var skipped = 0

        // Atomic: either every row lands or none of it does. Without this a
        // killed process or a single bad row mid-loop after deleteAll() leaves
        // the user with a partially-wiped database and no rollback.
        database.withTransaction {
            if (replaceExisting) sessionRepository.deleteAll()

            for (i in 1 until rows.size) {
                val row = rows[i]
                if (row.all { it.isBlank() }) continue
                val parsed = CsvFormat.fromRow(headers, row)
                if (parsed == null) { skipped++; continue }
                val tripId = parsed.tripName?.takeIf { it.isNotBlank() }?.let { name ->
                    tripIdByName.getOrPut(name) {
                        // Assign the pin color here from the pre-read list
                        // rather than letting TripRepository.upsert auto-pick:
                        // its auto-pick collects a DAO Flow, which must not run
                        // inside withTransaction (the exact pattern the comment
                        // above avoids) — and its read of committed state would
                        // hand several new trips in one import the same color.
                        val color = TripPinColor.nextDefault(usedPinColors).name
                        usedPinColors += color
                        tripRepository.upsert(Trip(name = name, pinColor = color))
                    }
                }
                val vehicleId = parsed.vehicleName?.takeIf { it.isNotBlank() }?.let { name ->
                    vehicleIdByName.getOrPut(name) { vehicleRepository.upsert(Vehicle(name = name)) }
                }
                sessionRepository.upsert(parsed.session.copy(tripId = tripId, vehicleId = vehicleId))
                imported++
            }
        }
        CsvImportResult(imported, skipped)
    }
}
