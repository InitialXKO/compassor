package com.growsnova.compassor.ui.viewmodel

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amap.api.maps.model.LatLng
import com.growsnova.compassor.CoordTransform
import com.growsnova.compassor.manager.DeviceLocationManager
import com.growsnova.compassor.manager.SensorOrientationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LocationViewModel @Inject constructor(
    private val deviceLocationManager: DeviceLocationManager,
    private val sensorOrientationManager: SensorOrientationManager,
    private val exceptionHandler: CoroutineExceptionHandler
) : ViewModel() {

    private val _currentLocation = MutableStateFlow<LatLng?>(null)
    val currentLocation: StateFlow<LatLng?> = _currentLocation.asStateFlow()

    private val _azimuth = MutableStateFlow(0f)
    val azimuth: StateFlow<Float> = _azimuth.asStateFlow()

    private val _errorFlow = MutableSharedFlow<String>(replay = 0)
    val errorFlow: SharedFlow<String> = _errorFlow.asSharedFlow()

    init {
        startLocationUpdates()
        startOrientationUpdates()
    }

    private fun startLocationUpdates() {
        viewModelScope.launch(exceptionHandler) {
            deviceLocationManager.getLocationFlow().collect { location ->
                val (gcjLat, gcjLng) = CoordTransform.wgs84ToGcj02(location.latitude, location.longitude)
                _currentLocation.value = LatLng(gcjLat, gcjLng)
            }
        }
    }

    private fun startOrientationUpdates() {
        viewModelScope.launch(exceptionHandler) {
            sensorOrientationManager.getOrientationFlow().collect { azimuth ->
                _azimuth.value = azimuth
            }
        }
    }
}
