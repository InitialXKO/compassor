package com.growsnova.compassor

import com.growsnova.compassor.data.repository.NavigationRepository
import com.growsnova.compassor.data.repository.RouteRepository
import com.growsnova.compassor.manager.NavigationManager
import org.junit.Assert.*
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
    fun testStartRouteNavigation() {
        val wp1 = Waypoint(id = 1L, name = "Point 1", latitude = 30.0, longitude = 120.0)
        val wp2 = Waypoint(id = 2L, name = "Point 2", latitude = 30.1, longitude = 120.1)
        val route = Route(id = 100L, name = "Test Route", waypoints = mutableListOf(wp1, wp2))

        navigationManager.startRouteNavigation(route)

        assertEquals(route, navigationManager.currentRoute.value)
        assertEquals(0, navigationManager.currentWaypointIndex.value)
        assertNotNull(navigationManager.targetLocation.value)
        assertEquals("Point 1", navigationManager.targetLocation.value?.second)
    }

    @Test
    fun testStartRouteNavigationWithOneWaypointDegradesToSingleTarget() {
        val wp1 = Waypoint(id = 1L, name = "Point 1", latitude = 30.0, longitude = 120.0)
        val route = Route(id = 100L, name = "One Point Route", waypoints = mutableListOf(wp1))

        navigationManager.startRouteNavigation(route)

        assertNull(navigationManager.currentRoute.value)
        assertEquals(-1, navigationManager.currentWaypointIndex.value)
        assertNotNull(navigationManager.targetLocation.value)
        assertEquals("Point 1", navigationManager.targetLocation.value?.second)
    }

    @Test
    fun testDegradationOnWaypointDeletedFrom3To2() {
        val wp1 = Waypoint(id = 1L, name = "Point 1", latitude = 30.0, longitude = 120.0)
        val wp2 = Waypoint(id = 2L, name = "Point 2", latitude = 30.1, longitude = 120.1)
        val wp3 = Waypoint(id = 3L, name = "Point 3", latitude = 30.2, longitude = 120.2)
        val route = Route(id = 100L, name = "Test Route 3", waypoints = mutableListOf(wp1, wp2, wp3))

        navigationManager.startRouteNavigation(route)
        assertEquals(route, navigationManager.currentRoute.value)

        // Delete wp1
        navigationManager.onWaypointDeleted(1L)

        // Remaining waypoints = 2, route navigation should remain active
        assertNotNull(navigationManager.currentRoute.value)
        assertEquals(2, navigationManager.currentRoute.value?.waypoints?.size)
        assertEquals(0, navigationManager.currentWaypointIndex.value)
        assertEquals("Point 2", navigationManager.targetLocation.value?.second)
    }

    @Test
    fun testDegradationToSingleTargetWhenWaypointsReduceToOne() {
        val wp1 = Waypoint(id = 1L, name = "Point 1", latitude = 30.0, longitude = 120.0)
        val wp2 = Waypoint(id = 2L, name = "Point 2", latitude = 30.1, longitude = 120.1)
        val route = Route(id = 100L, name = "Test Route 2", waypoints = mutableListOf(wp1, wp2))

        navigationManager.startRouteNavigation(route)
        assertEquals(route, navigationManager.currentRoute.value)

        // Delete wp1, remaining waypoints = 1 (wp2)
        navigationManager.onWaypointDeleted(1L)

        // Route navigation degrades to single-target navigation:
        // currentRoute becomes null, currentWaypointIndex becomes -1
        assertNull(navigationManager.currentRoute.value)
        assertEquals(-1, navigationManager.currentWaypointIndex.value)

        // Target location remains set to the single remaining waypoint ("Point 2")
        assertNotNull(navigationManager.targetLocation.value)
        assertEquals("Point 2", navigationManager.targetLocation.value?.second)
        assertEquals(30.1, navigationManager.targetLocation.value?.first?.latitude ?: 0.0, 0.0001)
    }

    @Test
    fun testStopNavigation() {
        val wp1 = Waypoint(id = 1L, name = "Point 1", latitude = 30.0, longitude = 120.0)
        val wp2 = Waypoint(id = 2L, name = "Point 2", latitude = 30.1, longitude = 120.1)
        val route = Route(id = 100L, name = "Test Route", waypoints = mutableListOf(wp1, wp2))

        navigationManager.startRouteNavigation(route)
        assertEquals(route, navigationManager.currentRoute.value)

        navigationManager.stopNavigation()

        assertNull(navigationManager.currentRoute.value)
        assertNull(navigationManager.targetLocation.value)
        assertEquals(-1, navigationManager.currentWaypointIndex.value)
    }
}
