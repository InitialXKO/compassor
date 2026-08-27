package com.growsnova.compassor

import org.junit.Assert.*
import org.junit.Test

class RouteJsonUtilsTest {

    @Test
    fun testExportAndImportRoute() {
        val waypoints = listOf(
            Waypoint(id = 1, name = "Start", latitude = 39.9, longitude = 116.4, floor = 1),
            Waypoint(id = 2, name = "End", latitude = 40.0, longitude = 116.5, remarks = "Stop here")
        )
        val route = Route(id = 10, name = "Test Route", isLooping = true, waypoints = waypoints.toMutableList())

        val json = RouteJsonUtils.exportRouteToJson(route)
        assertTrue(json.contains("Test Route"))
        assertTrue(json.contains("Start"))
        assertTrue(json.contains("End"))

        val imported = RouteJsonUtils.importRouteFromJson(json)
        assertNotNull(imported)
        val (importedRoute, importedWaypoints) = imported!!

        assertEquals("Test Route", importedRoute.name)
        assertTrue(importedRoute.isLooping)
        assertEquals(2, importedWaypoints.size)
        assertEquals("Start", importedWaypoints[0].name)
        assertEquals(1, importedWaypoints[0].floor)
        assertEquals("Stop here", importedWaypoints[1].remarks)
    }
}
