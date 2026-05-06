package com.evsct.app.data.csv

import android.content.Context
import android.net.Uri
import com.evsct.app.data.entity.Trip
import com.evsct.app.data.entity.Vehicle
import com.evsct.app.data.repository.SessionRepository
import com.evsct.app.data.repository.TripRepository
import com.evsct.app.data.repository.VehicleRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
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
        for (c in text) {
            when {
                inQuotes -> {
                    cur.append(c)
                    if (c == '"') {
                        // toggle handled in line parser; we need to track quotes across newlines
                        var quoteCount = 0
                        for (j in cur.length - 1 downTo 0) {
                            if (cur[j] == '"') quoteCount++ else break
                        }
                        if (quoteCount % 2 == 1) inQuotes = false
                    }
                }
                c == '"' -> { cur.append(c); inQuotes = true }
                c == '\n' -> { rows += parseLine(cur.toString().trimEnd('\r')); cur.clear() }
                else -> cur.append(c)
            }
        }
        if (cur.isNotEmpty()) rows += parseLine(cur.toString().trimEnd('\r'))
        return rows
    }
}

data class CsvImportResult(val imported: Int, val skipped: Int)

@Singleton
class CsvIo @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionRepository: SessionRepository,
    private val tripRepository: TripRepository,
    private val vehicleRepository: VehicleRepository,
) {
    suspend fun export(uri: Uri): Int = withContext(Dispatchers.IO) {
        val sessions = sessionRepository.observeAll().first()
        val trips = tripRepository.observeAll().first().associate { it.id to it.name }
        val vehicles = vehicleRepository.observeAll().first().associate { it.id to it.name }
        context.contentResolver.openOutputStream(uri, "wt")?.use { os ->
            OutputStreamWriter(os, Charsets.UTF_8).use { w ->
                w.appendLine(Csv.encodeRow(CsvFormat.HEADERS))
                for (s in sessions) {
                    w.appendLine(
                        Csv.encodeRow(
                            CsvFormat.toRow(
                                session = s,
                                tripName = trips[s.tripId],
                                vehicleName = vehicles[s.vehicleId],
                            )
                        )
                    )
                }
            }
        }
        sessions.size
    }

    suspend fun import(uri: Uri, replaceExisting: Boolean): CsvImportResult = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { inp ->
            BufferedReader(InputStreamReader(inp, Charsets.UTF_8)).readText()
        } ?: return@withContext CsvImportResult(0, 0)

        val rows = Csv.parseAll(text)
        if (rows.isEmpty()) return@withContext CsvImportResult(0, 0)
        val headers = rows.first().map { it.trim() }

        if (replaceExisting) sessionRepository.deleteAll()

        val tripIdByName = mutableMapOf<String, Long>()
        tripRepository.observeAll().first().forEach { tripIdByName[it.name] = it.id }
        val vehicleIdByName = mutableMapOf<String, Long>()
        vehicleRepository.observeAll().first().forEach { vehicleIdByName[it.name] = it.id }

        var imported = 0
        var skipped = 0
        for (i in 1 until rows.size) {
            val row = rows[i]
            if (row.all { it.isBlank() }) continue
            val parsed = CsvFormat.fromRow(headers, row)
            if (parsed == null) { skipped++; continue }
            val tripId = parsed.tripName?.takeIf { it.isNotBlank() }?.let { name ->
                tripIdByName.getOrPut(name) { tripRepository.upsert(Trip(name = name)) }
            }
            val vehicleId = parsed.vehicleName?.takeIf { it.isNotBlank() }?.let { name ->
                vehicleIdByName.getOrPut(name) { vehicleRepository.upsert(Vehicle(name = name)) }
            }
            sessionRepository.upsert(parsed.session.copy(tripId = tripId, vehicleId = vehicleId))
            imported++
        }
        CsvImportResult(imported, skipped)
    }
}
