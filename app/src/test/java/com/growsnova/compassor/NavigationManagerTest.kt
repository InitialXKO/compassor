package com.growsnova.compassor

import com.growsnova.compassor.data.repository.NavigationRepository
import com.growsnova.compassor.data.repository.RouteRepository
import com.growsnova.compassor.manager.NavigationManager
import com.amap.api.maps.model.LatLng
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

class NavigationManagerTest {

    private lateinit var navigationRepository: NavigationRepository
    private lateinit var routeRepository: RouteRepository
    private lateinit var navigationManager: NavigationManager

    @Before
    fun setUp() {
        navigationRepository = Mockito.mock(NavigationRepository::class.java)
        routeRepository = Mockito.mock(RouteRepository::class.java)
        navigationManager = NavigationManager(navigationRepository, routeRepository)
    }

    @Test
    fun testStartRouteNavigation_withTwoWaypoints_startsRouteNavigation() {
        val waypoints = mutableListOf(
            Waypoint(id = 1, name = "W1", latitude = 10.0, longitude = 20.0),
            Waypoint(id = 2, name = "W2", latitude = 10.1, longitude = 20.1)
        )
        val route = Route(id = 100, name = "Route 1", waypoints = waypoints)

        navigationManager.startRouteNavigation(route)

        assertEquals(route, navigationManager.currentRoute.value)
        assertEquals(0, navigationManager.currentWaypointIndex.value)
        assertEquals("W1", navigationManager.targetLocation.value?.second)
    }

    @Test
    fun testStartRouteNavigation_withOneWaypoint_degradesToSingleTargetNavigation() {
        val waypoints = mutableListOf(
            Waypoint(id = 1, name = "W1", latitude = 10.0, longitude = 20.0)
        )
        val route = Route(id = 100, name = "Route 1", waypoints = waypoints)

        navigationManager.startRouteNavigation(route)

        assertNull(navigationManager.currentRoute.value)
        assertEquals(-1, navigationManager.currentWaypointIndex.value)
        assertEquals("W1", navigationManager.targetLocation.value?.second)
    }

    @Test
    fun testOnRouteDeleted_stopsActiveNavigation() {
        val waypoints = mutableListOf(
            Waypoint(id = 1, name = "W1", latitude = 10.0, longitude = 20.0),
            Waypoint(id = 2, name = "W2", latitude = 10.1, longitude = 20.1)
        )
        val route = Route(id = 100, name = "Route 1", waypoints = waypoints)

        navigationManager.startRouteNavigation(route)
        assertEquals(route, navigationManager.currentRoute.value)

        navigationManager.onRouteDeleted(1, "Route 1")

        assertNull(navigationManager.currentRoute.value)
        assertEquals(-1, navigationManager.currentWaypointIndex.value)
        assertNull(navigationManager.navStartLocation.value)
        assertNull(navigationManager.targetLocation.value)
    }

    @Test
    fun testOnRouteDeleted_whenDegraded_stopsSingleTargetNavigation() {
        val waypoints = mutableListOf(
            Waypoint(id = 1, name = "W1", latitude = 10.0, longitude = 20.0),
            Waypoint(id = 2, name = "W2", latitude = 10.1, longitude = 20.1)
        )
        val route = Route(id = 100, name = "Route 1", waypoints = waypoints)

        navigationManager.startRouteNavigation(route)
        assertEquals(route, navigationManager.currentRoute.value)

        navigationManager.onWaypointDeleted(Waypoint(id = 2, name = "W2", latitude = 10.1, longitude = 20.1))

        // User now starts a new single-target navigation to a different point
        navigationManager.setTarget(LatLng(30.0, 40.0), "Custom Target")
        assertNull(navigationManager.currentRoute.value)
        assertEquals("Custom Target", navigationManager.targetLocation.value?.second)

        // User deletes old Route 100 -> Should NOT stop current "Custom Target" navigation
        navigationManager.onRouteDeleted(100)
        assertNotNull(navigationManager.targetLocation.value)
        assertEquals("Custom Target", navigationManager.targetLocation.value?.second)
    }

    @Test
    fun testOnWaypointDeleted_whenActiveTargetDeleted_stopsNavigation() {
        val waypoints = mutableListOf(
            Waypoint(id = 1, name = "W1", latitude = 10.0, longitude = 20.0),
            Waypoint(id = 2, name = "W2", latitude = 10.1, longitude = 20.1)
        )
        val route = Route(id = 100, name = "Route 1", waypoints = waypoints)

        navigationManager.startRouteNavigation(route)
        // Degrade to 1 waypoint W1
        navigationManager.onWaypointDeleted(2)
        assertEquals("W1", navigationManager.targetLocation.value?.second)

        // Delete W1 (the active single target) -> Should stop navigation
        navigationManager.onWaypointDeleted(1)
        assertNull(navigationManager.targetLocation.value)
    }

    @Test
    fun testOnWaypointDeleted_whenOtherWaypointDeleted_continuesNavigation() {
        val waypoints = mutableListOf(
            Waypoint(id = 1, name = "W1", latitude = 10.0, longitude = 20.0),
            Waypoint(id = 2, name = "W2", latitude = 10.1, longitude = 20.1)
        )
        val route = Route(id = 100, name = "Route 1", waypoints = waypoints)

        navigationManager.startRouteNavigation(route)
        // Degrade to 1 waypoint W1
        navigationManager.onWaypointDeleted(2)
        assertEquals("W1", navigationManager.targetLocation.value?.second)

        // Delete unrelated waypoint 999 -> Should continue navigating to W1
        navigationManager.onWaypointDeleted(999)
        assertEquals("W1", navigationManager.targetLocation.value?.second)
    }

    @Test
    fun testResumeState_withOneWaypointRoute_degradesToSingleTarget() = runBlocking {
        val waypoints = mutableListOf(
            Waypoint(id = 1, name = "W1", latitude = 10.0, longitude = 20.0)
        )
        val route = Route(id = 100, name = "Route 1", waypoints = waypoints)

        Mockito.`when`(navigationRepository.getNavRouteId()).thenReturn(100L)
        Mockito.`when`(routeRepository.getRouteWithWaypoints(100L)).thenReturn(route)

        navigationManager.resumeState()

        assertNull(navigationManager.currentRoute.value)
        assertEquals(-1, navigationManager.currentWaypointIndex.value)
        assertEquals("W1", navigationManager.targetLocation.value?.second)
    }
}
