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

        val location = getCurrentLocation() ?: return@withContext AutofillResult.NoLocation

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

    /**
     * Reverse-geocode an address string (no GPS fix involved). Returns null
     * when geocoding isn't available or no result is found. Used by the map
     * screen to backfill historical sessions that have only a textual address.
     */
    suspend fun geocodeAddress(query: String): GeocodedLocation? = withContext(Dispatchers.IO) {
        if (query.isBlank() || !Geocoder.isPresent()) return@withContext null
        val geocoder = Geocoder(context, Locale.getDefault())
        val result = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocationName(query, 1) { addresses ->
                        if (cont.isActive) cont.resume(addresses)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocationName(query, 1) ?: emptyList()
            }
        } catch (_: IOException) {
            null
        } ?: return@withContext null

        val match = result.firstOrNull() ?: return@withContext null
        match.toGeocoded().copy(
            latitude = match.latitude.takeIf { match.hasLatitude() },
            longitude = match.longitude.takeIf { match.hasLongitude() },
        )
    }

    private suspend fun getCurrentLocation(): Location? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        val provider = pickProvider(lm) ?: return null

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
        return when {
            LocationManager.FUSED_PROVIDER in providers -> LocationManager.FUSED_PROVIDER
            LocationManager.GPS_PROVIDER in providers -> LocationManager.GPS_PROVIDER
            LocationManager.NETWORK_PROVIDER in providers -> LocationManager.NETWORK_PROVIDER
            else -> providers.firstOrNull()
        }
    }

    private suspend fun awaitFreshLocation(
        lm: LocationManager,
        provider: String,
    ): Location? = suspendCancellableCoroutine { cont ->
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                @Suppress("MissingPermission")
                lm.getCurrentLocation(provider, null, context.mainExecutor) { loc ->
                    if (cont.isActive) cont.resume(loc)
                }
            } else {
                @Suppress("DEPRECATION", "MissingPermission")
                lm.requestSingleUpdate(
                    provider,
                    object : android.location.LocationListener {
                        override fun onLocationChanged(location: Location) {
                            if (cont.isActive) cont.resume(location)
                        }
                        override fun onProviderEnabled(p0: String) {}
                        override fun onProviderDisabled(p0: String) {
                            if (cont.isActive) cont.resume(null)
                        }
                        @Deprecated("Required override")
                        override fun onStatusChanged(p0: String?, p1: Int, p2: android.os.Bundle?) {}
                    },
                    context.mainLooper,
                )
            }
        } catch (e: SecurityException) {
            if (cont.isActive) cont.resume(null)
        }
    }

    private suspend fun reverseGeocode(lat: Double, lng: Double): List<Address> {
        val geocoder = Geocoder(context, Locale.getDefault())
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocation(lat, lng, 1) { addresses ->
                    if (cont.isActive) cont.resume(addresses)
                }
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
        )
    }
}
