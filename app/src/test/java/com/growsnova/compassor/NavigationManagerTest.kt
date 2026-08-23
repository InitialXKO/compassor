package com.growsnova.compassor

import com.amap.api.maps.model.LatLng
import com.growsnova.compassor.data.repository.NavigationRepository
import com.growsnova.compassor.data.repository.RouteRepository
import com.growsnova.compassor.manager.NavigationManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

class NavigationManagerTest {

    private lateinit var navigationRepository: NavigationRepository
    private lateinit var routeRepository: RouteRepository
    private lateinit var navigationManager: NavigationManager

    @Before
    fun setUp() {
        navigationRepository = mock(NavigationRepository::class.java)
        routeRepository = mock(RouteRepository::class.java)
        navigationManager = NavigationManager(navigationRepository, routeRepository)
    }

    @Test
    fun testOnWaypointDeleted_degradesToSingleTargetNavigation() {
        val wp1 = Waypoint(id = 1L, name = "Point A", latitude = 39.9, longitude = 116.3)
        val wp2 = Waypoint(id = 2L, name = "Point B", latitude = 39.95, longitude = 116.35)
        val route = Route(id = 100L, name = "Test Route", waypoints = mutableListOf(wp1, wp2))

        navigationManager.startRouteNavigation(route)
        assertEquals(route, navigationManager.currentRoute.value)

        // Delete wp1 from route with 2 waypoints -> only 1 waypoint remaining (wp2)
        navigationManager.onWaypointDeleted(1L)

        // Route navigation should degrade to single-target navigation
        assertNull(navigationManager.currentRoute.value)
        assertEquals(-1, navigationManager.currentWaypointIndex.value)
        assertEquals("Point B", navigationManager.targetLocation.value?.second)
    }
}
