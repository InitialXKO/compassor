package com.growsnova.compassor.manager

import android.location.Location
import com.amap.api.maps.model.LatLng
import com.growsnova.compassor.Route
import com.growsnova.compassor.Waypoint
import com.growsnova.compassor.base.AppConstants
import com.growsnova.compassor.data.repository.NavigationRepository
import com.growsnova.compassor.data.repository.RouteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NavigationManager @Inject constructor(
    private val navigationRepository: NavigationRepository,
    private val routeRepository: RouteRepository
) {
    private val _currentRoute = MutableStateFlow<Route?>(null)
    val currentRoute: StateFlow<Route?> = _currentRoute.asStateFlow()

    private val _currentWaypointIndex = MutableStateFlow(-1)
    val currentWaypointIndex: StateFlow<Int> = _currentWaypointIndex.asStateFlow()

    private val _targetLocation = MutableStateFlow<Pair<LatLng, String>?>(null)
    val targetLocation: StateFlow<Pair<LatLng, String>?> = _targetLocation.asStateFlow()

    private val _navStartLocation = MutableStateFlow<LatLng?>(null)
    val navStartLocation: StateFlow<LatLng?> = _navStartLocation.asStateFlow()

    fun startRouteNavigation(route: Route, startLocation: LatLng? = null) {
        if (route.waypoints.size < 2) {
            if (route.waypoints.size == 1) {
                val singleWaypoint = route.waypoints[0]
                setTarget(LatLng(singleWaypoint.latitude, singleWaypoint.longitude), singleWaypoint.name)
            } else {
                stopNavigation()
            }
            return
        }
        _currentRoute.value = route
        _currentWaypointIndex.value = 0
        _navStartLocation.value = startLocation
        val firstWaypoint = route.waypoints[0]
        updateTargetInternal(LatLng(firstWaypoint.latitude, firstWaypoint.longitude), firstWaypoint.name)
        saveState()
    }

    fun setTarget(latLng: LatLng, name: String) {
        _currentRoute.value = null
        _currentWaypointIndex.value = -1
        _navStartLocation.value = null
        updateTargetInternal(latLng, name)
        saveState()
    }

    private fun updateTargetInternal(latLng: LatLng, name: String) {
        _targetLocation.value = Pair(latLng, name)
    }

    fun stopNavigation() {
        _currentRoute.value = null
        _currentWaypointIndex.value = -1
        _navStartLocation.value = null
        _targetLocation.value = null
        navigationRepository.clearNavigationState()
    }

    fun onRouteDeleted(routeId: Long) {
        if (_currentRoute.value?.id == routeId) {
            _currentRoute.value = null
            _currentWaypointIndex.value = -1
            _navStartLocation.value = null
            saveState()
        }
    }

    fun onWaypointDeleted(waypointId: Long) {
        val activeRoute = _currentRoute.value ?: run {
            if (_targetLocation.value != null) {
                // If single-target navigation was heading to a waypoint by location, check if target was deleted
                // Note: single target handled by ViewModel or caller if needed
            }
            return
        }

        val deletedIndex = activeRoute.waypoints.indexOfFirst { it.id == waypointId }
        if (deletedIndex == -1) return

        val newWaypoints = activeRoute.waypoints.filter { it.id != waypointId }.toMutableList()
        if (newWaypoints.size < 2) {
            if (newWaypoints.size == 1) {
                val remainingWaypoint = newWaypoints[0]
                setTarget(LatLng(remainingWaypoint.latitude, remainingWaypoint.longitude), remainingWaypoint.name)
            } else {
                stopNavigation()
            }
            return
        }

        val currentIndex = _currentWaypointIndex.value
        val updatedRoute = activeRoute.copy(waypoints = newWaypoints)

        val newIndex = when {
            currentIndex > deletedIndex -> currentIndex - 1
            currentIndex == deletedIndex -> currentIndex.coerceAtMost(newWaypoints.size - 1)
            else -> currentIndex
        }

        _currentRoute.value = updatedRoute
        _currentWaypointIndex.value = newIndex
        val targetWaypoint = newWaypoints[newIndex]
        updateTargetInternal(LatLng(targetWaypoint.latitude, targetWaypoint.longitude), targetWaypoint.name)
        saveState()
    }

    fun skipNextWaypoint(): String? {
        val route = _currentRoute.value ?: return null
        val index = _currentWaypointIndex.value
        if (index < route.waypoints.size - 1) {
            _currentWaypointIndex.value = index + 1
            val nextWaypoint = route.waypoints[index + 1]
            updateTargetInternal(LatLng(nextWaypoint.latitude, nextWaypoint.longitude), nextWaypoint.name)
            saveState()
            return nextWaypoint.name
        } else if (route.isLooping) {
            _currentWaypointIndex.value = 0
            val firstWaypoint = route.waypoints[0]
            updateTargetInternal(LatLng(firstWaypoint.latitude, firstWaypoint.longitude), firstWaypoint.name)
            saveState()
            return firstWaypoint.name
        }
        stopNavigation()
        return null
    }

    fun goToPreviousWaypoint(): String? {
        val route = _currentRoute.value ?: return null
        val index = _currentWaypointIndex.value
        if (index > 0) {
            _currentWaypointIndex.value = index - 1
            val prevWaypoint = route.waypoints[index - 1]
            updateTargetInternal(LatLng(prevWaypoint.latitude, prevWaypoint.longitude), prevWaypoint.name)
            saveState()
            return prevWaypoint.name
        } else if (route.isLooping) {
            val lastIndex = route.waypoints.size - 1
            _currentWaypointIndex.value = lastIndex
            val lastWaypoint = route.waypoints[lastIndex]
            updateTargetInternal(LatLng(lastWaypoint.latitude, lastWaypoint.longitude), lastWaypoint.name)
            saveState()
            return lastWaypoint.name
        }
        return null
    }

    fun handleLocationUpdate(myLocation: LatLng): NavigationUpdate? {
        if (_currentRoute.value != null && _navStartLocation.value == null) {
            _navStartLocation.value = myLocation
        }
        val target = _targetLocation.value ?: return null
        val distance = calculateDistance(myLocation, target.first)

        var result: NavigationUpdate? = NavigationUpdate(target.second, distance)

        val route = _currentRoute.value
        if (route != null) {
            if (distance < AppConstants.NAVIGATION_PROXIMITY_THRESHOLD) {
                val nextName = skipNextWaypoint()
                if (nextName != null) {
                    val nextTarget = _targetLocation.value
                    if (nextTarget != null) {
                        result = NavigationUpdate(nextName, calculateDistance(myLocation, nextTarget.first), true)
                    }
                } else {
                    result = NavigationUpdate(target.second, distance, false, true)
                }
            }
        }
        return result
    }

    private fun saveState() {
        val route = _currentRoute.value
        if (route != null) {
            navigationRepository.saveNavigationState(route.id, _currentWaypointIndex.value)
        } else {
            _targetLocation.value?.let {
                navigationRepository.saveTargetState(it.first, it.second)
            }
        }
    }

    suspend fun resumeState() {
        val routeId = navigationRepository.getNavRouteId()
        if (routeId != -1L) {
            val route = routeRepository.getRouteWithWaypoints(routeId)
            if (route != null && route.waypoints.size >= 2) {
                _currentRoute.value = route
                val index = navigationRepository.getNavIndex().coerceIn(0, route.waypoints.size - 1)
                _currentWaypointIndex.value = index
                val waypoint = route.waypoints[index]
                _targetLocation.value = Pair(LatLng(waypoint.latitude, waypoint.longitude), waypoint.name)
            } else if (route != null && route.waypoints.size == 1) {
                val waypoint = route.waypoints[0]
                setTarget(LatLng(waypoint.latitude, waypoint.longitude), waypoint.name)
            } else {
                stopNavigation()
            }
        } else {
            val latLng = navigationRepository.getNavTargetLatLng()
            if (latLng != null) {
                val name = navigationRepository.getNavTargetName() ?: "目的地"
                _targetLocation.value = Pair(latLng, name)
            }
        }
    }

    private fun calculateDistance(from: LatLng, to: LatLng): Float {
        val results = FloatArray(1)
        Location.distanceBetween(from.latitude, from.longitude, to.latitude, to.longitude, results)
        return results[0]
    }

    data class NavigationUpdate(
        val targetName: String,
        val distance: Float,
        val nextWaypointReached: Boolean = false,
        val routeCompleted: Boolean = false
    )
}
