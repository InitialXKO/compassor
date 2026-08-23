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

    /**
     * 启动路线导航。
     * 若传入路线途经点不足 2 个，自动触发退化判断：
     * - 1 个途经点：退化为针对该点的单目标导航 (setTarget)；
     * - 0 个途经点：停止导航 (stopNavigation)。
     */
    fun startRouteNavigation(route: Route, startLocation: LatLng? = null) {
        if (route.waypoints.size < 2) {
            if (route.waypoints.size == 1) {
                val wp = route.waypoints[0]
                setTarget(LatLng(wp.latitude, wp.longitude), wp.name)
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

    /**
     * 设置单目标导航（单点/单一目的地导航）。
     * 当由路线导航退化为单目标导航，或用户手动选择单一目的地时调用：
     * 重置当前路线上下文（_currentRoute = null, _currentWaypointIndex = -1, _navStartLocation = null），
     * 仅保留目标位置 (_targetLocation)，完成向单目标导航的退化/切换。
     */
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

    /**
     * 当路线被删除时的回调。
     * 如果被删除的路线是当前正在导航的路线，退化为针对当前目标点的单目标导航（清除路线上下文）。
     */
    fun onRouteDeleted(routeId: Long) {
        if (_currentRoute.value?.id == routeId) {
            _currentRoute.value = null
            _currentWaypointIndex.value = -1
            saveState()
        }
    }

    /**
     * 当途经点被删除时的回调与退化逻辑：
     * 1. 若当前活跃路线不包含被删途经点，不作处理；
     * 2. 移除途经点后检查剩余途经点数量：
     *    - 剩余 1 个途经点：路线导航无法继续（路线导航至少需要 2 个途经点），退化为针对该唯一途经点的单目标导航。
     *      调用 setTarget(...) 将 _currentRoute 设为 null，仅保留 single-target 状态。
     *    - 剩余 0 个途经点：停止导航 (stopNavigation())。
     *    - 剩余 >= 2 个途经点：更新路线途经点列表并调整当前途经点索引 (_currentWaypointIndex)，保持路线导航。
     */
    fun onWaypointDeleted(waypointId: Long) {
        val activeRoute = _currentRoute.value ?: return

        val deletedIndex = activeRoute.waypoints.indexOfFirst { it.id == waypointId }
        if (deletedIndex == -1) return

        val newWaypoints = activeRoute.waypoints.filter { it.id != waypointId }.toMutableList()
        // 退化判断：路线导航至少需要 2 个途经点
        if (newWaypoints.size < 2) {
            if (newWaypoints.size == 1) {
                // 仅剩 1 个途经点：退化为单目标导航
                val remainingWaypoint = newWaypoints[0]
                setTarget(LatLng(remainingWaypoint.latitude, remainingWaypoint.longitude), remainingWaypoint.name)
            } else {
                // 0 个途经点：停止导航
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
                val wp = route.waypoints[0]
                setTarget(LatLng(wp.latitude, wp.longitude), wp.name)
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
