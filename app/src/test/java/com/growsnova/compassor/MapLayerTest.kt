package com.growsnova.compassor

import com.growsnova.compassor.manager.MapManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MapLayerTest {

    private lateinit var mapManager: MapManager

    @Before
    fun setUp() {
        mapManager = MapManager(null)
    }

    @Test
    fun testInitialMapLayerState() {
        assertEquals(MapManager.MapTypeMode.STANDARD, mapManager.getMapTypeMode())
        assertFalse(mapManager.isTrafficEnabled())
        assertTrue(mapManager.isIndoorEnabled())
        assertTrue(mapManager.isBuildingsEnabled())
    }

    @Test
    fun testSetMapTypeModeSatellite() {
        mapManager.setMapTypeMode(MapManager.MapTypeMode.SATELLITE, isNightMode = false)
        assertEquals(MapManager.MapTypeMode.SATELLITE, mapManager.getMapTypeMode())
    }

    @Test
    fun testSetMapTypeModeNight() {
        mapManager.setMapTypeMode(MapManager.MapTypeMode.NIGHT, isNightMode = false)
        assertEquals(MapManager.MapTypeMode.NIGHT, mapManager.getMapTypeMode())
    }

    @Test
    fun testSetMapTypeModeStandardSystemNight() {
        mapManager.setMapTypeMode(MapManager.MapTypeMode.STANDARD, isNightMode = true)
        assertEquals(MapManager.MapTypeMode.STANDARD, mapManager.getMapTypeMode())
    }

    @Test
    fun testSetMapTypeModeStandardSystemDay() {
        mapManager.setMapTypeMode(MapManager.MapTypeMode.STANDARD, isNightMode = false)
        assertEquals(MapManager.MapTypeMode.STANDARD, mapManager.getMapTypeMode())
    }

    @Test
    fun testTrafficOverlayToggle() {
        assertFalse(mapManager.isTrafficEnabled())

        mapManager.setTrafficEnabled(true)
        assertTrue(mapManager.isTrafficEnabled())

        mapManager.setTrafficEnabled(false)
        assertFalse(mapManager.isTrafficEnabled())
    }

    @Test
    fun testIndoorMapOverlayToggle() {
        assertTrue(mapManager.isIndoorEnabled())

        mapManager.setIndoorEnabled(false)
        assertFalse(mapManager.isIndoorEnabled())

        mapManager.setIndoorEnabled(true)
        assertTrue(mapManager.isIndoorEnabled())
    }

    @Test
    fun testBuildingsOverlayToggle() {
        assertTrue(mapManager.isBuildingsEnabled())

        mapManager.setBuildingsEnabled(false)
        assertFalse(mapManager.isBuildingsEnabled())

        mapManager.setBuildingsEnabled(true)
        assertTrue(mapManager.isBuildingsEnabled())
    }
}
