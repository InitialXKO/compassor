package com.growsnova.compassor

import org.junit.Assert.*
import org.junit.Test

class GeoJsonKmlUtilsTest {

    @Test
    fun testGeoJsonWaypointsExportAndImport() {
        val waypoints = listOf(
            Waypoint(id = 1, name = "Beijing", latitude = 39.9042, longitude = 116.4074, remarks = "Capital"),
            Waypoint(id = 2, name = "Shanghai", latitude = 31.2304, longitude = 121.4737, floor = 2)
        )

        val json = GeoJsonUtils.exportWaypointsToGeoJson(waypoints)
        assertTrue(json.contains("Beijing"))
        assertTrue(json.contains("FeatureCollection"))

        val imported = GeoJsonUtils.importGeoJsonToWaypoints(json)
        assertEquals(2, imported.size)
        assertEquals("Beijing", imported[0].name)
        assertEquals("Capital", imported[0].remarks)
        assertEquals("Shanghai", imported[1].name)
        assertEquals(2, imported[1].floor)
    }

    @Test
    fun testGeoJsonRouteExportAndImport() {
        val waypoints = listOf(
            Waypoint(id = 1, name = "A", latitude = 30.0, longitude = 120.0),
            Waypoint(id = 2, name = "B", latitude = 31.0, longitude = 121.0)
        )
        val route = Route(id = 10, name = "Trip", isLooping = true, waypoints = waypoints.toMutableList())

        val json = GeoJsonUtils.exportRouteToGeoJson(route)
        val imported = GeoJsonUtils.importGeoJsonToRoute(json)

        assertNotNull(imported)
        val (importedRoute, importedWaypoints) = imported!!
        assertEquals("Trip", importedRoute.name)
        assertTrue(importedRoute.isLooping)
        assertEquals(2, importedWaypoints.size)
    }

    @Test
    fun testKmlWaypointsExportAndImport() {
        val waypoints = listOf(
            Waypoint(id = 1, name = "Spot A", latitude = 39.9, longitude = 116.4, remarks = "Test Spot")
        )

        val kml = KmlUtils.exportWaypointsToKml(waypoints)
        assertTrue(kml.contains("<kml"))
        assertTrue(kml.contains("Spot A"))

        val imported = KmlUtils.importKmlToWaypoints(kml)
        assertEquals(1, imported.size)
        assertEquals("Spot A", imported[0].name)
        assertEquals("Test Spot", imported[0].remarks)
    }

    @Test
    fun testKmlRouteExportAndImport() {
        val waypoints = listOf(
            Waypoint(id = 1, name = "P1", latitude = 30.0, longitude = 120.0),
            Waypoint(id = 2, name = "P2", latitude = 31.0, longitude = 121.0)
        )
        val route = Route(id = 5, name = "Kml Route Test", waypoints = waypoints.toMutableList())

        val kml = KmlUtils.exportRouteToKml(route)
        val imported = KmlUtils.importKmlToRoute(kml)

        assertNotNull(imported)
        val (importedRoute, importedWaypoints) = imported!!
        assertEquals("Kml Route Test", importedRoute.name)
        assertEquals(2, importedWaypoints.size)
    }
}
