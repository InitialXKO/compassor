package com.growsnova.compassor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.growsnova.compassor.Route
import com.growsnova.compassor.Waypoint
import com.growsnova.compassor.data.repository.RouteRepository
import com.growsnova.compassor.manager.NavigationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import javax.inject.Inject

@HiltViewModel
class NavigationViewModel @Inject constructor(
    private val navigationManager: NavigationManager,
    private val routeRepository: RouteRepository,
    exceptionHandler: CoroutineExceptionHandler
) : ViewModel() {

    private val scope = viewModelScope + exceptionHandler

    val currentRoute: StateFlow<Route?> = navigationManager.currentRoute
    val currentIndex: StateFlow<Int> = navigationManager.currentIndex
    val targetWaypoint: StateFlow<Waypoint?> = navigationManager.targetWaypoint

    init {
        // Load saved navigation state
        val savedRoute = routeRepository.getSavedNavigationRoute()
        val savedIndex = routeRepository.getSavedNavigationIndex()
        if (savedRoute != null) {
            navigationManager.startRoute(savedRoute, savedIndex)
        }
    }

    fun startNavigation(route: Route, index: Int = 0) {
        navigationManager.startRoute(route, index)
        routeRepository.saveNavigationState(route, index)
    }

    fun stopNavigation() {
        navigationManager.stopNavigation()
        routeRepository.clearNavigationState()
    }

    fun nextWaypoint() {
        if (navigationManager.nextWaypoint()) {
            routeRepository.saveNavigationState(navigationManager.currentRoute.value, navigationManager.currentIndex.value)
        }
    }

    fun previousWaypoint() {
        if (navigationManager.previousWaypoint()) {
            routeRepository.saveNavigationState(navigationManager.currentRoute.value, navigationManager.currentIndex.value)
        }
    }

    fun setTarget(waypoint: Waypoint) {
        navigationManager.setTarget(waypoint)
        routeRepository.saveTargetLocation(waypoint.latitude, waypoint.longitude, waypoint.name)
    }
}
