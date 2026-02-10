package com.growsnova.compassor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.amap.api.location.AMapLocationClient
import com.amap.api.maps.AMap
import com.amap.api.maps.LocationSource
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.model.*
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.core.PoiItem
import com.amap.api.services.poisearch.PoiResult
import com.amap.api.services.poisearch.PoiSearch
import com.google.android.material.navigation.NavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    // 视图组件
    private lateinit var mapView: MapView
    private lateinit var aMap: AMap
    private lateinit var radarView: RadarCompassView
    private lateinit var simpleCompassView: SimpleCompassView
    private lateinit var radarContent: android.view.View
    private var isRadarFlipped = false
    private lateinit var drawerLayout: androidx.drawerlayout.widget.DrawerLayout
    private lateinit var navigationView: com.google.android.material.navigation.NavigationView
    private lateinit var toolbar: androidx.appcompat.widget.Toolbar

    // 定位组件
    private var locationManager: LocationManager? = null
    private var isRequestingLocationUpdates = false
    private var myCurrentLatLng: LatLng? = null
    private var isFirstLocation = true
    private val systemLocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            handleLocationUpdate(location)
        }

        @Suppress("DEPRECATION")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

        override fun onProviderEnabled(provider: String) {}

        override fun onProviderDisabled(provider: String) {}
    }
    private var mapLocationListener: LocationSource.OnLocationChangedListener? = null

    // POI搜索
    @Suppress("DEPRECATION")
    private var poiSearch: PoiSearch? = null
    private var geocodeSearch: com.amap.api.services.geocoder.GeocodeSearch? = null
    private var targetLatLng: LatLng? = null
    private var targetMarker: Marker? = null

    // 收藏地点和路线
    private val waypoints = mutableListOf<Waypoint>()
    private val waypointMarkers = mutableListOf<Marker>()
    private val routes = mutableListOf<Route>()
    private var routePolyline: Polyline? = null
    private var traveledPolyline: Polyline? = null
    private var guidancePolyline: Polyline? = null
    private var currentRoute: Route? = null
    private var currentWaypointIndex: Int = -1
    private val db by lazy { AppDatabase.getDatabase(this) }

    // Navigation UI
    private lateinit var navigationStatusCard: com.google.android.material.card.MaterialCardView
    private lateinit var navTargetText: android.widget.TextView
    private lateinit var navDistanceText: android.widget.TextView
    private lateinit var stopNavButton: com.google.android.material.button.MaterialButton
    private lateinit var skipNavButton: com.google.android.material.button.MaterialButton
    private lateinit var prevNavButton: com.google.android.material.button.MaterialButton

    private val createRouteLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.let { handleCreateRouteResult(it) }
        }
    }

    private val editRouteLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.let { handleEditRouteResult(it) }
        }
    }

    private val skinPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importSkinFromFile(it) }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private const val PREFS_NAME = "CompassorPrefs"
        private const val PREF_SKIN_NAME = "SkinName"
        private const val PREF_THEME_MODE = "ThemeMode"
        private const val PREF_NAV_ROUTE = "NavRoute"
        private const val PREF_NAV_INDEX = "NavIndex"
        private const val PREF_NAV_TARGET_LAT = "NavTargetLat"
        private const val PREF_NAV_TARGET_LNG = "NavTargetLng"
        private const val PREF_NAV_TARGET_NAME = "NavTargetName"
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Load theme preference before super.onCreate
        loadThemePreference()
        
        super.onCreate(savedInstanceState)

        // Update AMap privacy policies first
        AMapLocationClient.updatePrivacyShow(this, true, true)
        AMapLocationClient.updatePrivacyAgree(this, true)

        setContentView(R.layout.activity_main)

        // 初始化视图
        initViews(savedInstanceState)

        // 加载数据
        loadDataFromDb()

        // 加载皮肤
        loadSkinPreference()

        // 请求权限
        checkAndRequestPermissions()
        
        // 恢复导航状态
        resumeNavigationState()

        // 检查是否需要开始导航
        handleNavigationIntent()
    }
    
    private fun saveNavigationState() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        if (currentRoute != null) {
            val gson = com.google.gson.Gson()
            editor.putString(PREF_NAV_ROUTE, gson.toJson(currentRoute))
            editor.putInt(PREF_NAV_INDEX, currentWaypointIndex)
        } else {
            editor.remove(PREF_NAV_ROUTE)
            editor.remove(PREF_NAV_INDEX)
        }
        
        if (targetLatLng != null && currentRoute == null) {
            editor.putLong(PREF_NAV_TARGET_LAT, java.lang.Double.doubleToRawLongBits(targetLatLng!!.latitude))
            editor.putLong(PREF_NAV_TARGET_LNG, java.lang.Double.doubleToRawLongBits(targetLatLng!!.longitude))
            editor.putString(PREF_NAV_TARGET_NAME, targetMarker?.title)
        } else {
            editor.remove(PREF_NAV_TARGET_LAT)
            editor.remove(PREF_NAV_TARGET_LNG)
            editor.remove(PREF_NAV_TARGET_NAME)
        }
        
        editor.apply()
    }

    private fun resumeNavigationState() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val routeJson = prefs.getString(PREF_NAV_ROUTE, null)
        
        if (routeJson != null) {
            val gson = com.google.gson.Gson()
            val route = gson.fromJson(routeJson, Route::class.java)
            val index = prefs.getInt(PREF_NAV_INDEX, 0)
            
            // Wait for map to be ready
            mapView.post {
                currentRoute = route
                currentWaypointIndex = index
                if (index < route.waypoints.size) {
                    val waypoint = route.waypoints[index]
                    setTargetLocation(LatLng(waypoint.latitude, waypoint.longitude), waypoint.name)
                    drawRouteOnMap(route.waypoints)
                }
            }
        } else {
            val latBits = prefs.getLong(PREF_NAV_TARGET_LAT, 0)
            val lngBits = prefs.getLong(PREF_NAV_TARGET_LNG, 0)
            if (latBits != 0L && lngBits != 0L) {
                val lat = java.lang.Double.longBitsToDouble(latBits)
                val lng = java.lang.Double.longBitsToDouble(lngBits)
                val name = prefs.getString(PREF_NAV_TARGET_NAME, "目的地") ?: "目的地"
                mapView.post {
                    setTargetLocation(LatLng(lat, lng), name)
                }
            }
        }
    }

    private fun handleNavigationIntent() {
        val route = intent.getSerializableExtraCompat<Route>("start_navigation_route")
        route?.let {
            // 延迟启动导航，等待地图和数据加载完成
            mapView.postDelayed({
                if (it.waypoints.isNotEmpty()) {
                    startRouteNavigation(it)
                }
            }, 1000)
        }
    }

    private fun initViews(savedInstanceState: Bundle?) {
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Set up toolbar with proper navigation
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(androidx.appcompat.R.drawable.abc_ic_ab_back_material)

        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.nav_view)

        val toggle = androidx.appcompat.app.ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        navigationView.setNavigationItemSelectedListener(this)

        geocodeSearch = com.amap.api.services.geocoder.GeocodeSearch(this)

        mapView = findViewById(R.id.mapView)
        radarView = findViewById(R.id.radarView)
        simpleCompassView = findViewById(R.id.simpleCompassView)
        radarContent = findViewById(R.id.radarContent)
        
        radarContent.setOnClickListener {
            flipRadarCard()
        }

        // Init Navigation UI
        navigationStatusCard = findViewById(R.id.navigationStatusCard)
        navTargetText = findViewById(R.id.navTargetText)
        navDistanceText = findViewById(R.id.navDistanceText)
        stopNavButton = findViewById(R.id.stopNavButton)
        skipNavButton = findViewById(R.id.skipNavButton)
        prevNavButton = findViewById(R.id.prevNavButton)
        
        stopNavButton.applyTouchScale()
        stopNavButton.setOnClickListener {
            stopNavigation()
        }

        skipNavButton.applyTouchScale()
        skipNavButton.setOnClickListener {
            skipNextWaypoint()
        }

        prevNavButton.applyTouchScale()
        prevNavButton.setOnClickListener {
            goToPreviousWaypoint()
        }

        // 初始化地图
        mapView.onCreate(savedInstanceState)
        aMap = mapView.map
        aMap.setLocationSource(object : LocationSource {
            override fun activate(listener: LocationSource.OnLocationChangedListener) {
                mapLocationListener = listener
            }

            override fun deactivate() {
                mapLocationListener = null
            }
        })

        // 地图UI设置
        aMap.uiSettings.isZoomControlsEnabled = true
        aMap.uiSettings.isCompassEnabled = true
        aMap.uiSettings.isMyLocationButtonEnabled = true

        // 设置地图类型
        aMap.mapType = AMap.MAP_TYPE_NORMAL

        // 设置标记点击事件
        aMap.setOnMarkerClickListener { marker ->
            val waypoint = waypoints.find { it.latitude == marker.position.latitude && it.longitude == marker.position.longitude }
            if (waypoint != null) {
                showWaypointOptionsDialog(waypoint)
            }
            true
        }

        // 设置地图长按事件
        aMap.setOnMapLongClickListener { latLng ->
            showMapLongClickOptionsDialog(latLng)
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsNeeded = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsNeeded.toTypedArray(),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            setupLocation()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    setupLocation()
                } else {
                    DialogUtils.showErrorToast(this, getString(R.string.location_permission_denied))
                }
            }
        }
    }

    private fun setupLocation() {
        if (locationManager == null) {
            locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        }

        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true)
        val primaryColor = typedValue.data
        
        aMap.myLocationStyle = MyLocationStyle().apply {
            myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE_NO_CENTER)
            strokeColor(primaryColor)
            radiusFillColor(primaryColor and 0x30FFFFFF)
        }
        aMap.isMyLocationEnabled = true
        
        // Initialize radar skin from theme
        val skin = RadarSkin.createFromTheme(this)
        radarView.setSkin(skin)
        simpleCompassView.setSkin(skin)

        startLocationUpdates()
    }

    private fun startLocationUpdates() {
        if (isRequestingLocationUpdates) {
            return
        }

        val manager = locationManager ?: return
        val hasFinePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarsePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFinePermission && !hasCoarsePermission) {
            return
        }

        try {
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            var requested = false
            providers.forEach { provider ->
                if (manager.isProviderEnabled(provider)) {
                    manager.requestLocationUpdates(provider, 3000L, 5f, systemLocationListener, Looper.getMainLooper())
                    manager.getLastKnownLocation(provider)?.let { handleLocationUpdate(it) }
                    requested = true
                }
            }

            if (!requested) {
                DialogUtils.showErrorToast(this, getString(R.string.location_unavailable))
            } else {
                isRequestingLocationUpdates = true
            }
        } catch (ex: SecurityException) {
            Log.e(TAG, "Failed to request location updates", ex)
        }
    }

    private fun stopLocationUpdates() {
        if (!isRequestingLocationUpdates) {
            return
        }
        try {
            locationManager?.removeUpdates(systemLocationListener)
        } catch (ex: SecurityException) {
            Log.e(TAG, "Failed to remove location updates", ex)
        } finally {
            isRequestingLocationUpdates = false
        }
    }

    private fun skipNextWaypoint() {
        val route = currentRoute ?: return
        if (currentWaypointIndex < route.waypoints.size - 1) {
            currentWaypointIndex++
            val nextWaypoint = route.waypoints[currentWaypointIndex]
            setTargetLocation(LatLng(nextWaypoint.latitude, nextWaypoint.longitude), nextWaypoint.name)
            DialogUtils.showToast(this, getString(R.string.next_waypoint_notification, nextWaypoint.name))
            saveNavigationState()
        } else if (route.isLooping) {
            currentWaypointIndex = 0
            val firstWaypoint = route.waypoints[0]
            setTargetLocation(LatLng(firstWaypoint.latitude, firstWaypoint.longitude), firstWaypoint.name)
            DialogUtils.showToast(this, "已重置到起点")
            saveNavigationState()
        } else {
            DialogUtils.showToast(this, getString(R.string.route_completed))
            stopNavigation()
        }
    }

    private fun goToPreviousWaypoint() {
        val route = currentRoute ?: return
        if (currentWaypointIndex > 0) {
            currentWaypointIndex--
            val prevWaypoint = route.waypoints[currentWaypointIndex]
            setTargetLocation(LatLng(prevWaypoint.latitude, prevWaypoint.longitude), prevWaypoint.name)
            DialogUtils.showToast(this, "已返回到上一个点: ${prevWaypoint.name}")
            saveNavigationState()
        } else if (route.isLooping) {
            currentWaypointIndex = route.waypoints.size - 1
            val lastWaypoint = route.waypoints[currentWaypointIndex]
            setTargetLocation(LatLng(lastWaypoint.latitude, lastWaypoint.longitude), lastWaypoint.name)
            DialogUtils.showToast(this, "已返回到最后一个点: ${lastWaypoint.name}")
            saveNavigationState()
        }
    }

    private fun stopNavigation() {
        currentRoute = null
        currentWaypointIndex = -1
        targetLatLng = null
        targetMarker?.remove()
        targetMarker = null
        routePolyline?.remove()
        routePolyline = null
        traveledPolyline?.remove()
        traveledPolyline = null
        guidancePolyline?.remove()
        guidancePolyline = null
        
        navigationStatusCard.animate().alpha(0f).setDuration(300).withEndAction {
            navigationStatusCard.visibility = android.view.View.GONE
        }.start()
        
        // Clear keep screen on
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        saveNavigationState()
    }

    private fun updateGuidanceLine(myLoc: LatLng, targetLoc: LatLng) {
        guidancePolyline?.remove()

        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true)
        val primaryColor = typedValue.data

        val options = PolylineOptions()
            .add(myLoc, targetLoc)
            .color(primaryColor and 0x80FFFFFF.toInt()) // More transparent than main route
            .width(6f)
            .setDottedLine(true) // 虚线
            .setDottedLineType(PolylineOptions.DOTTEDLINE_TYPE_SQUARE)

        guidancePolyline = aMap.addPolyline(options)
    }

    private fun updateNavigationStatus(targetName: String, distanceMeters: Float) {
        if (navigationStatusCard.visibility != android.view.View.VISIBLE) {
            navigationStatusCard.alpha = 0f
            navigationStatusCard.visibility = android.view.View.VISIBLE
            navigationStatusCard.animate().alpha(1f).setDuration(300).start()
        }
        navTargetText.text = getString(R.string.nav_target_format, targetName)
        
        // Only show skip/prev buttons if we are in a multi-waypoint route
        if (currentRoute != null) {
            skipNavButton.visibility = android.view.View.VISIBLE
            prevNavButton.visibility = android.view.View.VISIBLE
            // Disable buttons if at the very beginning/end and not looping
            val canSkip = currentRoute!!.isLooping || currentWaypointIndex < currentRoute!!.waypoints.size - 1
            val canPrev = currentRoute!!.isLooping || currentWaypointIndex > 0
            skipNavButton.isEnabled = canSkip
            prevNavButton.isEnabled = canPrev
            skipNavButton.alpha = if (canSkip) 1.0f else 0.5f
            prevNavButton.alpha = if (canPrev) 1.0f else 0.5f
        } else {
            skipNavButton.visibility = android.view.View.GONE
            prevNavButton.visibility = android.view.View.GONE
        }
        
        val distanceStr = if (distanceMeters < 1000) {
            "${distanceMeters.toInt()}m"
        } else {
            "%.1fkm".format(distanceMeters / 1000f)
        }
        navDistanceText.text = getString(R.string.nav_distance_format, distanceStr)
    }

    private fun handleLocationUpdate(location: Location) {
        val (gcjLat, gcjLng) = CoordTransform.wgs84ToGcj02(location.latitude, location.longitude)
        val convertedLocation = Location(location).apply {
            latitude = gcjLat
            longitude = gcjLng
        }
        mapLocationListener?.onLocationChanged(convertedLocation)
        val newLatLng = LatLng(gcjLat, gcjLng)

        // 检查位置变化距离，只有移动超过一定距离才更新
        val distance = FloatArray(1)
        myCurrentLatLng?.let { current ->
            Location.distanceBetween(
                current.latitude, current.longitude,
                newLatLng.latitude, newLatLng.longitude,
                distance
            )
        }

        // 只有距离变化超过5米或首次定位才更新UI
        if (myCurrentLatLng == null || distance[0] > 5f || isFirstLocation) {
            myCurrentLatLng = newLatLng

            if (isFirstLocation) {
                aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(newLatLng, 15f))
            } else {
                aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(newLatLng, 15f))
            }
            isFirstLocation = false
        }

        // 更新雷达视图
        targetLatLng?.let { target ->
            myCurrentLatLng?.let { current ->
                val distArray = FloatArray(1)
                Location.distanceBetween(current.latitude, current.longitude, target.latitude, target.longitude, distArray)
                val dist = distArray[0]
                
                updateRadarViews(current, target)
                updateGuidanceLine(current, target)
                
                // Update navigation status card if not in route (route has its own update below)
                if (currentRoute == null) {
                    updateNavigationStatus(targetMarker?.title ?: "目的地", dist)
                }
            }
        }

        // 检查是否在路线导航中
        currentRoute?.let { route ->
            if (currentWaypointIndex != -1) {
                val currentTargetWaypoint = route.waypoints[currentWaypointIndex]
                val distanceToWaypoint = FloatArray(1)
                Location.distanceBetween(
                    newLatLng.latitude, newLatLng.longitude,
                    currentTargetWaypoint.latitude, currentTargetWaypoint.longitude,
                    distanceToWaypoint
                )
                
                updateNavigationStatus(currentTargetWaypoint.name, distanceToWaypoint[0])

                if (distanceToWaypoint[0] < 20) { // 20米阈值
                    if (currentWaypointIndex < route.waypoints.size - 1) {
                        currentWaypointIndex++
                        val nextWaypoint = route.waypoints[currentWaypointIndex]
                        setTargetLocation(LatLng(nextWaypoint.latitude, nextWaypoint.longitude), nextWaypoint.name)
                        DialogUtils.showToast(this, getString(R.string.next_waypoint_notification, nextWaypoint.name))
                        saveNavigationState()
                    } else {
                        if (route.isLooping) {
                            currentWaypointIndex = 0
                            val firstWaypoint = route.waypoints[0]
                            setTargetLocation(LatLng(firstWaypoint.latitude, firstWaypoint.longitude), firstWaypoint.name)
                            DialogUtils.showToast(this, getString(R.string.arrival_notification, currentTargetWaypoint.name))
                            saveNavigationState()
                        } else {
                            DialogUtils.showToast(this, getString(R.string.route_completed))
                            stopNavigation()
                        }
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun searchPOI(keyword: String) {
        if (!NetworkUtils.isNetworkAvailable(this)) {
            DialogUtils.showErrorToast(this, getString(R.string.network_unavailable))
            return
        }
        DialogUtils.showToast(this, getString(R.string.searching))

        // 获取当前城市
        val city = myCurrentLatLng?.let {
            // 这里简化处理，实际应该根据坐标获取城市
            ""
        } ?: ""

        val query = PoiSearch.Query(keyword, "", city)
        query.pageSize = 10
        query.pageNum = 1

        poiSearch = PoiSearch(this, query)
        myCurrentLatLng?.let {
            poiSearch?.bound = PoiSearch.SearchBound(LatLonPoint(it.latitude, it.longitude), 10000) // 10km radius
        }
        poiSearch?.setOnPoiSearchListener(object : PoiSearch.OnPoiSearchListener {
            override fun onPoiSearched(result: PoiResult?, errorCode: Int) {
                if (errorCode == 1000) {
                    result?.pois?.let { pois ->
                        if (pois.isNotEmpty()) {
                            showPOIListDialog(pois)
                        } else {
                            DialogUtils.showToast(this@MainActivity, getString(R.string.no_result))
                        }
                    }
                } else {
                    DialogUtils.showErrorToast(this@MainActivity, "搜索失败: $errorCode")
                }
            }

            override fun onPoiItemSearched(poiItem: PoiItem?, errorCode: Int) {}
        })

        poiSearch?.searchPOIAsyn()
    }

    private fun setTargetLocation(latLng: LatLng, title: String) {
        targetLatLng = latLng

        // 移除旧标记
        targetMarker?.remove()

        // 添加新标记
        val markerOptions = MarkerOptions()
            .position(latLng)
            .title(title)
            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))

        targetMarker = aMap.addMarker(markerOptions)
        
        // Hide info window during active navigation to prevent occlusion
        // We only show it if the user just set a target, but hide it once they start moving or if in route
        if (currentRoute == null) {
            targetMarker?.showInfoWindow()
        } else {
            targetMarker?.hideInfoWindow()
        }

        // 移动相机到目标位置
        aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))

        // 更新雷达视图
        myCurrentLatLng?.let { myLoc ->
            updateRadarViews(myLoc, latLng)
        }

        // 清除路线 (if this is a direct target set) or redraw (if in route)
        if (currentRoute == null) {
            routePolyline?.remove()
            routePolyline = null
            traveledPolyline?.remove()
            traveledPolyline = null
        } else {
            drawRouteOnMap(currentRoute!!.waypoints)
        }
        
        // Keep screen on during navigation
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        saveNavigationState()
    }

    private fun showPOIListDialog(pois: List<PoiItem>) {
        val poiNames = pois.map { "${it.title} (${it.distance}m)" }.toTypedArray()
        var selectedPoi: PoiItem? = null

        DialogUtils.showSingleChoiceDialog(
            context = this,
            title = "选择一个地点",
            items = poiNames,
            onItemSelected = { which ->
                selectedPoi = pois[which]
            },
            onPositive = {
                selectedPoi?.let {
                    val latLng = LatLng(it.latLonPoint.latitude, it.latLonPoint.longitude)
                    setTargetLocation(latLng, it.title)
                }
            },
            onNegative = {}
        )
    }

    private fun addWaypoint(latLng: LatLng, name: String) {
        lifecycleScope.launch {
            val distance = FloatArray(1)
            val existingWaypoint = waypoints.find {
                Location.distanceBetween(latLng.latitude, latLng.longitude, it.latitude, it.longitude, distance)
                (it.name.equals(name, ignoreCase = true) || it.name.contains(name, ignoreCase = true) || name.contains(it.name, ignoreCase = true)) && distance[0] < 10
            }

            if (existingWaypoint != null) {
                runOnUiThread {
                    DialogUtils.showConfirmationDialog(
                        context = this@MainActivity,
                        title = "更新收藏地点",
                        message = "附近已存在一个相似的收藏地点 '${existingWaypoint.name}'。您想用新的位置和名称 '$name' 更新它吗？",
                        onPositive = {
                            updateWaypoint(existingWaypoint, name, newLatLng = latLng)
                        }
                    )
                }
            } else {
                val waypoint = Waypoint(
                    name = name,
                    latitude = latLng.latitude,
                    longitude = latLng.longitude
                )
                val newId = db.waypointDao().insert(waypoint)
                val newWaypoint = waypoint.copy(id = newId)
                waypoints.add(newWaypoint)

                runOnUiThread {
                    val marker = aMap.addMarker(
                        MarkerOptions()
                            .position(latLng)
                            .title(name)
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                    )
                    waypointMarkers.add(marker)
                    DialogUtils.showSuccessToast(this@MainActivity, "${getString(R.string.waypoint_saved, name)}")
                }
            }
        }
    }

    private fun showSaveWaypointDialog(latLng: LatLng, waypointToEdit: Waypoint? = null, defaultName: String? = null) {
        val title = if (waypointToEdit == null) getString(R.string.save_location) else getString(R.string.edit_name)
        val initialValue = waypointToEdit?.name ?: defaultName ?: ""
        
        DialogUtils.showInputDialog(
            context = this,
            title = title,
            hint = getString(R.string.waypoint_name_hint),
            initialValue = initialValue,
            onPositive = { waypointName ->
                if (waypointToEdit == null) {
                    addWaypoint(latLng, waypointName)
                } else {
                    updateWaypoint(waypointToEdit, waypointName)
                }
            }
        )
    }

    private fun showMapLongClickOptionsDialog(latLng: LatLng) {
        val options = arrayOf(getString(R.string.save_location), getString(R.string.set_destination))
        
        DialogUtils.showOptionsDialog(
            context = this,
            title = getString(R.string.select_action),
            options = options
        ) { which ->
            when (which) {
                0 -> reverseGeocode(latLng) { name ->
                    showSaveWaypointDialog(latLng, defaultName = name)
                }
                1 -> reverseGeocode(latLng) { name ->
                    setTargetLocation(latLng, name)
                }
            }
        }
    }

    private fun updateWaypoint(waypoint: Waypoint, newName: String, newLatLng: LatLng? = null) {
        lifecycleScope.launch {
            val otherWaypoints = waypoints.filter { it.id != waypoint.id }
            val distance = FloatArray(1)
            val potentialDuplicate = otherWaypoints.find {
                val checkLatLng = newLatLng ?: LatLng(waypoint.latitude, waypoint.longitude)
                Location.distanceBetween(checkLatLng.latitude, checkLatLng.longitude, it.latitude, it.longitude, distance)
                it.name.equals(newName, ignoreCase = true) && distance[0] < 10
            }

            if (potentialDuplicate != null) {
                runOnUiThread {
                    DialogUtils.showErrorToast(this@MainActivity, getString(R.string.duplicate_waypoint_error))
                }
                return@launch
            }

            waypoint.name = newName
            if (newLatLng != null) {
                waypoint.latitude = newLatLng.latitude
                waypoint.longitude = newLatLng.longitude
            }

            db.waypointDao().update(waypoint)

            runOnUiThread {
                val marker = waypointMarkers.find { it.position.latitude == waypoint.latitude && it.position.longitude == waypoint.longitude }
                if (newLatLng != null) {
                    marker?.position = newLatLng
                }
                marker?.title = newName

                routes.forEach { route ->
                    if (route.waypoints.any { it.id == waypoint.id }) {
                        if (currentRoute?.id == route.id || routePolyline?.points?.size == route.waypoints.size) {
                            drawRouteOnMap(route.waypoints)
                        }
                    }
                }
                DialogUtils.showSuccessToast(this@MainActivity, getString(R.string.waypoint_updated))
            }
        }
    }

    private fun deleteWaypointSafely(waypoint: Waypoint) {
        lifecycleScope.launch {
            val routesContainingWaypoint = routes.filter { route -> route.waypoints.any { it.id == waypoint.id } }
            val affectedRoutes = mutableListOf<Route>()
            
            for (route in routesContainingWaypoint) {
                db.routeDao().deleteRouteWaypointCrossRef(RouteWaypointCrossRef(route.id, waypoint.id))
                route.waypoints.removeIf { it.id == waypoint.id }
                if (route.waypoints.size < 2) {
                    db.routeDao().deleteRoute(route)
                    routes.remove(route)
                } else {
                    affectedRoutes.add(route)
                }
            }

            db.waypointDao().delete(waypoint)
            waypoints.remove(waypoint)

            runOnUiThread {
                val markerToRemove = waypointMarkers.find { it.position.latitude == waypoint.latitude && it.position.longitude == waypoint.longitude }
                markerToRemove?.remove()
                waypointMarkers.remove(markerToRemove)
                
                // 如果删除的收藏地点影响了当前导航的路线，更新currentRoute引用并重绘路线
                currentRoute?.let { current ->
                    val updatedRoute = affectedRoutes.find { it.id == current.id }
                    if (updatedRoute != null) {
                        currentRoute = updatedRoute
                        if (updatedRoute.waypoints.size >= 2) {
                            drawRouteOnMap(updatedRoute.waypoints)
                            // 更新当前导航目标
                            if (currentWaypointIndex >= updatedRoute.waypoints.size) {
                                currentWaypointIndex = updatedRoute.waypoints.size - 1
                            }
                            if (currentWaypointIndex >= 0) {
                                val targetWaypoint = updatedRoute.waypoints[currentWaypointIndex]
                                setTargetLocation(LatLng(targetWaypoint.latitude, targetWaypoint.longitude), targetWaypoint.name)
                            }
                        } else {
                            // 路线已被删除或不足两个点
                            currentRoute = null
                            currentWaypointIndex = -1
                            routePolyline?.remove()
                            routePolyline = null
                            targetMarker?.remove()
                            targetMarker = null
                        }
                    }
                }
                
                DialogUtils.showSuccessToast(this@MainActivity, getString(R.string.waypoint_deleted))
            }
        }
    }

    private fun drawRouteOnMap(waypoints: List<Waypoint>) {
        routePolyline?.remove()
        traveledPolyline?.remove()

        if (waypoints.size > 1) {
            val typedValue = android.util.TypedValue()
            theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true)
            val primaryColor = typedValue.data
            
            theme.resolveAttribute(com.google.android.material.R.attr.colorOutline, typedValue, true)
            val traveledColor = typedValue.data

            // Draw traveled portion if in navigation
            if (currentRoute != null && currentWaypointIndex > 0) {
                val traveledPoints = waypoints.subList(0, currentWaypointIndex + 1).map { LatLng(it.latitude, it.longitude) }
                val traveledOptions = PolylineOptions()
                    .addAll(traveledPoints)
                    .color(traveledColor and 0x80FFFFFF.toInt()) // Semi-transparent grey/outline
                    .width(10f)
                    .setDottedLine(true)
                traveledPolyline = aMap.addPolyline(traveledOptions)
            }

            // Draw remaining portion
            val startIndex = if (currentRoute != null) currentWaypointIndex else 0
            val remainingWaypoints = waypoints.subList(startIndex, waypoints.size)
            
            if (remainingWaypoints.size > 1) {
                val polylineOptions = PolylineOptions()
                    .addAll(remainingWaypoints.map { LatLng(it.latitude, it.longitude) })
                    .color(primaryColor and 0xCCFFFFFF.toInt()) // Semi-transparent
                    .width(12f)
                    .setDottedLine(false)
                    .useGradient(true)

                routePolyline = aMap.addPolyline(polylineOptions)
            }
        }
        
        updateWaypointMarkerStyles()
    }

    private fun updateWaypointMarkerStyles() {
        val route = currentRoute ?: return
        
        waypointMarkers.forEach { marker ->
            val waypoint = route.waypoints.find { it.latitude == marker.position.latitude && it.longitude == marker.position.longitude }
            if (waypoint != null) {
                val index = route.waypoints.indexOf(waypoint)
                if (index < currentWaypointIndex) {
                    // Reached waypoint - show greyed out
                    marker.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET))
                    marker.alpha = 0.5f
                } else if (index == currentWaypointIndex) {
                    // Current target waypoint - show red
                    marker.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                    marker.alpha = 1.0f
                } else {
                    // Future waypoint - show azure
                    marker.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                    marker.alpha = 1.0f
                }
            }
        }
    }

    private fun showRouteManagementDialog() {
        val routeNames = routes.map { it.name }.toTypedArray()

        DialogUtils.showListDialog(
            context = this,
            title = getString(R.string.manage_routes),
            items = routeNames,
            onItemSelected = { which ->
                showRouteOptionsDialog(routes[which])
            },
            positiveButtonText = R.string.create_route,
            onPositive = {
                launchCreateRoute()
            }
        )
    }

    private fun launchCreateRoute() {
        val intent = android.content.Intent(this, CreateRouteActivity::class.java).apply {
            putExtra("waypoints_wrapper", WaypointListWrapper(ArrayList(waypoints)))
            myCurrentLatLng?.let { putExtra("current_latlng", it) }
        }
        createRouteLauncher.launch(intent)
    }

    private fun launchEditRoute(route: Route) {
        val intent = android.content.Intent(this, CreateRouteActivity::class.java).apply {
            putExtra("route_to_edit", route)
            putExtra("waypoints_wrapper", WaypointListWrapper(ArrayList(waypoints)))
            myCurrentLatLng?.let { putExtra("current_latlng", it) }
        }
        editRouteLauncher.launch(intent)
    }

    private fun startRouteNavigation(route: Route) {
        currentRoute = route
        currentWaypointIndex = 0
        val firstWaypoint = route.waypoints[0]
        setTargetLocation(LatLng(firstWaypoint.latitude, firstWaypoint.longitude), firstWaypoint.name)
        drawRouteOnMap(route.waypoints)
        DialogUtils.showSuccessToast(this, getString(R.string.navigation_started, route.name))
        saveNavigationState()
    }

    private fun showRouteOptionsDialog(route: Route) {
        val options = arrayOf(
            getString(R.string.start_navigation),
            getString(R.string.edit_route),
            getString(R.string.delete_route),
            getString(R.string.set_destination)
        )
        
        DialogUtils.showOptionsDialog(
            context = this,
            title = route.name,
            options = options
        ) { which ->
            when (which) {
                0 -> startRouteNavigation(route)
                1 -> launchEditRoute(route)
                2 -> deleteRoute(route)
                3 -> {
                    if (route.waypoints.isNotEmpty()) {
                        val waypoint = route.waypoints[0]
                        setTargetLocation(LatLng(waypoint.latitude, waypoint.longitude), waypoint.name)
                    }
                }
            }
        }
    }

    private fun deleteRoute(route: Route) {
        lifecycleScope.launch {
            db.routeDao().deleteRoute(route)
            route.waypoints.forEach { waypoint ->
                db.routeDao().deleteRouteWaypointCrossRef(RouteWaypointCrossRef(route.id, waypoint.id))
            }
            routes.remove(route)

            runOnUiThread {
                if (routePolyline?.points?.map { LatLng(it.latitude, it.longitude) } == route.waypoints.map { LatLng(it.latitude, it.longitude) }) {
                    routePolyline?.remove()
                    routePolyline = null
                }
                DialogUtils.showSuccessToast(this@MainActivity, getString(R.string.route_deleted))
            }
        }
    }

    private fun loadDataFromDb() {
        lifecycleScope.launch {
            val loadedWaypoints = db.waypointDao().getAllWaypoints()
            waypoints.clear()
            waypoints.addAll(loadedWaypoints)

            val routesWithWaypoints = db.routeDao().getRoutesWithWaypoints()
            routes.clear()
            routes.addAll(routesWithWaypoints.map {
                val route = it.route
                route.waypoints.addAll(it.waypoints)
                route
            })

            runOnUiThread {
                waypointMarkers.forEach { it.remove() }
                waypointMarkers.clear()
                waypoints.forEach { waypoint ->
                    val marker = aMap.addMarker(
                        MarkerOptions()
                            .position(LatLng(waypoint.latitude, waypoint.longitude))
                            .title(waypoint.name)
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                    )
                    waypointMarkers.add(marker)
                }
            }
        }
    }
    // 地图生命周期管理
    override fun onResume() {
        super.onResume()
        mapView.onResume()
        startLocationUpdates()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        // If not navigating, stop location updates to save battery
        if (targetLatLng == null) {
            stopLocationUpdates()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
        stopLocationUpdates()
        locationManager = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    private fun reverseGeocode(latLng: LatLng, callback: (String) -> Unit) {
        val query = com.amap.api.services.geocoder.RegeocodeQuery(
            LatLonPoint(latLng.latitude, latLng.longitude),
            200f,
            com.amap.api.services.geocoder.GeocodeSearch.AMAP
        )
        geocodeSearch?.setOnGeocodeSearchListener(object : com.amap.api.services.geocoder.GeocodeSearch.OnGeocodeSearchListener {
            override fun onRegeocodeSearched(result: com.amap.api.services.geocoder.RegeocodeResult?, rCode: Int) {
                if (rCode == 1000) {
                    val address = result?.regeocodeAddress?.formatAddress
                    if (!address.isNullOrEmpty()) {
                        callback(address)
                    } else {
                        callback("自定义位置")
                    }
                } else {
                    callback("自定义位置")
                }
            }

            override fun onGeocodeSearched(result: com.amap.api.services.geocoder.GeocodeResult?, rCode: Int) {}
        })
        geocodeSearch?.getFromLocationAsyn(query)
    }

    override fun onNavigationItemSelected(item: android.view.MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_save_location -> {
                myCurrentLatLng?.let { latLng ->
                    reverseGeocode(latLng) { name ->
                        showSaveWaypointDialog(latLng, defaultName = name)
                    }
                } ?: run {
                    DialogUtils.showErrorToast(this, getString(R.string.location_unavailable))
                }
            }
            R.id.nav_manage_waypoints -> {
                showWaypointManagementDialog()
            }
            R.id.nav_manage_routes -> {
                showRouteManagementDialog()
            }
            R.id.nav_change_skin -> {
                showSkinSelectionDialog()
            }
            R.id.nav_settings -> {
                showSettingsDialog()
            }
        }
        drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
        return true
    }

    private fun showSearchDialog() {
        lifecycleScope.launch {
            val searchHistories = db.searchHistoryDao().getRecentSearches()
            
            runOnUiThread {
                val view = layoutInflater.inflate(R.layout.dialog_search_with_history, null)
                val editText = view.findViewById<EditText>(R.id.searchEditText)
                val historyRecyclerView = view.findViewById<RecyclerView>(R.id.historyRecyclerView)
                val clearHistoryButton = view.findViewById<Button>(R.id.clearHistoryButton)
                
                clearHistoryButton.applyTouchScale()
                
                historyRecyclerView.layoutManager = LinearLayoutManager(this@MainActivity)
                
                val historyList = searchHistories.toMutableList()
                lateinit var historyAdapter: SearchHistoryAdapter
                
                val performSearchAction = { keyword: String, dialog: androidx.appcompat.app.AlertDialog ->
                    if (keyword.isNotEmpty()) {
                        searchPOI(keyword)
                        lifecycleScope.launch {
                            db.searchHistoryDao().insert(SearchHistory(query = keyword))
                        }
                        dialog.dismiss()
                    }
                }

                val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle(getString(R.string.search_location))
                    .setView(view)
                    .setPositiveButton(getString(R.string.search), null)
                    .setNegativeButton(R.string.cancel, null)
                    .create()

                historyAdapter = SearchHistoryAdapter(
                    historyList,
                    onItemClick = { history ->
                        editText.setText(history.query)
                        performSearchAction(history.query, dialog)
                    },
                    onDeleteClick = { history ->
                        lifecycleScope.launch {
                            db.searchHistoryDao().delete(history.id)
                            historyList.remove(history)
                            runOnUiThread {
                                historyAdapter.notifyDataSetChanged()
                                if (historyList.isEmpty()) {
                                    historyRecyclerView.visibility = android.view.View.GONE
                                    clearHistoryButton.visibility = android.view.View.GONE
                                }
                            }
                        }
                    }
                )
                historyRecyclerView.adapter = historyAdapter

                // Add incremental search (optional but consistent)
                var searchJob: kotlinx.coroutines.Job? = null
                editText.addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        searchJob?.cancel()
                        val query = s?.toString()?.trim() ?: ""
                        if (query.length >= 3) { // Slightly longer threshold for dialog
                            searchJob = lifecycleScope.launch {
                                kotlinx.coroutines.delay(800)
                                runOnUiThread {
                                    // We don't dismiss the dialog for incremental search in the dialog itself
                                    // but we could refresh a local result list if we had one.
                                    // For now, let's keep it manual in the dialog to avoid surprise dismissals.
                                }
                            }
                        }
                    }
                    override fun afterTextChanged(s: android.text.Editable?) {}
                })

                if (historyList.isEmpty()) {
                    historyRecyclerView.visibility = android.view.View.GONE
                    clearHistoryButton.visibility = android.view.View.GONE
                } else {
                    historyRecyclerView.visibility = android.view.View.VISIBLE
                    clearHistoryButton.visibility = android.view.View.VISIBLE
                }
                
                clearHistoryButton.setOnClickListener {
                    lifecycleScope.launch {
                        db.searchHistoryDao().clearAll()
                        historyList.clear()
                        runOnUiThread {
                            historyAdapter.notifyDataSetChanged()
                            historyRecyclerView.visibility = android.view.View.GONE
                            clearHistoryButton.visibility = android.view.View.GONE
                        }
                    }
                }

                // Request focus and show keyboard
                editText.requestFocus()
                dialog.setOnShowListener {
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
                }

                dialog.show()

                // Override positive button to prevent auto-dismiss if empty
                dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    performSearchAction(editText.text.toString().trim(), dialog)
                }

                editText.setOnEditorActionListener { _, actionId, event ->
                    if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                        (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                        performSearchAction(editText.text.toString().trim(), dialog)
                        true
                    } else {
                        false
                    }
                }
            }
        }
    }

    private fun showWaypointManagementDialog() {
        val waypointDisplayInfo = waypoints.map { waypoint ->
            val routesContainingWaypoint = routes.filter { it.waypoints.contains(waypoint) }
            val routeNames = if (routesContainingWaypoint.isNotEmpty()) {
                getString(R.string.in_routes, routesContainingWaypoint.joinToString { it.name })
            } else {
                ""
            }
            waypoint.name + routeNames
        }.toTypedArray()

        DialogUtils.showOptionsDialog(
            context = this,
            title = getString(R.string.manage_waypoints_title),
            options = waypointDisplayInfo
        ) { which ->
            showWaypointOptionsDialog(waypoints[which])
        }
    }

    private fun showWaypointOptionsDialog(waypoint: Waypoint) {
        val options = arrayOf(
            getString(R.string.set_destination),
            getString(R.string.edit_name),
            getString(R.string.delete)
        )
        
        DialogUtils.showOptionsDialog(
            context = this,
            title = waypoint.name,
            options = options
        ) { which ->
            when (which) {
                0 -> setTargetLocation(LatLng(waypoint.latitude, waypoint.longitude), waypoint.name)
                1 -> showSaveWaypointDialog(LatLng(waypoint.latitude, waypoint.longitude), waypoint)
                2 -> {
                    val routesContainingWaypoint = routes.filter { it.waypoints.contains(waypoint) }
                    if (routesContainingWaypoint.isNotEmpty()) {
                        val routeNames = routesContainingWaypoint.joinToString { it.name }
                        DialogUtils.showConfirmationDialog(
                            context = this,
                            title = getString(R.string.confirm_delete),
                            message = getString(R.string.confirm_delete_waypoint_message, routeNames),
                            onPositive = {
                                deleteWaypointSafely(waypoint)
                            }
                        )
                    } else {
                        deleteWaypointSafely(waypoint)
                    }
                }
            }
        }
    }

    private fun showSkinSelectionDialog() {
        val skinNames = arrayOf("Default", "Forest", "Ocean", getString(R.string.import_skin))
        val skinDescriptions = arrayOf(
            "默认蓝色主题",
            "森林绿色主题", 
            "海洋蓝色主题",
            getString(R.string.import_skin_description)
        )
        
        // 创建包含描述的选项列表
        val optionsWithDescriptions = skinNames.mapIndexed { index, name ->
            "${name} - ${skinDescriptions[index]}"
        }.toTypedArray()
        
        DialogUtils.showOptionsDialog(
            context = this,
            title = getString(R.string.select_skin),
            options = optionsWithDescriptions
        ) { which ->
            when (which) {
                0, 1, 2 -> {
                    val selectedSkin = DefaultSkins.skins[which]
                    radarView.setSkin(selectedSkin)
                    saveSkinPreference(skinNames[which])
                }
                3 -> {
                    // 导入自定义皮肤
                    openFilePicker()
                }
            }
        }
    }
    
    private fun showImportSkinDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.import_skin))
            .setMessage(getString(R.string.import_skin_description))
            .setPositiveButton(getString(R.string.import_skin)) { _, _ ->
                openFilePicker()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun saveSkinPreference(skinName: String) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit().putString(PREF_SKIN_NAME, skinName).apply()
    }

    private fun loadSkinPreference() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val skinName = prefs.getString(PREF_SKIN_NAME, "Default")
        val skin = when (skinName) {
            "Forest" -> DefaultSkins.forest
            "Ocean" -> DefaultSkins.ocean
            else -> DefaultSkins.default
        }
        radarView.setSkin(skin)
    }

    private fun loadThemePreference() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val themeMode = prefs.getInt(PREF_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(themeMode)
    }

    private fun saveThemePreference(mode: Int) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit().putInt(PREF_THEME_MODE, mode).apply()
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    private fun showSettingsDialog() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val currentMode = prefs.getInt(PREF_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        
        val themeOptions = arrayOf(
            getString(R.string.system_default),
            getString(R.string.light_mode),
            getString(R.string.dark_mode)
        )
        
        val currentSelection = when (currentMode) {
            AppCompatDelegate.MODE_NIGHT_NO -> 1
            AppCompatDelegate.MODE_NIGHT_YES -> 2
            else -> 0
        }
        
        AlertDialog.Builder(this)
            .setTitle(R.string.theme_settings)
            .setSingleChoiceItems(themeOptions, currentSelection) { dialog, which ->
                val newMode = when (which) {
                    1 -> AppCompatDelegate.MODE_NIGHT_NO
                    2 -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                saveThemePreference(newMode)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun openFilePicker() {
        skinPickerLauncher.launch(arrayOf("application/json"))
    }

    private fun handleCreateRouteResult(data: android.content.Intent) {
        val newRoute = data.getSerializableExtraCompat<Route>("new_route") ?: return
        val shouldStartNavigation = data.getBooleanExtra("start_navigation", false)
        lifecycleScope.launch {
            val waypointsWithIds = mutableListOf<Waypoint>()
            // First, save any new waypoints to get their database IDs
            for (waypoint in newRoute.waypoints) {
                if (waypoint.id == 0L) {
                    val newId = db.waypointDao().insert(waypoint)
                    val savedWaypoint = waypoint.copy(id = newId)
                    waypointsWithIds.add(savedWaypoint)
                    waypoints.add(savedWaypoint) // Add to main list
                    // Draw new waypoint on map
                    runOnUiThread {
                        val marker = aMap.addMarker(
                            MarkerOptions()
                                .position(LatLng(savedWaypoint.latitude, savedWaypoint.longitude))
                                .title(savedWaypoint.name)
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                        )
                        waypointMarkers.add(marker)
                    }
                } else {
                    waypointsWithIds.add(waypoint) // Existing waypoint
                }
            }

            // Now, save the route and its cross-references with correct IDs
            val routeId = db.routeDao().insertRoute(newRoute)
            waypointsWithIds.forEach { waypoint ->
                db.routeDao().insertRouteWaypointCrossRef(RouteWaypointCrossRef(routeId, waypoint.id))
            }

            val finalRoute = newRoute.copy(id = routeId)
            finalRoute.waypoints.clear()
            finalRoute.waypoints.addAll(waypointsWithIds)
            routes.add(finalRoute)

            runOnUiThread {
                Toast.makeText(this@MainActivity, "Route '${newRoute.name}' saved", Toast.LENGTH_SHORT).show()
                if (shouldStartNavigation) {
                    startRouteNavigation(finalRoute)
                }
            }
        }
    }

    private fun handleEditRouteResult(data: android.content.Intent) {
        val updatedRoute = data.getSerializableExtraCompat<Route>("new_route") ?: return
        lifecycleScope.launch {
            db.routeDao().updateRoute(updatedRoute)
            db.routeDao().deleteCrossRefsForRoute(updatedRoute.id)
            updatedRoute.waypoints.forEach { waypoint ->
                db.routeDao().insertRouteWaypointCrossRef(RouteWaypointCrossRef(updatedRoute.id, waypoint.id))
            }

            val index = routes.indexOfFirst { r -> r.id == updatedRoute.id }
            if (index != -1) {
                routes[index] = updatedRoute
            }
            runOnUiThread {
                DialogUtils.showSuccessToast(this@MainActivity, getString(R.string.route_updated))
                if (currentRoute?.id == updatedRoute.id) {
                    drawRouteOnMap(updatedRoute.waypoints)
                }
            }
        }
    }

    private fun importSkinFromFile(uri: android.net.Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val json = inputStream.reader().readText()
                val skin = com.google.gson.Gson().fromJson(json, RadarSkin::class.java)
                // Here you would typically save the skin to a list of custom skins
                // For simplicity, we'll just apply it directly for now
                radarView.setSkin(skin)
                simpleCompassView.setSkin(skin)
                DialogUtils.showSuccessToast(this, getString(R.string.skin_imported))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            DialogUtils.showErrorToast(this, getString(R.string.skin_import_failed))
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_search -> {
                showSearchDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun flipRadarCard() {
        val root = radarContent
        val front = radarView
        val back = simpleCompassView
        
        val scale = resources.displayMetrics.density
        root.cameraDistance = 8000 * scale

        val outAnim = android.animation.ObjectAnimator.ofFloat(root, "rotationY", if (isRadarFlipped) 180f else 0f, if (isRadarFlipped) 90f else 90f)
        val inAnim = android.animation.ObjectAnimator.ofFloat(root, "rotationY", if (isRadarFlipped) -90f else 270f, if (isRadarFlipped) 0f else 180f)
        
        outAnim.duration = 150
        inAnim.duration = 150
        
        outAnim.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                if (isRadarFlipped) {
                    back.visibility = android.view.View.GONE
                    front.visibility = android.view.View.VISIBLE
                    root.rotationY = 0f
                } else {
                    front.visibility = android.view.View.GONE
                    back.visibility = android.view.View.VISIBLE
                    root.rotationY = 180f
                }
                isRadarFlipped = !isRadarFlipped
                inAnim.start()
            }
        })
        outAnim.start()
    }

    private fun updateRadarViews(current: LatLng, target: LatLng) {
        radarView.updateTarget(current, target)
        simpleCompassView.updateTarget(current, target)
    }
}
