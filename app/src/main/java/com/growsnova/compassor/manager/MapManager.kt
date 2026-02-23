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
    private var routePolyline: Polyline? = null
    private var traveledPolyline: Polyline? = null
    private var guidancePolyline: Polyline? = null
    private var multiPointOverlay: MultiPointOverlay? = null
    private var locationListener: com.amap.api.maps.LocationSource.OnLocationChangedListener? = null

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

    fun updateMyLocation(latLng: LatLng) {
        val location = android.location.Location("custom").apply {
            latitude = latLng.latitude
            longitude = latLng.longitude
            accuracy = 10f // dummy
            time = System.currentTimeMillis()
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
            val waypointId = waypointMarkers.entries.find { it.value == marker }?.key
            if (waypointId != null) {
                waypoints.find { it.id == waypointId }?.let { onMarkerClick(it) }
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

    fun setTargetLocation(latLng: LatLng, title: String) {
        val map = aMap ?: return
        targetMarker?.remove()
        targetMarker = map.addMarker(
            MarkerOptions()
                .position(latLng)
                .title(title)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
        )
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
    }

    fun clearTarget() {
        targetMarker?.remove()
        targetMarker = null
    }

    fun drawRoute(waypoints: List<Waypoint>, currentIndex: Int, primaryColor: Int, traveledColor: Int) {
        val map = aMap ?: return
        routePolyline?.remove()
        traveledPolyline?.remove()

        if (waypoints.size < 2) return

        val points = waypoints.map { LatLng(it.latitude, it.longitude) }

        // Traveled portion
        if (currentIndex > 0) {
            val traveledPoints = points.subList(0, currentIndex + 1)
            traveledPolyline = map.addPolyline(
                PolylineOptions()
                    .addAll(traveledPoints)
                    .color(traveledColor and 0x80FFFFFF.toInt())
                    .width(10f)
                    .setDottedLine(true)
            )
        }

        // Remaining portion
        val remainingPoints = points.subList(currentIndex.coerceAtLeast(0), points.size)
        if (remainingPoints.size > 1) {
            routePolyline = map.addPolyline(
                PolylineOptions()
                    .addAll(remainingPoints)
                    .color(primaryColor and 0xCCFFFFFF.toInt())
                    .width(12f)
                    .useGradient(true)
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
        routePolyline?.remove()
        routePolyline = null
        traveledPolyline?.remove()
        traveledPolyline = null
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
