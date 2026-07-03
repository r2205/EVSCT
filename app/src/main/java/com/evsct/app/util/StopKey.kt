package com.evsct.app.util

import com.evsct.app.data.entity.ChargingSession
import kotlin.math.roundToInt

/**
 * Grouping key for map stops, shared by the live map and the year-recap
 * map: brand + address + city, lowercased. Station/stall name is
 * intentionally NOT part of the key — visits to the same physical charger
 * should share a pin even when each visit logs a different stall number.
 * (The session editor's recent-stop suggestions keep their own text-only
 * variant: a coordinate bucket has no text to autofill.)
 *
 * Sessions with none of those fields but with coordinates — a map-pick
 * with no typed address, or a GPS fix whose reverse-geocode returned
 * nothing — get a coordinate-bucket key instead. Without the fallback they
 * blank-key out of every map despite being perfectly plottable, and the
 * map claims "No locations to map yet". Buckets are 4 decimal places
 * (~11 m): tight enough to separate neighbouring stations, loose enough to
 * merge repeat visits to the same spot.
 *
 * Returns "" when there is nothing to group by (no text, no coordinates);
 * callers drop those sessions.
 */
object StopKey {
    fun of(s: ChargingSession): String {
        val text = listOfNotNull(
            s.brand?.trim()?.lowercase()?.takeIf { it.isNotEmpty() },
            s.locationAddress?.trim()?.lowercase()?.takeIf { it.isNotEmpty() },
            s.locationCity?.trim()?.lowercase()?.takeIf { it.isNotEmpty() },
        ).joinToString("|")
        if (text.isNotEmpty()) return text
        val lat = s.latitude ?: return ""
        val lng = s.longitude ?: return ""
        // Integer buckets, not String.format: %.4f is locale-dependent and
        // a comma decimal separator would collide with the pair separator.
        return "geo:${(lat * 10_000).roundToInt()},${(lng * 10_000).roundToInt()}"
    }
}
