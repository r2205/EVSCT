package com.evsct.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class GeocodedLocation(
    val city: String?,
    val provinceState: String?,
    val address: String?,
    val countryCode: String?,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

sealed interface AutofillResult {
    data object MissingPermission : AutofillResult
    data object NoProvider : AutofillResult
    data object NoLocation : AutofillResult
    data object GeocoderUnavailable : AutofillResult
    data class Failure(val reason: String) : AutofillResult
    data class Success(val data: GeocodedLocation) : AutofillResult
}

@Singleton
class LocationAutofill @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun hasPermission(): Boolean = listOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ).any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun fetch(): AutofillResult = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext AutofillResult.MissingPermission

        // Resolve the provider here (rather than inside getCurrentLocation)
        // so "location services are off" surfaces as NoProvider — with its
        // "turn on location" message — instead of being collapsed into the
        // same null as a fix timeout.
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext AutofillResult.NoProvider
        val provider = pickProvider(lm)
            ?: return@withContext AutofillResult.NoProvider

        val location = getCurrentLocation(lm, provider)
            ?: return@withContext AutofillResult.NoLocation

        if (!Geocoder.isPresent()) return@withContext AutofillResult.GeocoderUnavailable

        val addresses = try {
            reverseGeocode(location.latitude, location.longitude)
        } catch (e: IOException) {
            return@withContext AutofillResult.Failure(e.message ?: "Reverse geocoding failed")
        }
        val address = addresses.firstOrNull() ?: return@withContext AutofillResult.NoLocation
        AutofillResult.Success(
            address.toGeocoded().copy(
                latitude = location.latitude,
                longitude = location.longitude,
            )
        )
    }

    /** Just the device's current coordinates — no reverse geocoding. Null
     *  when permission is missing, no usable provider is enabled, or the
     *  fix times out. Backs the map screens' my-location button and the
     *  location picker's initial camera seed. */
    suspend fun currentLatLng(): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext null
        val provider = pickProvider(lm) ?: return@withContext null
        getCurrentLocation(lm, provider)?.let { it.latitude to it.longitude }
    }

    /**
     * Reverse-geocode an address string (no GPS fix involved). Returns null
     * when geocoding isn't available or no result is found. Used by the map
     * screen to backfill historical sessions that have only a textual address.
     */
    /**
     * Forward-geocode an address to lat/lng with disambiguation. Geocoder
     * sometimes ranks a famous matching street in a big city above an
     * obscure one in a small town when both share a province — we mitigate
     * by:
     *   1. Asking for several candidates instead of just the first.
     *   2. Filtering to candidates whose [Address.locality] matches the
     *      caller's typed [city] (case-insensitive).
     *   3. Retrying with a country qualifier when the first attempt yielded
     *      no city-matching candidate (often resolves the ambiguity).
     *   4. Falling back to a city-only query as a last resort, so the pin
     *      lands somewhere in the right town instead of the wrong one.
     */
    suspend fun geocode(
        address: String?,
        city: String?,
        province: String?,
    ): GeocodedLocation? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        val geocoder = Geocoder(context, Locale.getDefault())
        val expectedCity = city?.trim()?.takeIf { it.isNotBlank() }
        val countryHint = countryFor(province)

        suspend fun lookup(query: String, max: Int): List<Address> = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    // A lambda here would SAM-implement only onGeocode; the
                    // default no-op onError would leave the coroutine suspended
                    // forever on any geocoder failure. Resume with IOException
                    // so this branch reports errors the same way the pre-33
                    // synchronous call does.
                    geocoder.getFromLocationName(
                        query,
                        max,
                        object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                if (cont.isActive) cont.resume(addresses)
                            }

                            override fun onError(errorMessage: String?) {
                                if (cont.isActive) {
                                    cont.resumeWithException(
                                        IOException(errorMessage ?: "Geocoding failed")
                                    )
                                }
                            }
                        },
                    )
                }
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocationName(query, max) ?: emptyList()
            }
        } catch (_: IOException) {
            emptyList()
        }

        fun matchesExpectedCity(addr: Address): Boolean =
            expectedCity == null || addr.locality?.equals(expectedCity, ignoreCase = true) == true

        val baseQuery = listOfNotNull(
            address?.takeIf { it.isNotBlank() },
            city?.takeIf { it.isNotBlank() },
            province?.takeIf { it.isNotBlank() },
        ).joinToString(", ").takeIf { it.isNotBlank() }
            ?: return@withContext null

        // 1. Plain query, take the first locality-matching hit.
        var candidates = lookup(baseQuery, max = 5)
        candidates.firstOrNull(::matchesExpectedCity)?.let { return@withContext it.toGeocoded() }

        // 2. Same query plus a country qualifier — usually nudges Geocoder
        //    away from a famous-but-wrong match in another town. Keep step
        //    1's candidates for the step-4 fallback when this retry comes
        //    back empty (routine Geocoder flakiness) — overwriting with an
        //    empty list used to return no pin at all where the documented
        //    intent is to surrender to an imperfect match.
        if (countryHint != null) {
            val withCountry = lookup("$baseQuery, $countryHint", max = 5)
            withCountry.firstOrNull(::matchesExpectedCity)?.let { return@withContext it.toGeocoded() }
            if (withCountry.isNotEmpty()) candidates = withCountry
        }

        // 3. Address-level lookup keeps failing; ask for the city alone so
        //    the pin at least lands in the right town. Acceptable downgrade
        //    versus rendering at completely the wrong location.
        val cityOnly = listOfNotNull(
            city?.takeIf { it.isNotBlank() },
            province?.takeIf { it.isNotBlank() },
            countryHint,
        ).joinToString(", ").takeIf { it.isNotBlank() }
        if (cityOnly != null) {
            lookup(cityOnly, max = 1).firstOrNull()?.let { return@withContext it.toGeocoded() }
        }

        // 4. As a last resort, surrender to whatever Geocoder gave us.
        candidates.firstOrNull()?.toGeocoded()
    }

    /** Best-effort country name from the typed province / state code, used
     *  as a Geocoder qualifier. Returns null when we can't tell. */
    private fun countryFor(province: String?): String? {
        val code = province?.trim()?.uppercase() ?: return null
        return when (code) {
            in CANADIAN_PROVINCE_CODES -> "Canada"
            in US_STATE_CODES -> "USA"
            else -> null
        }
    }

    private companion object {
        private val CANADIAN_PROVINCE_CODES = setOf(
            "AB", "BC", "MB", "NB", "NL", "NS", "NT", "NU",
            "ON", "PE", "QC", "SK", "YT",
        )
        private val US_STATE_CODES = setOf(
            "AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "FL", "GA",
            "HI", "ID", "IL", "IN", "IA", "KS", "KY", "LA", "ME", "MD",
            "MA", "MI", "MN", "MS", "MO", "MT", "NE", "NV", "NH", "NJ",
            "NM", "NY", "NC", "ND", "OH", "OK", "OR", "PA", "RI", "SC",
            "SD", "TN", "TX", "UT", "VT", "VA", "WA", "WV", "WI", "WY",
            "DC",
        )
    }

    private suspend fun getCurrentLocation(lm: LocationManager, provider: String): Location? {
        // Try last known first — instant if recent.
        try {
            @Suppress("MissingPermission")
            lm.getLastKnownLocation(provider)?.let { last ->
                if (System.currentTimeMillis() - last.time < 60_000) return last
            }
        } catch (_: SecurityException) {
            return null
        }

        return withTimeoutOrNull(15_000) { awaitFreshLocation(lm, provider) }
    }

    private fun pickProvider(lm: LocationManager): String? {
        val providers = lm.getProviders(true)
        // Don't fall through to providers.firstOrNull() — on some devices
        // that's PASSIVE_PROVIDER, which only delivers updates when another
        // app requests one. getCurrentLocation against PASSIVE just times
        // out, so the user sees "Could not get a location fix" even though
        // location is on. Better to return null and surface NoProvider.
        // FUSED_PROVIDER is only documented (and guaranteed functional)
        // from API 31 — on Android 11 it can appear in getProviders(true)
        // yet never compute a fix through getCurrentLocation, riding out
        // the 15 s timeout while working GPS/NETWORK providers sit unused.
        val fusedUsable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            LocationManager.FUSED_PROVIDER in providers
        return when {
            fusedUsable -> LocationManager.FUSED_PROVIDER
            LocationManager.GPS_PROVIDER in providers -> LocationManager.GPS_PROVIDER
            LocationManager.NETWORK_PROVIDER in providers -> LocationManager.NETWORK_PROVIDER
            else -> null
        }
    }

    private suspend fun awaitFreshLocation(
        lm: LocationManager,
        provider: String,
    ): Location? = suspendCancellableCoroutine { cont ->
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // CancellationSignal lets us tell the system to stop the fix
                // when the coroutine is cancelled (e.g., screen rotated while
                // a GPS fix was pending). Otherwise the GPS chip stays warm
                // until the OS times out on its own.
                val cancellationSignal = android.os.CancellationSignal()
                cont.invokeOnCancellation { cancellationSignal.cancel() }
                @Suppress("MissingPermission")
                lm.getCurrentLocation(provider, cancellationSignal, context.mainExecutor) { loc ->
                    if (cont.isActive) cont.resume(loc)
                }
            } else {
                // Hold a reference to the listener so we can detach it on
                // cancellation — otherwise it stays subscribed and resumes a
                // dead coroutine on the next location update, leaking the
                // listener (and the captured continuation) until the process
                // dies. minSdk 30 means this branch is currently unreachable
                // but the defensive cleanup is cheap.
                @Suppress("DEPRECATION", "MissingPermission")
                val listener = object : android.location.LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (cont.isActive) cont.resume(location)
                    }
                    override fun onProviderEnabled(p0: String) {}
                    override fun onProviderDisabled(p0: String) {
                        if (cont.isActive) cont.resume(null)
                    }
                    @Deprecated("Required override")
                    override fun onStatusChanged(p0: String?, p1: Int, p2: android.os.Bundle?) {}
                }
                cont.invokeOnCancellation { lm.removeUpdates(listener) }
                @Suppress("DEPRECATION", "MissingPermission")
                lm.requestSingleUpdate(provider, listener, context.mainLooper)
            }
        } catch (e: SecurityException) {
            if (cont.isActive) cont.resume(null)
        }
    }

    /**
     * Reverse-geocode a known lat/lng to an address. Used by the manual map
     * picker so a confirmed point fills in the city/prov/address fields.
     * The returned [GeocodedLocation] always carries the input lat/lng, so
     * even if Geocoder returns nothing the caller still has the coordinates.
     */
    suspend fun reverseGeocodeAt(lat: Double, lng: Double): GeocodedLocation? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) {
            return@withContext GeocodedLocation(
                city = null, provinceState = null, address = null, countryCode = null,
                latitude = lat, longitude = lng,
            )
        }
        val addresses = try { reverseGeocode(lat, lng) } catch (_: IOException) { emptyList() }
        val first = addresses.firstOrNull()
        first?.toGeocoded()?.copy(latitude = lat, longitude = lng)
            ?: GeocodedLocation(
                city = null, provinceState = null, address = null, countryCode = null,
                latitude = lat, longitude = lng,
            )
    }

    private suspend fun reverseGeocode(lat: Double, lng: Double): List<Address> {
        val geocoder = Geocoder(context, Locale.getDefault())
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                // Full listener for the same reason as in geocode(): a lambda
                // leaves onError as a no-op and the coroutine hangs forever on
                // geocoder failure. IOException matches the pre-33 contract,
                // which every caller already catches.
                geocoder.getFromLocation(
                    lat,
                    lng,
                    1,
                    object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            if (cont.isActive) cont.resume(addresses)
                        }

                        override fun onError(errorMessage: String?) {
                            if (cont.isActive) {
                                cont.resumeWithException(
                                    IOException(errorMessage ?: "Reverse geocoding failed")
                                )
                            }
                        }
                    },
                )
            }
        } else {
            @Suppress("DEPRECATION")
            geocoder.getFromLocation(lat, lng, 1) ?: emptyList()
        }
    }

    private fun Address.toGeocoded(): GeocodedLocation {
        val streetParts = listOfNotNull(subThoroughfare, thoroughfare)
            .filter { it.isNotBlank() }
        val street = if (streetParts.isNotEmpty()) streetParts.joinToString(" ") else null
        val cityCandidate = locality
            ?: subAdminArea
            ?: subLocality
        return GeocodedLocation(
            city = cityCandidate?.takeIf { it.isNotBlank() },
            provinceState = adminArea?.takeIf { it.isNotBlank() }?.let { RegionCodes.normalize(it) },
            address = street ?: getAddressLine(0)?.takeIf { it.isNotBlank() },
            countryCode = countryCode,
            latitude = if (hasLatitude()) latitude else null,
            longitude = if (hasLongitude()) longitude else null,
        )
    }
}
