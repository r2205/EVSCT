package com.evsct.app.util

import com.evsct.app.data.entity.ChargingSession
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class StopKeyTest {

    @Test
    fun `text key joins brand address city lowercased`() {
        val key = StopKey.of(
            session(brand = "Tesla Supercharger", address = "945 Gardiners Rd", city = "Kingston"),
        )
        assertEquals("tesla supercharger|945 gardiners rd|kingston", key)
    }

    @Test
    fun `partial text fields still form a key`() {
        assertEquals("kingston", StopKey.of(session(city = "Kingston")))
        assertEquals("flo", StopKey.of(session(brand = "FLO")))
    }

    @Test
    fun `trim and case differences group together`() {
        assertEquals(
            StopKey.of(session(brand = " Tesla ", city = "kingston ")),
            StopKey.of(session(brand = "tesla", city = "Kingston")),
        )
    }

    @Test
    fun `station name is not part of the key`() {
        assertEquals(
            StopKey.of(session(brand = "FLO", stationName = "Stall 4")),
            StopKey.of(session(brand = "FLO", stationName = "Stall 9")),
        )
    }

    @Test
    fun `coordinates-only session gets a geo bucket key`() {
        val key = StopKey.of(session(lat = 45.2718, lng = -75.7580))
        assertTrue(key.startsWith("geo:"), "expected geo key, got $key")
    }

    @Test
    fun `repeat visits to the same picked spot share a bucket`() {
        // ~5 m apart — same 4-decimal bucket.
        assertEquals(
            StopKey.of(session(lat = 45.27181, lng = -75.75801)),
            StopKey.of(session(lat = 45.27183, lng = -75.75802)),
        )
    }

    @Test
    fun `distinct locations get distinct buckets`() {
        assertNotEquals(
            StopKey.of(session(lat = 45.2718, lng = -75.7580)),
            StopKey.of(session(lat = 45.4215, lng = -75.6972)),
        )
    }

    @Test
    fun `text key wins over coordinates`() {
        // Same charger logged with and without a GPS fix must share a pin.
        assertEquals(
            StopKey.of(session(brand = "FLO", city = "Ottawa", lat = 45.2718, lng = -75.7580)),
            StopKey.of(session(brand = "FLO", city = "Ottawa")),
        )
    }

    @Test
    fun `no text and no coordinates yields a blank key`() {
        assertEquals("", StopKey.of(session()))
    }

    @Test
    fun `a lone coordinate is not enough`() {
        assertEquals("", StopKey.of(session(lat = 45.2718)))
        assertEquals("", StopKey.of(session(lng = -75.7580)))
    }

    private fun session(
        brand: String? = null,
        address: String? = null,
        city: String? = null,
        stationName: String? = null,
        lat: Double? = null,
        lng: Double? = null,
    ): ChargingSession = ChargingSession(
        sessionStart = 0,
        brand = brand,
        locationAddress = address,
        locationCity = city,
        stationName = stationName,
        latitude = lat,
        longitude = lng,
    )
}
