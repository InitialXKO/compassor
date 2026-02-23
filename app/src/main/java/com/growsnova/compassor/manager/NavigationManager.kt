package com.growsnova.compassor.manager

import android.location.Location
import com.amap.api.maps.model.LatLng
import com.growsnova.compassor.Route
import com.growsnova.compassor.Waypoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NavigationManager @Inject constructor() {

    private val _currentRoute = MutableStateFlow<Route?>(null)
    val currentRoute: StateFlow<Route?> = _currentRoute

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex

    private val _targetWaypoint = MutableStateFlow<Waypoint?>(null)
    val targetWaypoint: StateFlow<Waypoint?> = _targetWaypoint

    fun startRoute(route: Route, startIndex: Int = 0) {
        _currentRoute.value = route
        _currentIndex.value = startIndex
        if (route.waypoints.isNotEmpty() && startIndex in route.waypoints.indices) {
            _targetWaypoint.value = route.waypoints[startIndex]
        }
    }

    fun stopNavigation() {
        _currentRoute.value = null
        _currentIndex.value = -1
        _targetWaypoint.value = null
    }

    fun nextWaypoint(): Boolean {
        val route = _currentRoute.value ?: return false
        val nextIndex = _currentIndex.value + 1
        return if (nextIndex < route.waypoints.size) {
            _currentIndex.value = nextIndex
            _targetWaypoint.value = route.waypoints[nextIndex]
            true
        } else if (route.isLooping && route.waypoints.isNotEmpty()) {
            _currentIndex.value = 0
            _targetWaypoint.value = route.waypoints[0]
            true
        } else {
            false
        }
    }

    fun previousWaypoint(): Boolean {
        val route = _currentRoute.value ?: return false
        val prevIndex = _currentIndex.value - 1
        return if (prevIndex >= 0) {
            _currentIndex.value = prevIndex
            _targetWaypoint.value = route.waypoints[prevIndex]
            true
        } else {
            false
        }
    }

    fun setTarget(waypoint: Waypoint) {
        _currentRoute.value = null
        _currentIndex.value = -1
        _targetWaypoint.value = waypoint
    }

    fun calculateDistance(currentLocation: LatLng, target: LatLng): Float {
        val results = FloatArray(1)
        Location.distanceBetween(
            currentLocation.latitude, currentLocation.longitude,
            target.latitude, target.longitude,
            results
        )
        return results[0]
    }
}
