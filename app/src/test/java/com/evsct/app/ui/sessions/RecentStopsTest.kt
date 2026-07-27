package com.evsct.app.ui.sessions

import com.evsct.app.data.entity.ChargingSession
import com.evsct.app.data.entity.ChargingType
import com.evsct.app.data.entity.PricingModel
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The station-grouping behind "Use a recent stop…", which since #6 carries the
 * pricing context — the fields worth repeating are precisely the ones a user
 * would otherwise re-type from the same sign every visit.
 *
 * The sourcing rules are the substance here: identity text from the most
 * recent visit, the pricing set whole from the newest visit that recorded any
 * of it, coordinates from the newest visit that has them. Each rule exists to
 * stop a quick log — no rates typed, no map pick — from degrading the stop.
 */
class RecentStopsTest {

    private var nextStart = 1_752_000_000_000L

    /** Each call is a later visit unless [start] pins one explicitly. */
    private fun visit(
        brand: String? = "Petro-Canada",
        city: String? = "Kingston",
        address: String? = "225 Division St",
        start: Long? = null,
        postedEnergyPricePerKwh: Double? = null,
        postedTimeRatePerMin: Double? = null,
        postedMaxPowerKw: Double? = null,
        pricingModel: PricingModel = PricingModel.PER_KWH,
        chargingType: ChargingType = ChargingType.DC_FAST,
        latitude: Double? = null,
        longitude: Double? = null,
        stationName: String? = null,
        stallName: String? = null,
    ): ChargingSession {
        val at = start ?: run { nextStart += 3_600_000L; nextStart }
        return ChargingSession(
            sessionStart = at,
            brand = brand,
            locationCity = city,
            locationAddress = address,
            postedEnergyPricePerKwh = postedEnergyPricePerKwh,
            postedTimeRatePerMin = postedTimeRatePerMin,
            postedMaxPowerKw = postedMaxPowerKw,
            pricingModel = pricingModel,
            chargingType = chargingType,
            latitude = latitude,
            longitude = longitude,
            stationName = stationName,
            stallName = stallName,
        )
    }

    /* ------------------------------- Grouping ------------------------------- */

    @Test
    fun `visits to the same place make one stop, counted`() {
        val stops = computeRecentStops(listOf(visit(), visit(), visit()))
        assertEquals(1, stops.size)
        assertEquals(3, stops.single().visits)
    }

    @Test
    fun `stall and station name do not split a stop`() {
        // Same physical charger, different stall each visit — the same rule
        // the map pins follow.
        val stops = computeRecentStops(
            listOf(
                visit(stationName = "Circle K", stallName = "Stall 2"),
                visit(stationName = "Circle K charger", stallName = "Stall 4"),
            ),
        )
        assertEquals(1, stops.size)
    }

    @Test
    fun `key text is case- and whitespace-insensitive`() {
        assertEquals(
            stopKey(brand = "Petro-Canada", address = "225 Division St", city = "Kingston"),
            stopKey(brand = " petro-canada ", address = "225 DIVISION ST", city = "kingston "),
        )
    }

    @Test
    fun `sessions with no place text are dropped`() {
        val stops = computeRecentStops(
            listOf(visit(brand = null, city = null, address = null)),
        )
        assertTrue(stops.isEmpty())
    }

    /* ---------------------------- Pricing context ---------------------------- */

    @Test
    fun `the pricing set survives a quick log that skipped the rates`() {
        val stops = computeRecentStops(
            listOf(
                visit(postedEnergyPricePerKwh = 0.53, postedMaxPowerKw = 180.0),
                visit(), // newest: rates not typed
            ),
        )
        val stop = stops.single()
        assertEquals(0.53, stop.postedEnergyPricePerKwh)
        assertEquals(180.0, stop.postedMaxPowerKw)
    }

    @Test
    fun `the pricing set is one visit's set, never a cross-visit mix`() {
        // The station switched from per-minute to per-kWh billing between
        // visits. The stop must carry the newer visit's whole set — pairing
        // the new model with the old minute rate would describe a sign that
        // never existed.
        val stops = computeRecentStops(
            listOf(
                visit(pricingModel = PricingModel.PER_MINUTE, postedTimeRatePerMin = 0.45),
                visit(pricingModel = PricingModel.PER_KWH, postedEnergyPricePerKwh = 0.57),
            ),
        )
        val stop = stops.single()
        assertEquals(PricingModel.PER_KWH, stop.pricingModel)
        assertEquals(0.57, stop.postedEnergyPricePerKwh)
        assertNull(stop.postedTimeRatePerMin)
    }

    @Test
    fun `no visit ever recorded pricing leaves the pricing fields empty`() {
        val stop = computeRecentStops(listOf(visit(), visit())).single()
        assertNull(stop.postedEnergyPricePerKwh)
        assertNull(stop.postedTimeRatePerMin)
        assertNull(stop.postedMaxPowerKw)
        // The model still reflects the latest visit rather than inventing one.
        assertEquals(PricingModel.PER_KWH, stop.pricingModel)
    }

    /* ------------------------------ Coordinates ------------------------------ */

    @Test
    fun `coordinates survive a visit without a map pick`() {
        val stops = computeRecentStops(
            listOf(
                visit(latitude = 44.2312, longitude = -76.4860),
                visit(), // newest: no pick
            ),
        )
        val stop = stops.single()
        assertEquals(44.2312, stop.latitude)
        assertEquals(-76.4860, stop.longitude)
    }

    @Test
    fun `a lone latitude is not half-applied`() {
        // Coordinates only count as a pair; a session that somehow carries
        // one half must not become a stop with a latitude and no longitude.
        val stop = computeRecentStops(listOf(visit(latitude = 44.0))).single()
        assertNull(stop.latitude)
        assertNull(stop.longitude)
    }

    /* ------------------------------- Ordering -------------------------------- */

    @Test
    fun `stops list newest place first, and identity text follows the newest visit`() {
        val stops = computeRecentStops(
            listOf(
                visit(brand = "Flo", address = "1 Main St", city = "Belleville"),
                visit(stationName = "Division St fast charger"),
            ),
        )
        assertEquals(2, stops.size)
        assertEquals("Petro-Canada", stops.first().brand)
        assertEquals("Division St fast charger", stops.first().stationName)
        assertEquals("Flo", stops.last().brand)
    }
}
