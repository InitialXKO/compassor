package com.growsnova.compassor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.growsnova.compassor.RouteWithWaypoints
import com.growsnova.compassor.Waypoint
import com.growsnova.compassor.data.repository.RouteRepository
import com.growsnova.compassor.data.repository.WaypointRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    private val waypointRepository: WaypointRepository,
    private val routeRepository: RouteRepository,
    exceptionHandler: CoroutineExceptionHandler
) : ViewModel() {

    private val scope = viewModelScope + exceptionHandler

    val allWaypoints: StateFlow<List<Waypoint>> = waypointRepository.getAllWaypointsFlow()
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allRoutes: StateFlow<List<RouteWithWaypoints>> = routeRepository.getAllRoutesWithWaypointsFlow()
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
}
