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

    private var activeRouteId: Long? = null
    private var activeTargetWaypointId: Long? = null

    fun startRouteNavigation(route: Route, startLocation: LatLng? = null) {
        if (route.waypoints.size < 2) {
            if (route.waypoints.size == 1) {
                val singleWaypoint = route.waypoints[0]
                setTarget(LatLng(singleWaypoint.latitude, singleWaypoint.longitude), singleWaypoint.name)
                activeRouteId = route.id
                activeTargetWaypointId = singleWaypoint.id
            } else {
                stopNavigation()
            }
            return
        }
        _currentRoute.value = route
        _currentWaypointIndex.value = 0
        _navStartLocation.value = startLocation
        activeRouteId = route.id
        val firstWaypoint = route.waypoints[0]
        activeTargetWaypointId = firstWaypoint.id
        updateTargetInternal(LatLng(firstWaypoint.latitude, firstWaypoint.longitude), firstWaypoint.name)
        saveState()
    }

    fun setTarget(latLng: LatLng, name: String) {
        _currentRoute.value = null
        _currentWaypointIndex.value = -1
        _navStartLocation.value = null
        activeRouteId = null
        activeTargetWaypointId = null
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
        activeRouteId = null
        activeTargetWaypointId = null
        navigationRepository.clearNavigationState()
    }

    fun onRouteDeleted(routeId: Long, routeName: String? = null) {
        val current = _currentRoute.value
        if (current != null && (current.id == routeId || (routeName != null && current.name == routeName))) {
            _currentRoute.value = null
            _currentWaypointIndex.value = -1
            _navStartLocation.value = null
            saveState()
        }
    }

    fun onWaypointDeleted(waypoint: Waypoint) {
        val activeRoute = _currentRoute.value ?: run {
            _targetLocation.value?.let { target ->
                val dist = FloatArray(1)
                Location.distanceBetween(target.first.latitude, target.first.longitude, waypoint.latitude, waypoint.longitude, dist)
                if (dist[0] < 5f || target.second == waypoint.name) {
                    stopNavigation()
                }
            }

        val deletedIndex = activeRoute.waypoints.indexOfFirst {
            (it.id != 0L && it.id == waypoint.id) ||
            (it.latitude == waypoint.latitude && it.longitude == waypoint.longitude) ||
            it.name == waypoint.name
        }
        if (deletedIndex == -1) return

        val newWaypoints = activeRoute.waypoints.filterIndexed { i, _ -> i != deletedIndex }.toMutableList()
        if (newWaypoints.size < 2) {
            if (newWaypoints.size == 1) {
                val remainingWaypoint = newWaypoints[0]
                setTarget(LatLng(remainingWaypoint.latitude, remainingWaypoint.longitude), remainingWaypoint.name)
            } else {
                stopNavigation()
            }

            _currentRoute.value = updatedRoute
            _currentWaypointIndex.value = newIndex
            val targetWaypoint = newWaypoints[newIndex]
            activeTargetWaypointId = targetWaypoint.id
            updateTargetInternal(LatLng(targetWaypoint.latitude, targetWaypoint.longitude), targetWaypoint.name)
            saveState()
        } else if (_targetLocation.value != null) {
            if (activeTargetWaypointId == waypointId && waypointId != 0L) {
                stopNavigation()
            }
        }
    }

    fun skipNextWaypoint(): String? {
        val route = _currentRoute.value ?: return null
        val index = _currentWaypointIndex.value
        if (index < route.waypoints.size - 1) {
            _currentWaypointIndex.value = index + 1
            val nextWaypoint = route.waypoints[index + 1]
            activeTargetWaypointId = nextWaypoint.id
            updateTargetInternal(LatLng(nextWaypoint.latitude, nextWaypoint.longitude), nextWaypoint.name)
            saveState()
            return nextWaypoint.name
        } else if (route.isLooping) {
            _currentWaypointIndex.value = 0
            val firstWaypoint = route.waypoints[0]
            activeTargetWaypointId = firstWaypoint.id
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
            activeTargetWaypointId = prevWaypoint.id
            updateTargetInternal(LatLng(prevWaypoint.latitude, prevWaypoint.longitude), prevWaypoint.name)
            saveState()
            return prevWaypoint.name
        } else if (route.isLooping) {
            val lastIndex = route.waypoints.size - 1
            _currentWaypointIndex.value = lastIndex
            val lastWaypoint = route.waypoints[lastIndex]
            activeTargetWaypointId = lastWaypoint.id
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
                activeRouteId = route.id
                activeTargetWaypointId = waypoint.id
                _targetLocation.value = Pair(LatLng(waypoint.latitude, waypoint.longitude), waypoint.name)
            } else if (route != null && route.waypoints.size == 1) {
                val waypoint = route.waypoints[0]
                setTarget(LatLng(waypoint.latitude, waypoint.longitude), waypoint.name)
                activeRouteId = route.id
                activeTargetWaypointId = waypoint.id
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
