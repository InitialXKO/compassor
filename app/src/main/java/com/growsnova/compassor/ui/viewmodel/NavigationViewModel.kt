package com.growsnova.compassor.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amap.api.maps.model.LatLng
import com.amap.api.services.core.LatLonPoint
import com.growsnova.compassor.Route
import com.growsnova.compassor.Waypoint
import com.growsnova.compassor.data.repository.RouteRepository
import com.growsnova.compassor.data.repository.SearchRepository
import com.growsnova.compassor.data.repository.WaypointRepository
import com.growsnova.compassor.manager.NavigationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NavigationViewModel @Inject constructor(
    private val navigationManager: NavigationManager,
    private val waypointRepository: WaypointRepository,
    private val routeRepository: RouteRepository,
    private val searchRepository: SearchRepository,
    private val exceptionHandler: CoroutineExceptionHandler
) : ViewModel() {

    val currentRoute = navigationManager.currentRoute
    val currentWaypointIndex = navigationManager.currentWaypointIndex
    val targetLocation = navigationManager.targetLocation

    private val _navigationUpdate = MutableStateFlow<NavigationManager.NavigationUpdate?>(null)
    val navigationUpdate: StateFlow<NavigationManager.NavigationUpdate?> = _navigationUpdate.asStateFlow()

    private val _searchResults = MutableSharedFlow<List<com.amap.api.services.core.PoiItem>>(replay = 0)
    val searchResults: SharedFlow<List<com.amap.api.services.core.PoiItem>> = _searchResults.asSharedFlow()

    private val _errorFlow = MutableSharedFlow<String>(replay = 0)
    val errorFlow: SharedFlow<String> = _errorFlow.asSharedFlow()

    private val _routes = MutableStateFlow<List<Route>>(emptyList())
    val routes: StateFlow<List<Route>> = _routes.asStateFlow()

    init {
        resumeState()
        loadRoutes()
    }

    private fun resumeState() {
        viewModelScope.launch(exceptionHandler) {
            navigationManager.resumeState()
        }
    }

    fun loadRoutes() {
        viewModelScope.launch(exceptionHandler) {
            _routes.value = routeRepository.getRoutesWithWaypoints()
        }
    }

    fun startRouteNavigation(route: Route) {
        navigationManager.startRouteNavigation(route)
    }

    fun setTarget(latLng: LatLng, name: String) {
        navigationManager.setTarget(latLng, name)
    }

    fun stopNavigation() {
        navigationManager.stopNavigation()
    }

    fun skipNextWaypoint() {
        navigationManager.skipNextWaypoint()
    }

    fun goToPreviousWaypoint() {
        navigationManager.goToPreviousWaypoint()
    }

    fun updateLocation(myLocation: LatLng) {
        _navigationUpdate.value = navigationManager.handleLocationUpdate(myLocation)
    }

    fun searchPOI(keyword: String, myLocation: LatLng?) {
        viewModelScope.launch(exceptionHandler) {
            val center = myLocation?.let { LatLonPoint(it.latitude, it.longitude) }
            val result = searchRepository.searchPOI(keyword, center)
            result.onSuccess {
                _searchResults.emit(it.pois)
            }.onFailure {
                _errorFlow.emit(it.message ?: "Search failed")
            }
        }
    }

    fun reverseGeocode(latLng: LatLng, callback: (String) -> Unit) {
        viewModelScope.launch(exceptionHandler) {
            val result = searchRepository.reverseGeocode(latLng)
            result.onSuccess { callback(it) }
                .onFailure { callback("自定义位置") }
        }
    }

    fun deleteRoute(route: Route) {
        viewModelScope.launch(exceptionHandler) {
            routeRepository.deleteRoute(route)
            routeRepository.deleteCrossRefsForRoute(route.id)
            loadRoutes()
        }
    }

    fun addWaypoint(waypoint: Waypoint) {
        viewModelScope.launch(exceptionHandler) {
            waypointRepository.insertWaypoint(waypoint)
        }
    }

    fun updateWaypoint(waypoint: Waypoint) {
        viewModelScope.launch(exceptionHandler) {
            waypointRepository.updateWaypoint(waypoint)
            // If the waypoint is part of the current route, we might need to redraw
            if (currentRoute.value?.waypoints?.any { it.id == waypoint.id } == true) {
                // Trigger a refresh of current route if needed,
                // but since currentRoute is a StateFlow of the manager,
                // the manager should probably be informed or the repository update should ripple.
                // For now, let's just reload routes.
                loadRoutes()
            }
        }
    }

    fun deleteWaypoint(waypoint: Waypoint) {
        viewModelScope.launch(exceptionHandler) {
            // First find all routes containing this waypoint
            val allRoutes = routeRepository.getRoutesWithWaypoints()
            val affectedRoutes = allRoutes.filter { it.waypoints.any { w -> w.id == waypoint.id } }

            for (route in affectedRoutes) {
                routeRepository.deleteRouteWaypointCrossRef(com.growsnova.compassor.RouteWaypointCrossRef(route.id, waypoint.id))
                route.waypoints.removeIf { it.id == waypoint.id }
                if (route.waypoints.size < 2) {
                    routeRepository.deleteRoute(route)
                }
            }

            waypointRepository.deleteWaypoint(waypoint)
            loadRoutes()

            // If the deleted waypoint was part of current navigation, we might need to stop or skip
            if (targetLocation.value?.first?.latitude == waypoint.latitude &&
                targetLocation.value?.first?.longitude == waypoint.longitude) {
                navigationManager.stopNavigation()
            }
        }
    }

    fun saveNewRoute(route: Route, waypoints: List<Waypoint>, startNav: Boolean) {
        viewModelScope.launch(exceptionHandler) {
            val waypointIds = waypoints.map {
                if (it.id == 0L) waypointRepository.insertWaypoint(it) else it.id
            }
            val routeId = routeRepository.insertRoute(route)
            waypointIds.forEach { id ->
                routeRepository.insertRouteWaypointCrossRef(com.growsnova.compassor.RouteWaypointCrossRef(routeId, id))
            }
            loadRoutes()
            if (startNav) {
                val savedRoute = routeRepository.getRouteWithWaypoints(routeId)
                savedRoute?.let { startRouteNavigation(it) }
            }
        }
    }

    fun updateRoute(route: Route, waypoints: List<Waypoint>) {
        viewModelScope.launch(exceptionHandler) {
            routeRepository.updateRoute(route)
            routeRepository.deleteCrossRefsForRoute(route.id)
            waypoints.forEach { waypoint ->
                val id = if (waypoint.id == 0L) waypointRepository.insertWaypoint(waypoint) else waypoint.id
                routeRepository.insertRouteWaypointCrossRef(com.growsnova.compassor.RouteWaypointCrossRef(route.id, id))
            }
            loadRoutes()
            // If it's the current route, restart or update it
            if (currentRoute.value?.id == route.id) {
                val updated = routeRepository.getRouteWithWaypoints(route.id)
                updated?.let { startRouteNavigation(it) }
            }
        }
    }

    private val _recentSearches = MutableStateFlow<List<com.growsnova.compassor.SearchHistory>>(emptyList())
    val recentSearches: StateFlow<List<com.growsnova.compassor.SearchHistory>> = _recentSearches.asStateFlow()

    fun loadRecentSearches() {
        viewModelScope.launch(exceptionHandler) {
            _recentSearches.value = searchRepository.getRecentSearches()
        }
    }

    fun deleteSearchHistory(id: Long) {
        viewModelScope.launch(exceptionHandler) {
            searchRepository.deleteSearchHistory(id)
            loadRecentSearches()
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch(exceptionHandler) {
            searchRepository.clearAllHistory()
            _recentSearches.value = emptyList()
        }
    }

    fun onSearchPerformed(query: String) {
        viewModelScope.launch(exceptionHandler) {
            searchRepository.insertSearchHistory(query)
            loadRecentSearches()
        }
    }
}
