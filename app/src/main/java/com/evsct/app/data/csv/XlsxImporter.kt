package com.evsct.app.data.csv

import android.content.Context
import android.net.Uri
import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.data.entity.ChargingType
import com.evsct.app.data.entity.PricingModel
import com.evsct.app.data.repository.SessionRepository
import com.evsct.app.data.repository.VehicleRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.openxml4j.util.ZipSecureFile
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.xssf.usermodel.XSSFWorkbook

data class XlsxImportResult(val imported: Int, val skipped: Int)

// Decompression caps for the legacy XLSX importer. A real charging-log
// sheet is comfortably under these — they exist to short-circuit
// zip/OOXML bombs that decompress a few KB of input into gigabytes.
private const val MAX_XLSX_ENTRIES: Long = 2_000L
private const val MAX_XLSX_ENTRY_BYTES: Long = 50L * 1024 * 1024

/**
 * Imports the legacy "DC Fast Charging.xlsx" sheet with the column layout the user has been
 * maintaining. Heuristics live here so the production app stays clean.
 */
@Singleton
class XlsxImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionRepository: SessionRepository,
    private val vehicleRepository: VehicleRepository,
) {

    suspend fun import(uri: Uri): XlsxImportResult = withContext(Dispatchers.IO) {
        // POI hardening against malicious XLSX (zip-bomb / OOXML-bomb) —
        // these are static so we set them before every import. Defaults in
        // POI 5.x are reasonable for compression-ratio but the per-entry
        // size cap is effectively unbounded out of the box.
        ZipSecureFile.setMinInflateRatio(0.01)
        ZipSecureFile.setMaxFileCount(MAX_XLSX_ENTRIES)
        ZipSecureFile.setMaxEntrySize(MAX_XLSX_ENTRY_BYTES)
        ZipSecureFile.setMaxTextSize(MAX_XLSX_ENTRY_BYTES)

        var imported = 0
        var skipped = 0
        val sessions = mutableListOf<ChargingSession>()
        val defaultVehicleId = vehicleRepository.findDefault()?.id

        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Could not open file" }
            XSSFWorkbook(input).use { wb ->
                val sheet = wb.getSheetAt(0)
                var headerSeen = false
                for (r in 0..sheet.lastRowNum) {
                    val row = sheet.getRow(r)
                    val parsed = parseRow(row, headerSeen)
                    when (parsed) {
                        ParsedRow.Header -> headerSeen = true
                        ParsedRow.Skip -> Unit
                        ParsedRow.Invalid -> if (headerSeen) skipped++
                        is ParsedRow.Session -> {
                            sessions += parsed.session.copy(vehicleId = defaultVehicleId)
                            imported++
                        }
                    }
                }
            }
        }
        if (sessions.isNotEmpty()) sessionRepository.insertAll(sessions)
        XlsxImportResult(imported, skipped)
    }

    private sealed interface ParsedRow {
        data object Header : ParsedRow
        data object Skip : ParsedRow
        data object Invalid : ParsedRow
        data class Session(val session: ChargingSession) : ParsedRow
    }

    private fun parseRow(row: Row?, headerSeen: Boolean): ParsedRow {
        if (row == null) return ParsedRow.Skip
        val first = row.getCell(0)?.toStringSafe()?.trim()?.uppercase()
        if (first == "DATE") return ParsedRow.Header
        if (!headerSeen) return ParsedRow.Skip

        val date = row.getCell(0)
        val time = row.getCell(1)
        val sessionStart = combineDateTime(date, time) ?: return ParsedRow.Invalid

        val mileage = row.getCell(2)?.numericOrNull()
        val energy = row.getCell(3)?.numericOrNull()
        val cost = row.getCell(4)?.numericOrNull()
        val durationCell = row.getCell(5)
        val durationSeconds = durationToSeconds(durationCell)
        val postedKwh = row.getCell(7)?.numericOrNull()
        val postedTimeRate = row.getCell(9)?.numericOrNull()
        val postedMaxKw = row.getCell(12)?.numericOrNull()
        val battStart = row.getCell(13)?.percentOrNull()
        val battEnd = row.getCell(14)?.percentOrNull()
        val brand = row.getCell(15)?.toStringSafe()?.trim()?.takeIf { it.isNotEmpty() }
        val cityProv = row.getCell(16)?.toStringSafe()?.trim()
        val (city, prov) = splitCityProv(cityProv)
        val address = row.getCell(17)?.toStringSafe()?.trim()?.takeIf { it.isNotEmpty() }
        val station = row.getCell(18)?.toStringSafe()?.trim()?.takeIf { it.isNotEmpty() }
        val notes = row.getCell(19)?.toStringSafe()?.trim()?.takeIf { it.isNotEmpty() }

        val pricing = inferPricing(cost, energy, postedKwh, postedTimeRate)
        val type = inferType(postedMaxKw, energy, durationSeconds)

        return ParsedRow.Session(
            // Same boundary gate as the CSV path: legacy sheets are
            // hand-maintained, and impossible values (battery > 100%,
            // negative energy) would silently poison downstream math.
            ImportSanitizer.sanitize(
                ChargingSession(
                    sessionStart = sessionStart,
                    durationSeconds = durationSeconds,
                    odometerKm = mileage,
                    energyKwh = energy,
                    totalCost = cost,
                    currency = "CAD",
                    postedEnergyPricePerKwh = postedKwh,
                    postedTimeRatePerMin = postedTimeRate,
                    postedMaxPowerKw = postedMaxKw,
                    batteryStartPct = battStart,
                    batteryEndPct = battEnd,
                    chargingType = type,
                    pricingModel = pricing,
                    brand = brand,
                    locationCity = city,
                    locationProvince = prov,
                    locationAddress = address,
                    stationName = station,
                    notes = notes,
                )
            )
        )
    }

    /**
     * Battery cells in the legacy sheet are %-formatted (85% stored as
     * 0.85), but sheets from other tools often hold a plain 85 in an
     * unformatted cell. Use the cell's number format to pick the scale;
     * [ImportSanitizer.cellToPercent] holds the fallback heuristic for
     * unformatted cells and the 0–100 range gate.
     */
    private fun Cell.percentOrNull(): Int? {
        val raw = numericOrNull() ?: return null
        val isPercentFormatted = try {
            cellStyle?.dataFormatString?.contains('%') == true
        } catch (_: Exception) {
            false
        }
        return ImportSanitizer.cellToPercent(raw, isPercentFormatted)
    }

    private fun Cell.numericOrNull(): Double? {
        return try {
            when (cellType) {
                CellType.NUMERIC -> numericCellValue
                CellType.STRING -> stringCellValue.trim().toDoubleOrNull()
                CellType.FORMULA -> when (cachedFormulaResultType) {
                    CellType.NUMERIC -> numericCellValue
                    CellType.STRING -> stringCellValue.trim().toDoubleOrNull()
                    else -> null
                }
                else -> null
            }
        } catch (_: Exception) { null }
    }

    private fun Cell.toStringSafe(): String = try {
        when (cellType) {
            CellType.STRING -> stringCellValue
            CellType.NUMERIC -> if (DateUtil.isCellDateFormatted(this)) dateCellValue.toString() else numericCellValue.toString()
            CellType.BOOLEAN -> booleanCellValue.toString()
            CellType.FORMULA -> when (cachedFormulaResultType) {
                CellType.STRING -> stringCellValue
                CellType.NUMERIC -> numericCellValue.toString()
                else -> ""
            }
            else -> ""
        }
    } catch (_: Exception) { "" }

    private fun combineDateTime(dateCell: Cell?, timeCell: Cell?): Long? {
        if (dateCell == null || dateCell.cellType != CellType.NUMERIC) return null
        if (!DateUtil.isCellDateFormatted(dateCell)) return null
        val date: Date = dateCell.dateCellValue ?: return null
        val cal = Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (timeCell != null && timeCell.cellType == CellType.NUMERIC) {
            val frac = timeCell.numericCellValue
            val totalSeconds = (frac * 24 * 3600).toLong()
            cal.add(Calendar.SECOND, totalSeconds.toInt())
        }
        return cal.timeInMillis
    }

    /** Excel duration cells store fraction-of-a-day. */
    private fun durationToSeconds(cell: Cell?): Long? {
        if (cell == null || cell.cellType != CellType.NUMERIC) return null
        val frac = cell.numericCellValue
        if (frac.isNaN()) return null
        return (frac * 24 * 3600).toLong()
    }

    private fun splitCityProv(text: String?): Pair<String?, String?> {
        if (text.isNullOrBlank()) return null to null
        val parts = text.split(",").map { it.trim() }
        return when (parts.size) {
            1 -> parts[0].takeIf { it.isNotEmpty() } to null
            else -> parts[0].takeIf { it.isNotEmpty() } to parts.last().takeIf { it.isNotEmpty() }
        }
    }

    private fun inferPricing(cost: Double?, energy: Double?, postedKwh: Double?, postedTime: Double?): PricingModel = when {
        cost == null || cost == 0.0 -> PricingModel.FREE
        postedKwh != null && postedTime != null -> PricingModel.HYBRID
        postedTime != null -> PricingModel.PER_MINUTE
        postedKwh != null -> PricingModel.PER_KWH
        energy != null && energy > 0 -> PricingModel.PER_KWH
        else -> PricingModel.FLAT
    }

    private fun inferType(postedMaxKw: Double?, energy: Double?, durationSeconds: Long?): ChargingType {
        // Anything under ~22kW is AC L2, under ~3kW is L1
        val avgPower = if (energy != null && durationSeconds != null && durationSeconds > 0)
            energy / (durationSeconds / 3600.0) else null
        val peak = postedMaxKw ?: avgPower
        return when {
            peak == null -> ChargingType.DC_FAST
            peak >= 24.0 -> ChargingType.DC_FAST
            peak >= 3.5 -> ChargingType.AC_L2
            else -> ChargingType.AC_L1
        }
    }
}
