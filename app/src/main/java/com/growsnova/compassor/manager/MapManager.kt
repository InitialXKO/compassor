package com.growsnova.compassor.manager

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.*
import com.growsnova.compassor.R
import com.growsnova.compassor.Waypoint
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject

@ActivityScoped
class MapManager @Inject constructor(
    @ActivityContext private val context: Context
) {
    private var aMap: AMap? = null
    private val waypointMarkers = mutableMapOf<Long, Marker>()
    private var targetMarker: Marker? = null
    private var targetMarkerClickListener: (() -> Unit)? = null
    private var completedPolyline: Polyline? = null
    private var remainingPolyline: Polyline? = null
    private var guidancePolyline: Polyline? = null
    private var multiPointOverlay: MultiPointOverlay? = null
    private var locationListener: com.amap.api.maps.LocationSource.OnLocationChangedListener? = null
    private var isFirstLocation = true
    private var lastLatLng: LatLng? = null
    private var lastAzimuth: Float? = null

    fun initialize(map: AMap) {
        this.aMap = map
        setupMapSettings()
        setupLocationSource()
    }

    private fun setupLocationSource() {
        aMap?.setLocationSource(object : com.amap.api.maps.LocationSource {
            override fun activate(listener: com.amap.api.maps.LocationSource.OnLocationChangedListener?) {
                locationListener = listener
            }

            override fun deactivate() {
                locationListener = null
            }
        })
        aMap?.isMyLocationEnabled = true
    }

    fun updateMyLocation(latLng: LatLng, azimuth: Float? = null) {
        lastLatLng = latLng
        if (azimuth != null) {
            lastAzimuth = azimuth
        }
        val currentAzimuth = azimuth ?: lastAzimuth

        val location = android.location.Location("custom").apply {
            latitude = latLng.latitude
            longitude = latLng.longitude
            accuracy = 10f // dummy
            time = System.currentTimeMillis()
            currentAzimuth?.let { bearing = it }
        }
        locationListener?.onLocationChanged(location)

        if (isFirstLocation) {
            isFirstLocation = false
            animateToLocation(latLng, 16f)
        }
    }

    fun updateMyLocationAzimuth(azimuth: Float) {
        lastAzimuth = azimuth
        val latLng = lastLatLng ?: return

        val location = android.location.Location("custom").apply {
            latitude = latLng.latitude
            longitude = latLng.longitude
            accuracy = 10f // dummy
            time = System.currentTimeMillis()
            bearing = azimuth
        }
        locationListener?.onLocationChanged(location)
    }

    private fun setupMapSettings() {
        aMap?.apply {
            uiSettings.isZoomControlsEnabled = true
            uiSettings.isCompassEnabled = true
            uiSettings.isMyLocationButtonEnabled = true
            mapType = AMap.MAP_TYPE_NORMAL
        }
    }

    fun updatePadding(left: Int = 0, top: Int = 0, right: Int = 0, bottom: Int = 0) {
        val width = context.resources.displayMetrics.widthPixels
        val height = context.resources.displayMetrics.heightPixels
        val centerX = width / 2
        val centerY = (height - top - bottom) / 2 + top
        aMap?.setPointToCenter(centerX, centerY)
    }

    fun resetBearing() {
        aMap?.let { map ->
            val currentCamera = map.cameraPosition
            val updatedCamera = CameraPosition.builder(currentCamera)
                .bearing(0f)
                .build()
            map.animateCamera(CameraUpdateFactory.newCameraPosition(updatedCamera))
        }
    }

    fun updateWaypoints(waypoints: List<Waypoint>, onMarkerClick: (Waypoint) -> Unit) {
        val map = aMap ?: return

        // Use MultiPointOverlay for large number of points if needed
        if (waypoints.size > 100) {
            setupMultiPointOverlay(waypoints)
            // Clear markers if we are using overlay
            waypointMarkers.values.forEach { it.remove() }
            waypointMarkers.clear()
        } else {
            multiPointOverlay?.remove()
            multiPointOverlay = null

            // Standard marker management
            val currentIds = waypoints.map { it.id }.toSet()

            // Remove markers for waypoints that are gone
            val idsToRemove = waypointMarkers.keys.filter { it !in currentIds }
            idsToRemove.forEach { id ->
                waypointMarkers[id]?.remove()
                waypointMarkers.remove(id)
            }

            // Add or update markers
            waypoints.forEach { waypoint ->
                val latLng = LatLng(waypoint.latitude, waypoint.longitude)
                val marker = waypointMarkers[waypoint.id]
                if (marker == null) {
                    val newMarker = map.addMarker(
                        MarkerOptions()
                            .position(latLng)
                            .title(waypoint.name)
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                    )
                    waypointMarkers[waypoint.id] = newMarker
                } else {
                    marker.position = latLng
                    marker.title = waypoint.name
                }
            }
        }

        map.setOnMarkerClickListener { marker ->
            if (marker == targetMarker) {
                targetMarkerClickListener?.invoke()
            } else {
                val waypointId = waypointMarkers.entries.find { it.value == marker }?.key
                if (waypointId != null) {
                    waypoints.find { it.id == waypointId }?.let { onMarkerClick(it) }
                }
            }
            true
        }
    }

    private fun setupMultiPointOverlay(waypoints: List<Waypoint>) {
        val map = aMap ?: return
        if (multiPointOverlay == null) {
            val options = MultiPointOverlayOptions()
            options.icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_waypoint_small)) // Assume we have this
            options.anchor(0.5f, 0.5f)
            multiPointOverlay = map.addMultiPointOverlay(options)
        }

        val items = waypoints.map {
            MultiPointItem(LatLng(it.latitude, it.longitude)).apply {
                customerId = it.id.toString()
                title = it.name
            }
        }
        multiPointOverlay?.items = items
    }

    fun setTargetLocation(
        latLng: LatLng,
        title: String,
        startLocation: LatLng? = null,
        onTargetClick: (() -> Unit)? = null
    ) {
        val map = aMap ?: return
        targetMarkerClickListener = onTargetClick
        targetMarker?.remove()
        targetMarker = map.addMarker(
            MarkerOptions()
                .position(latLng)
                .title(title)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
        )
        zoomToFitStartAndTargets(startLocation ?: lastLatLng, listOf(latLng))
    }

    fun zoomToFitStartAndTargets(startLocation: LatLng?, targetLocations: List<LatLng>, paddingPx: Int = 180) {
        val map = aMap ?: return
        val validPoints = mutableListOf<LatLng>()
        startLocation?.let { validPoints.add(it) }
        validPoints.addAll(targetLocations)

        if (validPoints.isEmpty()) return

        if (validPoints.size == 1) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(validPoints[0], 15f))
            return
        }

        val builder = LatLngBounds.Builder()
        for (pt in validPoints) {
            builder.include(pt)
        }

        val bounds = builder.build()
        val dist = FloatArray(1)
        android.location.Location.distanceBetween(
            bounds.southwest.latitude, bounds.southwest.longitude,
            bounds.northeast.latitude, bounds.northeast.longitude,
            dist
        )
        if (dist[0] < 10) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(validPoints[0], 15f))
        } else {
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, paddingPx))
        }
    }

    fun clearTarget() {
        targetMarker?.remove()
        targetMarker = null
    }

    fun drawRoute(
        waypoints: List<Waypoint>,
        currentIndex: Int,
        navStartLocation: LatLng? = null,
        primaryColor: Int = android.graphics.Color.BLUE,
        traveledColor: Int = android.graphics.Color.GRAY
    ) {
        val map = aMap ?: return
        completedPolyline?.remove()
        completedPolyline = null
        remainingPolyline?.remove()
        remainingPolyline = null

        if (waypoints.isEmpty()) return

        val waypointsLatLng = waypoints.map { LatLng(it.latitude, it.longitude) }
        val startPt = navStartLocation ?: waypointsLatLng.firstOrNull() ?: return

        // 灰线：导航起点 → 已走过的航点
        if (currentIndex > 0) {
            val completed = listOf(startPt) + waypointsLatLng.take(currentIndex)
            if (completed.size >= 2) {
                completedPolyline = map.addPolyline(
                    PolylineOptions()
                        .addAll(completed)
                        .color(traveledColor)
                        .width(10f)
                )
            }
        }

        // 蓝线：从导航起点(或前一航点) → 当前及后续航点
        val remaining = if (currentIndex <= 0) {
            listOf(startPt) + waypointsLatLng
        } else {
            waypointsLatLng.drop((currentIndex - 1).coerceAtLeast(0))
        }

        if (remaining.size >= 2) {
            remainingPolyline = map.addPolyline(
                PolylineOptions()
                    .addAll(remaining)
                    .color(primaryColor)
                    .width(10f)
            )
        }

        updateWaypointMarkerStyles(waypoints, currentIndex)
    }

    private fun updateWaypointMarkerStyles(waypoints: List<Waypoint>, currentIndex: Int) {
        waypointMarkers.forEach { (id, marker) ->
            val index = waypoints.indexOfFirst { it.id == id }
            if (index != -1) {
                when {
                    index < currentIndex -> {
                        marker.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET))
                        marker.alpha = 0.5f
                    }
                    index == currentIndex -> {
                        marker.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                        marker.alpha = 1.0f
                    }
                    else -> {
                        marker.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                        marker.alpha = 1.0f
                    }
                }
            }
        }
    }

    fun updateGuidanceLine(myLoc: LatLng, targetLoc: LatLng, color: Int) {
        guidancePolyline?.remove()
        guidancePolyline = aMap?.addPolyline(
            PolylineOptions()
                .add(myLoc, targetLoc)
                .color(color and 0x80FFFFFF.toInt())
                .width(6f)
                .setDottedLine(true)
                .setDottedLineType(PolylineOptions.DOTTEDLINE_TYPE_SQUARE)
        )
    }

    fun clearRoute() {
        completedPolyline?.remove()
        completedPolyline = null
        remainingPolyline?.remove()
        remainingPolyline = null
    }

    fun clearAllNavigation() {
        clearTarget()
        clearRoute()
        guidancePolyline?.remove()
        guidancePolyline = null
    }

    fun animateToLocation(latLng: LatLng, zoom: Float? = null) {
        if (zoom != null) {
            aMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoom))
        } else {
            aMap?.animateCamera(CameraUpdateFactory.newLatLng(latLng))
        }
    }
}
