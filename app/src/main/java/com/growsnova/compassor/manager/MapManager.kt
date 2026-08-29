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
    @ActivityContext private val context: Context?
) {
    private var aMap: AMap? = null
    private val waypointMarkers = mutableMapOf<Long, Marker>()
    private var targetMarker: Marker? = null
    private var myLocationMarker: Marker? = null
    private var targetMarkerClickListener: (() -> Unit)? = null
    private var myLocationClickListener: (() -> Unit)? = null
    private var poiClickListener: ((Poi) -> Unit)? = null
    private var completedPolyline: Polyline? = null
    private var remainingPolyline: Polyline? = null
    private var guidancePolyline: Polyline? = null
    private var multiPointOverlay: MultiPointOverlay? = null
    private var locationListener: com.amap.api.maps.LocationSource.OnLocationChangedListener? = null
    private var isFirstLocation = true
    private var lastLatLng: LatLng? = null
    private var lastAzimuth: Float? = null
    private var topPaddingPx: Int = 0
    private var bottomPaddingPx: Int = 0

    fun initialize(map: AMap) {
        this.aMap = map
        setupMapSettings()
        setupLocationSource()
    }

    fun setOnMyLocationClickListener(listener: () -> Unit) {
        this.myLocationClickListener = listener
    }

    fun setOnPoiClickListener(listener: (Poi) -> Unit) {
        this.poiClickListener = listener
        aMap?.setOnPOIClickListener { poi ->
            poiClickListener?.invoke(poi)
        }
    }

    fun applyMapStyle(isNightMode: Boolean) {
        aMap?.mapType = if (isNightMode) AMap.MAP_TYPE_NIGHT else AMap.MAP_TYPE_NORMAL
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

        updateMyLocationMarker(latLng, currentAzimuth)

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
        updateMyLocationMarker(latLng, azimuth)
    }

    private fun updateMyLocationMarker(latLng: LatLng, azimuth: Float?) {
        val map = aMap ?: return
        if (myLocationMarker == null) {
            val descriptor = getNavArrowBitmapDescriptor()
            myLocationMarker = map.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .anchor(0.5f, 0.5f)
                    .setFlat(true)
                    .icon(descriptor)
            )
        } else {
            myLocationMarker?.position = latLng
        }
        azimuth?.let {
            myLocationMarker?.rotateAngle = 360f - it
        }
    }

    private fun getNavArrowBitmapDescriptor(): BitmapDescriptor {
        val ctx = context ?: return BitmapDescriptorFactory.fromResource(R.drawable.ic_nav_arrow)
        val drawable = androidx.core.content.ContextCompat.getDrawable(ctx, R.drawable.ic_nav_arrow)
        if (drawable != null) {
            val density = ctx.resources.displayMetrics.density
            val width = (36 * density).toInt().coerceAtLeast(1)
            val height = (36 * density).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            return BitmapDescriptorFactory.fromBitmap(bitmap)
        }
        return BitmapDescriptorFactory.fromResource(R.drawable.ic_nav_arrow)
    }

    private fun setupMapSettings() {
        aMap?.apply {
            uiSettings.isZoomControlsEnabled = true
            uiSettings.isCompassEnabled = true
            uiSettings.isMyLocationButtonEnabled = true
        }
        val isNight = context?.resources?.configuration?.let {
            (it.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        } ?: false
        applyMapStyle(isNight)
    }

    fun updatePadding(left: Int = 0, top: Int = 0, right: Int = 0, bottom: Int = 0) {
        this.topPaddingPx = top
        this.bottomPaddingPx = bottom
        val width = context?.resources?.displayMetrics?.widthPixels ?: 1080
        val height = context?.resources?.displayMetrics?.heightPixels ?: 2400
        val centerX = width / 2
        val centerY = (height - top - bottom) / 2 + top
        aMap?.setPointToCenter(centerX, centerY)
    }

    fun updateTrackingCamera(
        latLng: LatLng,
        azimuth: Float? = null,
        targetLocation: LatLng? = null,
        targetLocations: List<LatLng>? = null
    ) {
        val map = aMap ?: return
        val minZoom = map.minZoomLevel
        val maxZoom = map.maxZoomLevel
        val currentAzimuth = azimuth ?: lastAzimuth ?: 0f

        val targets = mutableListOf<LatLng>()
        targetLocation?.let { targets.add(it) }
        targetLocations?.let { targets.addAll(it) }

        val zoomLevel = if (targets.isNotEmpty()) {
            calculateTrackingZoomLevel(latLng, currentAzimuth, targets, minZoom, maxZoom)
        } else {
            maxZoom
        }

        val cameraPosition = CameraPosition.Builder()
            .target(latLng)
            .zoom(zoomLevel)
            .tilt(60f)
            .bearing(currentAzimuth)
            .build()
        map.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
    }

    fun calculateTrackingZoomLevel(
        userLatLng: LatLng,
        azimuth: Float,
        targetLocations: List<LatLng>,
        minZoom: Float = 3f,
        maxZoom: Float = 19f,
        widthPixels: Int = context?.resources?.displayMetrics?.widthPixels ?: 1080,
        heightPixels: Int = context?.resources?.displayMetrics?.heightPixels ?: 2400,
        density: Float = context?.resources?.displayMetrics?.density ?: 2.75f
    ): Float {
        if (targetLocations.isEmpty()) return maxZoom

        val centerX = widthPixels / 2.0
        val centerY = if (topPaddingPx > 0 || bottomPaddingPx > 0) {
            (heightPixels - topPaddingPx - bottomPaddingPx) / 2.0 + topPaddingPx
        } else {
            heightPixels / 2.0
        }

        val sidePaddingPx = (16 * density).toInt()
        val marginPx = (40 * density).toInt()

        val rLeft = centerX - sidePaddingPx - marginPx
        val rRight = widthPixels - sidePaddingPx - centerX - marginPx
        val rx = Math.max(50.0, Math.min(rLeft, rRight))

        val rTop = centerY - topPaddingPx - marginPx
        val rBottom = heightPixels - bottomPaddingPx - centerY - marginPx

        var minRequiredZoom = maxZoom

        val tiltFactor = Math.cos(Math.toRadians(60.0))

        for (target in targetLocations) {
            val distAndBearing = calculateDistanceAndBearing(
                userLatLng.latitude, userLatLng.longitude,
                target.latitude, target.longitude
            )
            val distance = distAndBearing[0]
            val bearing = distAndBearing[1]

            if (distance < 10f) continue

            val thetaDeg = (bearing - azimuth + 360f) % 360f
            val thetaRad = Math.toRadians(thetaDeg.toDouble())

            val cosTheta = Math.cos(thetaRad)
            val sinTheta = Math.sin(thetaRad)

            val ry = Math.max(50.0, if (cosTheta >= 0) rTop else rBottom)

            val reqH = Math.abs(distance * sinTheta) / rx
            val reqV = Math.abs(distance * cosTheta) * tiltFactor / ry
            val reqMetersPerPixel = Math.max(reqH, reqV)

            if (reqMetersPerPixel <= 0) continue

            val m0 = 156543.03392 * Math.cos(Math.toRadians(userLatLng.latitude))
            val z = (Math.log(m0 / reqMetersPerPixel) / Math.log(2.0)).toFloat()

            if (z < minRequiredZoom) {
                minRequiredZoom = z
            }
        }

        return minRequiredZoom.coerceIn(minZoom, maxZoom)
    }

    private fun calculateDistanceAndBearing(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): FloatArray {
        val r = 6371000.0
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaPhi = Math.toRadians(lat2 - lat1)
        val deltaLambda = Math.toRadians(lon2 - lon1)

        val a = Math.sin(deltaPhi / 2.0) * Math.sin(deltaPhi / 2.0) +
                Math.cos(phi1) * Math.cos(phi2) *
                Math.sin(deltaLambda / 2.0) * Math.sin(deltaLambda / 2.0)
        val c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a))
        val distance = (r * c).toFloat()

        val y = Math.sin(deltaLambda) * Math.cos(phi2)
        val x = Math.cos(phi1) * Math.sin(phi2) -
                Math.sin(phi1) * Math.cos(phi2) * Math.cos(deltaLambda)
        val bearing = ((Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0).toFloat()

        return floatArrayOf(distance, bearing)
    }

    fun restoreDefaultView(latLng: LatLng? = null) {
        val map = aMap ?: return
        val target = latLng ?: lastLatLng ?: map.cameraPosition.target ?: return
        val cameraPosition = CameraPosition.Builder()
            .target(target)
            .zoom(16f)
            .tilt(0f)
            .bearing(0f)
            .build()
        map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
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
            if (marker == myLocationMarker) {
                myLocationClickListener?.invoke()
            } else if (marker == targetMarker) {
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
        isTrackingMode: Boolean = false,
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
        if (!isTrackingMode) {
            zoomToFitStartAndTargets(startLocation ?: lastLatLng, listOf(latLng))
        }
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
        userLocation: LatLng? = null,
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
        val startPt = navStartLocation ?: userLocation ?: waypointsLatLng.firstOrNull() ?: return

        // 灰线 (已走过的路径): 导航起点 → 已到达的航点 → 用户实时位置
        val completed = mutableListOf<LatLng>()
        completed.add(startPt)
        if (currentIndex > 0) {
            completed.addAll(waypointsLatLng.take(currentIndex.coerceAtMost(waypoints.size)))
        }
        userLocation?.let { completed.add(it) }

        if (completed.size >= 2) {
            completedPolyline = map.addPolyline(
                PolylineOptions()
                    .addAll(completed)
                    .color(traveledColor)
                    .width(10f)
            )
        }

        // 蓝线 (剩余路径): 用户实时位置 → 当前及后续航点
        val remaining = mutableListOf<LatLng>()
        if (userLocation != null) {
            remaining.add(userLocation)
        } else if (currentIndex <= 0) {
            remaining.add(startPt)
        } else {
            val prevIndex = (currentIndex - 1).coerceAtLeast(0)
            if (prevIndex < waypointsLatLng.size) {
                remaining.add(waypointsLatLng[prevIndex])
            }
        }
        remaining.addAll(waypointsLatLng.drop(currentIndex.coerceAtLeast(0)))

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

    private var guidanceAnimator: android.animation.ValueAnimator? = null
    private var lastGuidanceColor: Int? = null
    private val guidanceTextureFrames = mutableListOf<BitmapDescriptor>()

    fun updateGuidanceLine(myLoc: LatLng, targetLoc: LatLng, color: Int) {
        val map = aMap ?: return
        val density = context?.resources?.displayMetrics?.density ?: 2.75f

        if (lastGuidanceColor != color || guidanceTextureFrames.isEmpty()) {
            prepareGuidanceTextures(color, density)
        }

        if (guidancePolyline == null) {
            val initialFrame = guidanceTextureFrames.firstOrNull() ?: return
            guidancePolyline = map.addPolyline(
                PolylineOptions()
                    .add(myLoc, targetLoc)
                    .width(4f * density)
                    .setCustomTexture(initialFrame)
                    .setUseTexture(true)
            )
            startGuidanceAnimation()
        } else {
            val pts = java.util.ArrayList<LatLng>()
            pts.add(myLoc)
            pts.add(targetLoc)
            guidancePolyline?.points = pts
        }
    }

    private fun prepareGuidanceTextures(color: Int, density: Float) {
        guidanceTextureFrames.clear()
        lastGuidanceColor = color
        val numFrames = 8
        val patternWidth = (12 * density).toInt().coerceAtLeast(12)
        val patternHeight = (24 * density).toInt().coerceAtLeast(24)
        val dashHeight = patternHeight / 2f
        val marginX = patternWidth * 0.3f
        val cornerRadius = dashHeight * 0.2f

        for (frame in 0 until numFrames) {
            val offset = ((numFrames - frame) * patternHeight) / numFrames
            val bitmap = Bitmap.createBitmap(patternWidth, patternHeight, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)

            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = android.graphics.Paint.Style.FILL
            }

            for (i in -1..2) {
                val startY = i * patternHeight - offset
                val rect = android.graphics.RectF(
                    marginX,
                    startY.toFloat(),
                    patternWidth - marginX,
                    startY + dashHeight
                )
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
            }

            guidanceTextureFrames.add(BitmapDescriptorFactory.fromBitmap(bitmap))
            bitmap.recycle()
        }
    }

    private fun startGuidanceAnimation() {
        if (guidanceAnimator?.isRunning == true) return
        guidanceAnimator?.cancel()
        guidanceAnimator = android.animation.ValueAnimator.ofInt(0, guidanceTextureFrames.size - 1).apply {
            duration = 800
            repeatCount = android.animation.ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener { animator ->
                val index = animator.animatedValue as Int
                if (index in guidanceTextureFrames.indices) {
                    guidancePolyline?.setCustomTextureList(listOf(guidanceTextureFrames[index]))
                }
            }
            start()
        }
    }

    private fun stopGuidanceAnimation() {
        guidanceAnimator?.cancel()
        guidanceAnimator = null
        guidanceTextureFrames.clear()
        lastGuidanceColor = null
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
        stopGuidanceAnimation()
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
