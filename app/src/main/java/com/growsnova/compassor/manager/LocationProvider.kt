package com.growsnova.compassor.manager

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager as SystemLocationManager
import android.os.Bundle
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as SystemLocationManager

    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            _currentLocation.value = location
        }
        @Suppress("DEPRECATION")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        try {
            if (locationManager.isProviderEnabled(SystemLocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    SystemLocationManager.GPS_PROVIDER,
                    1000L,
                    1f,
                    locationListener
                )
            }
            if (locationManager.isProviderEnabled(SystemLocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    SystemLocationManager.NETWORK_PROVIDER,
                    1000L,
                    1f,
                    locationListener
                )
            }
        } catch (e: Exception) {
            // Handle exception
        }
    }

    fun stopLocationUpdates() {
        locationManager.removeUpdates(locationListener)
    }
}
