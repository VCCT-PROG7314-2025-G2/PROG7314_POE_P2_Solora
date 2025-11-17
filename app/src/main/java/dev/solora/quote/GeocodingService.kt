package dev.solora.quote

import android.content.Context
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

/**
 * Converts between addresses and GPS coordinates
 * Used to get location data for NASA solar API calls
 */
class GeocodingService(private val context: Context) {
    
    data class LocationResult(
        val latitude: Double,
        val longitude: Double,
        val address: String,
        val success: Boolean,
        val error: String? = null
    )
    
    // Convert street address to latitude/longitude
    suspend fun getCoordinatesFromAddress(address: String): LocationResult = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            
            if (!Geocoder.isPresent()) {
                return@withContext LocationResult(
                    latitude = 0.0,
                    longitude = 0.0,
                    address = address,
                    success = false,
                    error = "Geocoder not available on this device"
                )
            }
            
            val addresses = geocoder.getFromLocationName(address, 1)
            
            if (addresses.isNullOrEmpty()) {
                return@withContext LocationResult(
                    latitude = 0.0,
                    longitude = 0.0,
                    address = address,
                    success = false,
                    error = "Address not found: $address"
                )
            }
            
            val location = addresses[0]
            LocationResult(
                latitude = location.latitude,
                longitude = location.longitude,
                address = location.getAddressLine(0) ?: address,
                success = true
            )
            
        } catch (e: Exception) {
            LocationResult(
                latitude = 0.0,
                longitude = 0.0,
                address = address,
                success = false,
                error = "Geocoding failed: ${e.message}"
            )
        }
    }
    
    // Convert latitude/longitude to street address
    suspend fun getAddressFromCoordinates(latitude: Double, longitude: Double): LocationResult = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            
            if (!Geocoder.isPresent()) {
                return@withContext LocationResult(
                    latitude = latitude,
                    longitude = longitude,
                    address = "Unknown",
                    success = false,
                    error = "Geocoder not available on this device"
                )
            }
            
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            
            if (addresses.isNullOrEmpty()) {
                return@withContext LocationResult(
                    latitude = latitude,
                    longitude = longitude,
                    address = "Unknown",
                    success = false,
                    error = "No address found for coordinates: $latitude, $longitude"
                )
            }
            
            val location = addresses[0]
            LocationResult(
                latitude = latitude,
                longitude = longitude,
                address = location.getAddressLine(0) ?: "Unknown",
                success = true
            )
            
        } catch (e: Exception) {
            LocationResult(
                latitude = latitude,
                longitude = longitude,
                address = "Unknown",
                success = false,
                error = "Reverse geocoding failed: ${e.message}"
            )
        }
    }
}
