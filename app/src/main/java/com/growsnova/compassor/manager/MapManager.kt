package com.growsnova.compassor.manager

import android.content.Context
import android.graphics.Color
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.*
import com.growsnova.compassor.Waypoint
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject

@ActivityScoped
class MapManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var aMap: AMap? = null
    private var multiPointOverlay: MultiPointOverlay? = null
    private var routePolyline: Polyline? = null
    private var onWaypointClickListener: ((Waypoint) -> Unit)? = null

    fun initialize(amap: AMap) {
        this.aMap = amap
        if (multiPointOverlay == null) {
            setupMultiPointOverlay()
        }
    }

    private fun setupMultiPointOverlay() {
        aMap?.let { map ->
            val options = MultiPointOverlayOptions()
            multiPointOverlay = map.addMultiPointOverlay(options)

            map.setOnMultiPointClickListener { _ ->
                // Note: We'd need a way to get the waypoint back from the ID
                // For now, we'll let MainActivity handle it through the callback
                false
            }
        }
    }

    fun setOnWaypointClickListener(listener: (Waypoint) -> Unit) {
        this.onWaypointClickListener = listener
    }

    fun updateWaypoints(waypoints: List<Waypoint>) {
        val items = waypoints.map {
            MultiPointItem(LatLng(it.latitude, it.longitude)).apply {
                title = it.name
                customerId = it.id.toString()
            }
        }
        multiPointOverlay?.setItems(items)
    }

    fun drawRoute(waypoints: List<Waypoint>, color: Int = Color.BLUE) {
        aMap?.let { map ->
            routePolyline?.remove()
            if (waypoints.size >= 2) {
                val points = waypoints.map { LatLng(it.latitude, it.longitude) }
                routePolyline = map.addPolyline(PolylineOptions()
                    .addAll(points)
                    .color(color)
                    .width(10f))
            }
        }
    }

    fun clearRoute() {
        routePolyline?.remove()
        routePolyline = null
    }

    fun moveCamera(latLng: LatLng, zoom: Float = 15f) {
        aMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoom))
    }

    fun onDestroy() {
        aMap = null
        multiPointOverlay = null
        routePolyline = null
    }
}
