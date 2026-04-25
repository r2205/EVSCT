package com.evsct.app.data.csv

import android.content.Context
import android.net.Uri
import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.data.entity.ChargingType
import com.evsct.app.data.entity.PricingModel
import com.evsct.app.data.repository.SessionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.xssf.usermodel.XSSFWorkbook

data class XlsxImportResult(val imported: Int, val skipped: Int, val errors: List<String>)

/**
 * Imports the legacy "DC Fast Charging.xlsx" sheet with the column layout the user has been
 * maintaining. Heuristics live here so the production app stays clean.
 */
@Singleton
class XlsxImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionRepository: SessionRepository,
) {

    suspend fun import(uri: Uri): XlsxImportResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        var imported = 0
        var skipped = 0
        val sessions = mutableListOf<ChargingSession>()

        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Could not open file" }
            XSSFWorkbook(input).use { wb ->
                val sheet = wb.getSheetAt(0)
                var headerRow: Row? = null
                for (r in 0..sheet.lastRowNum) {
                    val row = sheet.getRow(r) ?: continue
                    val first = row.getCell(0)?.toStringSafe()?.trim()?.uppercase()
                    if (first == "DATE") {
                        headerRow = row
                        continue
                    }
                    if (headerRow == null) continue

                    val date = row.getCell(0)
                    val time = row.getCell(1)
                    val sessionStart = combineDateTime(date, time) ?: run {
                        skipped++; continue
                    }

                    val mileage = row.getCell(2)?.numericOrNull()
                    val energy = row.getCell(3)?.numericOrNull()
                    val cost = row.getCell(4)?.numericOrNull()
                    val durationCell = row.getCell(5)
                    val durationSeconds = durationToSeconds(durationCell)
                    val postedKwh = row.getCell(7)?.numericOrNull()
                    val postedTimeRate = row.getCell(9)?.numericOrNull()
                    val postedMaxKw = row.getCell(12)?.numericOrNull()
                    val battStart = row.getCell(13)?.numericOrNull()?.let { (it * 100).toInt() }
                    val battEnd = row.getCell(14)?.numericOrNull()?.let { (it * 100).toInt() }
                    val brand = row.getCell(15)?.toStringSafe()?.trim()?.takeIf { it.isNotEmpty() }
                    val cityProv = row.getCell(16)?.toStringSafe()?.trim()
                    val (city, prov) = splitCityProv(cityProv)
                    val address = row.getCell(17)?.toStringSafe()?.trim()?.takeIf { it.isNotEmpty() }
                    val station = row.getCell(18)?.toStringSafe()?.trim()?.takeIf { it.isNotEmpty() }
                    val notes = row.getCell(19)?.toStringSafe()?.trim()?.takeIf { it.isNotEmpty() }

                    val pricing = inferPricing(cost, energy, postedKwh, postedTimeRate)
                    val type = inferType(postedMaxKw, energy, durationSeconds)

                    sessions += ChargingSession(
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
                    imported++
                }
            }
        }
        if (sessions.isNotEmpty()) sessionRepository.insertAll(sessions)
        XlsxImportResult(imported, skipped, errors)
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
