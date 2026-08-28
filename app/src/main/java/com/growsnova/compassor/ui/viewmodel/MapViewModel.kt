package com.growsnova.compassor.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.growsnova.compassor.Waypoint
import com.growsnova.compassor.data.repository.WaypointRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    private val waypointRepository: WaypointRepository,
    private val exceptionHandler: CoroutineExceptionHandler
) : ViewModel() {

    private val _waypoints = MutableStateFlow<List<Waypoint>>(emptyList())
    val waypoints: StateFlow<List<Waypoint>> = _waypoints.asStateFlow()

    private val _isFollowMode = MutableStateFlow(true)
    val isFollowMode: StateFlow<Boolean> = _isFollowMode.asStateFlow()

    private val _isTrackingMode = MutableStateFlow(false)
    val isTrackingMode: StateFlow<Boolean> = _isTrackingMode.asStateFlow()

    private val _errorFlow = MutableSharedFlow<String>(replay = 0)
    val errorFlow: SharedFlow<String> = _errorFlow.asSharedFlow()

    init {
        loadWaypoints()
    }

    private fun loadWaypoints() {
        viewModelScope.launch(exceptionHandler) {
            waypointRepository.getAllWaypointsFlow().collect {
                _waypoints.value = it
            }
        }
    }

    fun setFollowMode(enabled: Boolean) {
        _isFollowMode.value = enabled
        if (!enabled) {
            _isTrackingMode.value = false
        }
    }

    fun setTrackingMode(enabled: Boolean) {
        _isTrackingMode.value = enabled
        if (enabled) {
            _isFollowMode.value = true
        }
    }

    fun toggleTrackingMode() {
        setTrackingMode(!_isTrackingMode.value)
    }
}
