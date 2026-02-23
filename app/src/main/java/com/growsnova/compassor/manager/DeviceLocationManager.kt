package com.growsnova.compassor.manager

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import com.growsnova.compassor.base.AppConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceLocationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    @SuppressLint("MissingPermission")
    fun getLocationFlow(): Flow<Location> = callbackFlow {
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(location)
            }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        providers.forEach { provider ->
            if (locationManager.isProviderEnabled(provider)) {
                locationManager.requestLocationUpdates(
                    provider,
                    AppConstants.LOCATION_UPDATE_INTERVAL,
                    AppConstants.LOCATION_UPDATE_MIN_DISTANCE,
                    listener,
                    Looper.getMainLooper()
                )
                locationManager.getLastKnownLocation(provider)?.let { trySend(it) }
            }
        }

        awaitClose {
            locationManager.removeUpdates(listener)
        }
    }
}
