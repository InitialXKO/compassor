package com.growsnova.compassor

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import com.amap.api.maps.AMap
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

class MainActivity : AppCompatActivity(), AMapLocationListener, NavigationView.OnNavigationItemSelectedListener {

    // 视图组件
    private lateinit var mapView: MapView
    private lateinit var aMap: AMap
    private lateinit var radarView: RadarCompassView
    private lateinit var drawerLayout: androidx.drawerlayout.widget.DrawerLayout
    private lateinit var navigationView: com.google.android.material.navigation.NavigationView
    private lateinit var toolbar: androidx.appcompat.widget.Toolbar

    // 定位组件
    private var locationClient: AMapLocationClient? = null
    private var locationOption: AMapLocationClientOption? = null
    private var myCurrentLatLng: LatLng? = null
    private var isFirstLocation = true

    // POI搜索
    private var poiSearch: PoiSearch? = null
    private var geocodeSearch: com.amap.api.services.geocoder.GeocodeSearch? = null
    private var targetLatLng: LatLng? = null
    private var targetMarker: Marker? = null

    // 收藏地点和路线
    private val waypoints = mutableListOf<Waypoint>()
    private val waypointMarkers = mutableListOf<Marker>()
    private val routes = mutableListOf<Route>()
    private var routePolyline: Polyline? = null
    private var currentRoute: Route? = null
    private var currentWaypointIndex: Int = -1
    private val db by lazy { AppDatabase.getDatabase(this) }

    companion object {
        private const val TAG = "MainActivity"
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private const val PICK_SKIN_FILE_REQUEST_CODE = 1002
        private const val CREATE_ROUTE_REQUEST_CODE = 1003
        private const val EDIT_ROUTE_REQUEST_CODE = 1004
        private const val PREFS_NAME = "CompassorPrefs"
        private const val PREF_SKIN_NAME = "SkinName"
        private const val PREF_THEME_MODE = "ThemeMode"
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

        // 初始化地图
        mapView.onCreate(savedInstanceState)
        aMap = mapView.map

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
        // 初始化定位客户端
        locationClient = AMapLocationClient(applicationContext)
        locationOption = AMapLocationClientOption()

        // 配置定位选项
        locationOption?.apply {
            locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
            interval = 3000 // 定位间隔3秒，减少频率以提高稳定性
            isNeedAddress = true
            isOnceLocation = false
            // 启用GPS传感器和WiFi定位混合模式
            locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
        }

        // 设置定位参数
        locationClient?.setLocationOption(locationOption)
        locationClient?.setLocationListener(this)

        // 启动定位
        locationClient?.startLocation()

        // 显示我的位置图层
        aMap.isMyLocationEnabled = true
        aMap.myLocationStyle = MyLocationStyle().apply {
            myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE_NO_CENTER)
            strokeColor(Color.BLUE)
            radiusFillColor(Color.argb(50, 0, 0, 255))
        }
    }

    override fun onLocationChanged(location: AMapLocation?) {
        location?.let {
            if (it.errorCode == 0) {
                // 定位成功，添加防抖尼处理
                val newLatLng = LatLng(it.latitude, it.longitude)
                
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

                    // 平滑移动相机而不是跳跃
                    if (isFirstLocation) {
                        aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(myCurrentLatLng!!, 15f))
                    } else {
                        aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(myCurrentLatLng!!, 15f))
                    }
                    isFirstLocation = false
                }

                // 更新雷达视图
                targetLatLng?.let { target ->
                    radarView.updateTarget(myCurrentLatLng!!, target)
                }

                // 检查是否在路线导航中
                currentRoute?.let { route ->
                    if (currentWaypointIndex != -1) {
                        val currentTargetWaypoint = route.waypoints[currentWaypointIndex]
                        val distance = FloatArray(1)
                        Location.distanceBetween(
                            myCurrentLatLng!!.latitude, myCurrentLatLng!!.longitude,
                            currentTargetWaypoint.latitude, currentTargetWaypoint.longitude,
                            distance
                        )

                        if (distance[0] < 20) { // 20米阈值
                            currentWaypointIndex++
                            if (currentWaypointIndex < route.waypoints.size) {
                                val nextWaypoint = route.waypoints[currentWaypointIndex]
                                setTargetLocation(LatLng(nextWaypoint.latitude, nextWaypoint.longitude), nextWaypoint.name)
                                Toast.makeText(this, "已到达地点，前往下一个: ${nextWaypoint.name}", Toast.LENGTH_SHORT).show()
                            } else {
                                if (route.isLooping) {
                                    currentWaypointIndex = 0
                                    val firstWaypoint = route.waypoints[0]
                                    setTargetLocation(LatLng(firstWaypoint.latitude, firstWaypoint.longitude), firstWaypoint.name)
                                    Toast.makeText(this, "路线循环，返回起点: ${firstWaypoint.name}", Toast.LENGTH_SHORT).show()
                                } else {
                                    // 路线结束
                                    Toast.makeText(this, "已完成路线: ${route.name}", Toast.LENGTH_SHORT).show()
                                    currentRoute = null
                                    currentWaypointIndex = -1
                                    routePolyline?.remove()
                                    routePolyline = null
                                }
                            }
                        }
                    }
                }
            } else {
                Toast.makeText(
                    this,
                    "定位失败: ${it.errorCode} - ${it.errorInfo}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun searchPOI(keyword: String) {
        Toast.makeText(this, R.string.searching, Toast.LENGTH_SHORT).show()

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
                            Toast.makeText(
                                this@MainActivity,
                                R.string.no_result,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "搜索失败: $errorCode",
                        Toast.LENGTH_SHORT
                    ).show()
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
        targetMarker?.showInfoWindow()

        // 移动相机到目标位置
        aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))

        // 更新雷达视图
        myCurrentLatLng?.let { myLoc ->
            radarView.updateTarget(myLoc, latLng)
        }

        // 清除路线
        routePolyline?.remove()
        routePolyline = null
    }

    private fun showPOIListDialog(pois: List<PoiItem>) {
        val poiNames = pois.map { "${it.title} (${it.distance}m)" }.toTypedArray()
        var selectedPoi: PoiItem? = null

        AlertDialog.Builder(this)
            .setTitle("选择一个地点")
            .setSingleChoiceItems(poiNames, -1) { _, which ->
                selectedPoi = pois[which]
            }
            .setPositiveButton("设为目的地") { _, _ ->
                selectedPoi?.let {
                    val latLng = LatLng(it.latLonPoint.latitude, it.latLonPoint.longitude)
                    setTargetLocation(latLng, it.title)
                }
            }
            .setNeutralButton("添加到收藏") { _, _ ->
                selectedPoi?.let {
                    val latLng = LatLng(it.latLonPoint.latitude, it.latLonPoint.longitude)
                    addWaypoint(latLng, it.title)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
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
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("更新收藏地点")
                        .setMessage("附近已存在一个相似的收藏地点 '${existingWaypoint.name}'。您想用新的位置和名称 '$name' 更新它吗？")
                        .setPositiveButton("更新") { _, _ ->
                            updateWaypoint(existingWaypoint, name, newLatLng = latLng)
                        }
                        .setNegativeButton("取消", null)
                        .show()
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

            val oldName = waypoint.name
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

        if (waypoints.size > 1) {
            val polylineOptions = PolylineOptions()
                .addAll(waypoints.map { LatLng(it.latitude, it.longitude) })
                .color(Color.BLUE)
                .width(10f)

            routePolyline = aMap.addPolyline(polylineOptions)
        }
    }

    private fun showRouteManagementDialog() {
        val routeNames = routes.map { it.name }.toTypedArray()

        val builder = AlertDialog.Builder(this)
            .setTitle(getString(R.string.manage_routes))
            .setItems(routeNames) { _, which ->
                showRouteOptionsDialog(routes[which])
            }
            .setPositiveButton(R.string.create_route) { _, _ ->
                val intent = android.content.Intent(this, CreateRouteActivity::class.java)
                intent.putExtra("waypoints_wrapper", WaypointListWrapper(ArrayList(waypoints)))
                myCurrentLatLng?.let {
                    intent.putExtra("current_latlng", it)
                }
                startActivityForResult(intent, CREATE_ROUTE_REQUEST_CODE)
            }
            .setNegativeButton(R.string.cancel, null)

        // 添加创建路线按钮
        .setPositiveButton(getString(R.string.create_route)) { _, _ ->
            val intent = android.content.Intent(this, CreateRouteActivity::class.java)
            intent.putExtra("waypoints_wrapper", WaypointListWrapper(ArrayList(waypoints)))
            myCurrentLatLng?.let {
                intent.putExtra("current_latlng", it)
            }
            startActivityForResult(intent, CREATE_ROUTE_REQUEST_CODE)
        }

        builder.show()
    }

    private fun startRouteNavigation(route: Route) {
        currentRoute = route
        currentWaypointIndex = 0
        val firstWaypoint = route.waypoints[0]
        setTargetLocation(LatLng(firstWaypoint.latitude, firstWaypoint.longitude), firstWaypoint.name)
        drawRouteOnMap(route.waypoints)
        DialogUtils.showSuccessToast(this, getString(R.string.navigation_started, route.name))
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
                1 -> {
                    val intent = android.content.Intent(this, CreateRouteActivity::class.java)
                        intent.putExtra("route_to_edit", route)
                        intent.putExtra("waypoints_wrapper", WaypointListWrapper(ArrayList(waypoints)))
                        myCurrentLatLng?.let {
                            intent.putExtra("current_latlng", it)
                        }
                        startActivityForResult(intent, EDIT_ROUTE_REQUEST_CODE)
                    }
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
        locationClient?.startLocation()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        locationClient?.stopLocation()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
        locationClient?.onDestroy()
        locationClient = null
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
                
                historyRecyclerView.layoutManager = LinearLayoutManager(this@MainActivity)
                
                val historyAdapter = SearchHistoryAdapter(
                    searchHistories.toMutableList(),
                    onItemClick = { history ->
                        editText.setText(history.query)
                    },
                    onDeleteClick = { history ->
                        lifecycleScope.launch {
                            db.searchHistoryDao().delete(history.id)
                        }
                    }
                )
                historyRecyclerView.adapter = historyAdapter
                
                if (searchHistories.isEmpty()) {
                    historyRecyclerView.visibility = android.view.View.GONE
                    clearHistoryButton.visibility = android.view.View.GONE
                } else {
                    historyRecyclerView.visibility = android.view.View.VISIBLE
                    clearHistoryButton.visibility = android.view.View.VISIBLE
                }
                
                clearHistoryButton.setOnClickListener {
                    lifecycleScope.launch {
                        db.searchHistoryDao().clearAll()
                        historyRecyclerView.visibility = android.view.View.GONE
                        clearHistoryButton.visibility = android.view.View.GONE
                        historyAdapter.notifyDataSetChanged()
                    }
                }
                
                val dialog = AlertDialog.Builder(this@MainActivity)
                    .setTitle(getString(R.string.search_location))
                    .setView(view)
                    .setPositiveButton(getString(R.string.search)) { _, _ ->
                        val keyword = editText.text.toString().trim()
                        if (keyword.isNotEmpty()) {
                            searchPOI(keyword)
                            lifecycleScope.launch {
                                db.searchHistoryDao().insert(SearchHistory(query = keyword))
                            }
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .create()
                    
                dialog.show()
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
        val skinNames = arrayOf("Default", "Forest", "Ocean")
        val skinDescriptions = arrayOf(
            "默认蓝色主题",
            "森林绿色主题", 
            "海洋蓝色主题"
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
            val skinIndex = which % 3 // 确保索引在范围内
            val selectedSkin = DefaultSkins.skins[skinIndex]
            radarView.setSkin(selectedSkin)
            saveSkinPreference(skinNames[skinIndex])
            
            // 显示导入选项的单独对话框
            showImportSkinDialog()
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
        val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        startActivityForResult(intent, PICK_SKIN_FILE_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && data != null) {
            when (requestCode) {
                CREATE_ROUTE_REQUEST_CODE -> {
                    val newRoute = data.getSerializableExtra("new_route") as? Route
                    newRoute?.let { route ->
                        lifecycleScope.launch {
                            val waypointsWithIds = mutableListOf<Waypoint>()
                            // First, save any new waypoints to get their database IDs
                            for (waypoint in route.waypoints) {
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
                            val routeId = db.routeDao().insertRoute(route)
                            waypointsWithIds.forEach { waypoint ->
                                db.routeDao().insertRouteWaypointCrossRef(RouteWaypointCrossRef(routeId, waypoint.id))
                            }

                            val finalRoute = route.copy(id = routeId)
                            finalRoute.waypoints.clear()
                            finalRoute.waypoints.addAll(waypointsWithIds)
                            routes.add(finalRoute)

                            runOnUiThread {
                                Toast.makeText(this@MainActivity, "Route '${route.name}' saved", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                EDIT_ROUTE_REQUEST_CODE -> {
                    val updatedRoute = data.getSerializableExtra("new_route") as? Route
                    updatedRoute?.let { route ->
                        lifecycleScope.launch {
                            db.routeDao().updateRoute(route)
                            db.routeDao().deleteCrossRefsForRoute(route.id)
                            route.waypoints.forEach { waypoint ->
                                db.routeDao().insertRouteWaypointCrossRef(RouteWaypointCrossRef(route.id, waypoint.id))
                            }

                            val index = routes.indexOfFirst { r -> r.id == route.id }
                            if (index != -1) {
                                routes[index] = route
                            }
                            runOnUiThread {
                                DialogUtils.showSuccessToast(this@MainActivity, getString(R.string.route_updated))
                                if (currentRoute?.id == route.id) {
                                    drawRouteOnMap(route.waypoints)
                                }
                            }
                        }
                    }
                }
                PICK_SKIN_FILE_REQUEST_CODE -> {
                    data.data?.also { uri ->
                        importSkinFromFile(uri)
                    }
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
}
