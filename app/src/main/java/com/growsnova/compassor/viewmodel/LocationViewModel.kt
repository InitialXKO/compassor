package com.growsnova.compassor.viewmodel

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.growsnova.compassor.manager.CompassSensorManager
import com.growsnova.compassor.manager.LocationProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.plus
import javax.inject.Inject

@HiltViewModel
class LocationViewModel @Inject constructor(
    private val locationProvider: LocationProvider,
    private val sensorManager: CompassSensorManager,
    exceptionHandler: CoroutineExceptionHandler
) : ViewModel() {

    private val scope = viewModelScope + exceptionHandler

    val currentLocation: StateFlow<Location?> = locationProvider.currentLocation
    val azimuth: StateFlow<Float> = sensorManager.azimuth

    fun startUpdates() {
        locationProvider.startLocationUpdates()
        sensorManager.start()
    }

    fun stopUpdates() {
        locationProvider.stopLocationUpdates()
        sensorManager.stop()
    }

    override fun onCleared() {
        super.onCleared()
        stopUpdates()
    }
}
