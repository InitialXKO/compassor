package com.growsnova.compassor

import com.growsnova.compassor.data.repository.NavigationRepository
import com.growsnova.compassor.data.repository.RouteRepository
import com.growsnova.compassor.manager.NavigationManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
        val route = Route(id = 1, name = "Route 1", waypoints = waypoints)

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
        val route = Route(id = 1, name = "Route 1", waypoints = waypoints)

        navigationManager.startRouteNavigation(route)

        assertNull(navigationManager.currentRoute.value)
        assertEquals(-1, navigationManager.currentWaypointIndex.value)
        assertEquals("W1", navigationManager.targetLocation.value?.second)
    }

    @Test
    fun testOnRouteDeleted_clearsCurrentRouteAndStartLocation() {
        val waypoints = mutableListOf(
            Waypoint(id = 1, name = "W1", latitude = 10.0, longitude = 20.0),
            Waypoint(id = 2, name = "W2", latitude = 10.1, longitude = 20.1)
        )
        val route = Route(id = 1, name = "Route 1", waypoints = waypoints)

        navigationManager.startRouteNavigation(route)
        assertEquals(route, navigationManager.currentRoute.value)

        navigationManager.onRouteDeleted(1)

        assertNull(navigationManager.currentRoute.value)
        assertEquals(-1, navigationManager.currentWaypointIndex.value)
        assertNull(navigationManager.navStartLocation.value)
        assertEquals("W1", navigationManager.targetLocation.value?.second)
    }

    @Test
    fun testOnWaypointDeleted_whenOneWaypointRemains_degradesToSingleTarget() {
        val waypoints = mutableListOf(
            Waypoint(id = 1, name = "W1", latitude = 10.0, longitude = 20.0),
            Waypoint(id = 2, name = "W2", latitude = 10.1, longitude = 20.1)
        )
        val route = Route(id = 1, name = "Route 1", waypoints = waypoints)

        navigationManager.startRouteNavigation(route)
        assertEquals(route, navigationManager.currentRoute.value)

        navigationManager.onWaypointDeleted(2)

        assertNull(navigationManager.currentRoute.value)
        assertEquals(-1, navigationManager.currentWaypointIndex.value)
        assertEquals("W1", navigationManager.targetLocation.value?.second)
    }

    @Test
    fun testResumeState_withOneWaypointRoute_degradesToSingleTarget() = runBlocking {
        val waypoints = mutableListOf(
            Waypoint(id = 1, name = "W1", latitude = 10.0, longitude = 20.0)
        )
        val route = Route(id = 1, name = "Route 1", waypoints = waypoints)

        Mockito.`when`(navigationRepository.getNavRouteId()).thenReturn(1L)
        Mockito.`when`(routeRepository.getRouteWithWaypoints(1L)).thenReturn(route)

        navigationManager.resumeState()

        assertNull(navigationManager.currentRoute.value)
        assertEquals(-1, navigationManager.currentWaypointIndex.value)
        assertEquals("W1", navigationManager.targetLocation.value?.second)
    }
}
