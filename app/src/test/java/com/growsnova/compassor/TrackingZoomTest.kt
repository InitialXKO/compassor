package com.growsnova.compassor

import com.amap.api.maps.model.LatLng
import com.growsnova.compassor.manager.MapManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingZoomTest {

    private fun calculateZoom(
        userLatLng: LatLng,
        azimuth: Float,
        targets: List<LatLng>,
        topPaddingPx: Int = 0,
        bottomPaddingPx: Int = 0,
        widthPixels: Int = 1080,
        heightPixels: Int = 2400,
        density: Float = 2.75f,
        minZoom: Float = 3f,
        maxZoom: Float = 19f
    ): Float {
        val mapManager = MapManager(null)
        if (topPaddingPx > 0 || bottomPaddingPx > 0) {
            val fieldTop = MapManager::class.java.getDeclaredField("topPaddingPx")
            fieldTop.isAccessible = true
            fieldTop.set(mapManager, topPaddingPx)

            val fieldBottom = MapManager::class.java.getDeclaredField("bottomPaddingPx")
            fieldBottom.isAccessible = true
            fieldBottom.set(mapManager, bottomPaddingPx)
        }

        return mapManager.calculateTrackingZoomLevel(
            userLatLng = userLatLng,
            azimuth = azimuth,
            targetLocations = targets,
            minZoom = minZoom,
            maxZoom = maxZoom,
            widthPixels = widthPixels,
            heightPixels = heightPixels,
            density = density
        )
    }

    @Test
    fun testEmptyTargetListReturnsMaxZoom() {
        val userLocation = LatLng(39.9, 116.4)
        val zoom = calculateZoom(
            userLatLng = userLocation,
            azimuth = 0f,
            targets = emptyList(),
            maxZoom = 19f
        )
        assertEquals(19f, zoom, 0.001f)
    }

    @Test
    fun testVeryCloseTargetReturnsMaxZoom() {
        val userLocation = LatLng(39.9, 116.4)
        val closeTarget = LatLng(39.90004, 116.4) // 5 meters away
        val zoom = calculateZoom(
            userLatLng = userLocation,
            azimuth = 0f,
            targets = listOf(closeTarget),
            maxZoom = 19f
        )
        assertEquals(19f, zoom, 0.001f)
    }

    @Test
    fun testMediumDistanceTargetZoomCalculation() {
        val userLocation = LatLng(39.9, 116.4)
        val target = LatLng(39.909, 116.4) // ~1km away
        val zoom = calculateZoom(
            userLatLng = userLocation,
            azimuth = 0f,
            targets = listOf(target)
        )
        assertTrue("Zoom should be < 19f for 1km target", zoom < 19f)
        assertTrue("Zoom should be > 14f for 1km target", zoom > 14f)
    }

    @Test
    fun testFarDistanceTargetZoomCalculation() {
        val userLocation = LatLng(39.9, 116.4)
        val target = LatLng(39.99, 116.4) // ~10km away
        val zoom1km = calculateZoom(
            userLatLng = userLocation,
            azimuth = 0f,
            targets = listOf(LatLng(39.909, 116.4))
        )
        val zoom10km = calculateZoom(
            userLatLng = userLocation,
            azimuth = 0f,
            targets = listOf(target)
        )
        assertTrue("10km target zoom should be smaller than 1km target zoom", zoom10km < zoom1km)
    }

    @Test
    fun testBottomPaddingAdjustsZoom() {
        val userLocation = LatLng(39.9, 116.4)
        val targetSouth = LatLng(39.891, 116.4) // Target South
        val zoomWithoutPadding = calculateZoom(
            userLatLng = userLocation,
            azimuth = 0f,
            targets = listOf(targetSouth),
            bottomPaddingPx = 0
        )
        val zoomWithPadding = calculateZoom(
            userLatLng = userLocation,
            azimuth = 0f,
            targets = listOf(targetSouth),
            bottomPaddingPx = 600
        )
        assertTrue("Zoom with bottom padding should be smaller or equal to fit in smaller area", zoomWithPadding <= zoomWithoutPadding)
    }

    @Test
    fun testMultipleTargetsUsesMinimumZoom() {
        val userLocation = LatLng(39.9, 116.4)
        val closeTarget = LatLng(39.902, 116.4) // ~200m
        val farTarget = LatLng(39.95, 116.4)   // ~5.5km

        val zoomFarOnly = calculateZoom(
            userLatLng = userLocation,
            azimuth = 0f,
            targets = listOf(farTarget)
        )
        val zoomBoth = calculateZoom(
            userLatLng = userLocation,
            azimuth = 0f,
            targets = listOf(closeTarget, farTarget)
        )
        assertEquals(zoomFarOnly, zoomBoth, 0.001f)
    }
}
