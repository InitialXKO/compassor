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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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

    // 路点和路线
    private val waypoints = mutableListOf<Waypoint>()
    private val waypointMarkers = mutableListOf<Marker>()
    private val routes = mutableListOf<Route>()
    private var routePolyline: Polyline? = null
    private var currentRoute: Route? = null
    private var currentWaypointIndex: Int = -1

    companion object {
        private const val TAG = "MainActivity"
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private const val PICK_SKIN_FILE_REQUEST_CODE = 1002
        private const val CREATE_ROUTE_REQUEST_CODE = 1003
        private const val EDIT_ROUTE_REQUEST_CODE = 1004
        private const val DATA_FILENAME = "compassor_data.json"
        private const val PREFS_NAME = "CompassorPrefs"
        private const val PREF_SKIN_NAME = "SkinName"
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Update AMap privacy policies first
        AMapLocationClient.updatePrivacyShow(this, true, true)
        AMapLocationClient.updatePrivacyAgree(this, true)

        setContentView(R.layout.activity_main)

        // 初始化视图
        initViews(savedInstanceState)

        // 加载数据
        loadData()

        // 加载皮肤
        loadSkinPreference()

        // 请求权限
        checkAndRequestPermissions()
    }

    private fun initViews(savedInstanceState: Bundle?) {
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

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
                    Toast.makeText(this, R.string.location_permission_denied, Toast.LENGTH_LONG).show()
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
            interval = 2000 // 定位间隔2秒
            isNeedAddress = true
            isOnceLocation = false
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
                // 定位成功
                myCurrentLatLng = LatLng(it.latitude, it.longitude)

                // 第一次定位时移动相机
                if (isFirstLocation) {
                    aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(myCurrentLatLng, 15f))
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
                                Toast.makeText(this, "已到达路点，前往下一个: ${nextWaypoint.name}", Toast.LENGTH_SHORT).show()
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
            .setNeutralButton("存为路点") { _, _ ->
                selectedPoi?.let {
                    val latLng = LatLng(it.latLonPoint.latitude, it.latLonPoint.longitude)
                    addWaypoint(latLng, it.title)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun addWaypoint(latLng: LatLng, name: String) {
        val distance = FloatArray(1)
        val existingWaypoint = waypoints.find {
            Location.distanceBetween(latLng.latitude, latLng.longitude, it.latitude, it.longitude, distance)
            // Name check is case-insensitive and also checks for partial matches
            (it.name.equals(name, ignoreCase = true) || it.name.contains(name, ignoreCase = true) || name.contains(it.name, ignoreCase = true)) && distance[0] < 10
        }

        if (existingWaypoint != null) {
            // If a similar waypoint exists, ask the user if they want to update it
            AlertDialog.Builder(this)
                .setTitle("更新路点")
                .setMessage("附近已存在一个相似的路点 '${existingWaypoint.name}'。您想用新的位置和名称 '$name' 更新它吗？")
                .setPositiveButton("更新") { _, _ ->
                    updateWaypoint(existingWaypoint, name, newLatLng = latLng)
                }
                .setNegativeButton("取消", null)
                .show()
        } else {
            // If no similar waypoint exists, add a new one
            val waypoint = Waypoint(
                id = System.currentTimeMillis(),
                name = name,
                latitude = latLng.latitude,
                longitude = latLng.longitude
            )
            waypoints.add(waypoint)

            // 在地图上添加标记
            val marker = aMap.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title(name)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
            )
            waypointMarkers.add(marker)
            saveData()

            Toast.makeText(this, "路点已保存: $name", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSaveWaypointDialog(latLng: LatLng, waypointToEdit: Waypoint? = null, defaultName: String? = null) {
        val editText = EditText(this)
        editText.hint = getString(R.string.waypoint_name_hint)
        if (waypointToEdit != null) {
            editText.setText(waypointToEdit.name)
        } else if (defaultName != null) {
            editText.setText(defaultName)
        }

        val builder = AlertDialog.Builder(this)
            .setTitle(if (waypointToEdit == null) "保存路点" else "编辑路点")
            .setView(editText)
            .setPositiveButton(R.string.save) { _, _ ->
                val waypointName = editText.text.toString().trim()
                if (waypointName.isNotEmpty()) {
                    if (waypointToEdit == null) {
                        addWaypoint(latLng, waypointName)
                    } else {
                        updateWaypoint(waypointToEdit, waypointName)
                    }
                } else {
                    Toast.makeText(this, "路点名称不能为空", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)

        if (waypointToEdit != null) {
            builder.setNeutralButton(R.string.delete) { _, _ ->
                deleteWaypointSafely(waypointToEdit)
            }
        }

        builder.show()
    }

    private fun showMapLongClickOptionsDialog(latLng: LatLng) {
        val options = arrayOf("增加路点", "设为目的地")
        AlertDialog.Builder(this)
            .setTitle("选择操作")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> reverseGeocode(latLng) { name ->
                        showSaveWaypointDialog(latLng, defaultName = name)
                    }
                    1 -> reverseGeocode(latLng) { name ->
                        setTargetLocation(latLng, name)
                    }
                }
            }
            .show()
    }

    private fun updateWaypoint(waypoint: Waypoint, newName: String, newLatLng: LatLng? = null) {
        // Check for potential duplicates before updating
        val otherWaypoints = waypoints.filter { it.id != waypoint.id }
        val distance = FloatArray(1)
        val potentialDuplicate = otherWaypoints.find {
            val checkLatLng = newLatLng ?: LatLng(waypoint.latitude, waypoint.longitude)
            Location.distanceBetween(checkLatLng.latitude, checkLatLng.longitude, it.latitude, it.longitude, distance)
            it.name.equals(newName, ignoreCase = true) && distance[0] < 10
        }

        if (potentialDuplicate != null) {
            Toast.makeText(this, "附近已存在同名路点，无法更新", Toast.LENGTH_SHORT).show()
            return // Abort update
        }

        var oldName = waypoint.name
        waypoint.name = newName

        var marker = waypointMarkers.find { it.position.latitude == waypoint.latitude && it.position.longitude == waypoint.longitude }

        if (newLatLng != null) {
            waypoint.latitude = newLatLng.latitude
            waypoint.longitude = newLatLng.longitude
            marker?.position = newLatLng
        }

        marker?.title = newName

        // Update routes that contain this waypoint and redraw if necessary
        routes.forEach { route ->
            if (route.waypoints.any { it.id == waypoint.id }) {
                // If the route is currently displayed, redraw it
                if (currentRoute?.id == route.id || routePolyline?.points?.size == route.waypoints.size) {
                    drawRouteOnMap(route.waypoints)
                }
            }
        }

        saveData()
        Toast.makeText(this, "路点 '$oldName' 已更新为 '$newName'", Toast.LENGTH_SHORT).show()
    }

    private fun deleteWaypointSafely(waypoint: Waypoint) {
        // Remove waypoint from all routes that contain it
        val routesToClean = routes.filter { it.waypoints.contains(waypoint) }
        routesToClean.forEach { it.waypoints.remove(waypoint) }

        // Delete routes that have become invalid (less than 2 waypoints)
        val routesToDelete = routes.filter { it.waypoints.size < 2 }
        routes.removeAll(routesToDelete)

        // Finally, delete the waypoint itself
        waypoints.remove(waypoint)
        val markerToRemove = waypointMarkers.find { it.position.latitude == waypoint.latitude && it.position.longitude == waypoint.longitude }
        markerToRemove?.remove()
        waypointMarkers.remove(markerToRemove)

        saveData()
        Toast.makeText(this, "路点已删除", Toast.LENGTH_SHORT).show()
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
            .setTitle("管理路线")
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

        builder.show()
    }

    private fun startRouteNavigation(route: Route) {
        currentRoute = route
        currentWaypointIndex = 0
        val firstWaypoint = route.waypoints[0]
        setTargetLocation(LatLng(firstWaypoint.latitude, firstWaypoint.longitude), firstWaypoint.name)
        drawRouteOnMap(route.waypoints)
        Toast.makeText(this, "开始路线导航: ${route.name}", Toast.LENGTH_SHORT).show()
    }

    private fun showRouteOptionsDialog(route: Route) {
        val options = arrayOf("开始导航", "编辑", "删除", "设为目的地")
        AlertDialog.Builder(this)
            .setTitle(route.name)
            .setItems(options) { _, which ->
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
            .show()
    }

    private fun deleteRoute(route: Route) {
        // If the deleted route is currently displayed, remove the polyline
        if (routePolyline?.points?.map { LatLng(it.latitude, it.longitude) } == route.waypoints.map { LatLng(it.latitude, it.longitude) }) {
            routePolyline?.remove()
            routePolyline = null
        }
        routes.remove(route)
        saveData()
        Toast.makeText(this, "路线已删除", Toast.LENGTH_SHORT).show()
    }

    private data class DataBundle(
        val waypoints: List<Waypoint> = emptyList(),
        val routes: List<Route> = emptyList()
    )

    private fun saveData() {
        val dataBundle = DataBundle(waypoints, routes)
        val json = com.google.gson.Gson().toJson(dataBundle)
        Log.d(TAG, "Saving data: $json")
        try {
            openFileOutput(DATA_FILENAME, MODE_PRIVATE).use {
                it.write(json.toByteArray())
            }
            Log.d(TAG, "Data saved successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save data", e)
            Toast.makeText(this, "Failed to save data: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadData() {
        val file = getFileStreamPath(DATA_FILENAME)
        if (!file.exists()) {
            Log.d(TAG, "Data file does not exist. No data to load.")
            return
        }

        try {
            val json = file.reader().readText()
            Log.d(TAG, "Loading data: $json")
            if (json.isBlank()) {
                Log.d(TAG, "Data file is blank.")
                return
            }

            val dataBundle = com.google.gson.Gson().fromJson(json, DataBundle::class.java)

            // Defensive check for nulls, though default values should prevent this.
            val loadedWaypoints = dataBundle.waypoints ?: emptyList()
            val loadedRoutes = dataBundle.routes ?: emptyList()

            waypoints.clear()
            waypoints.addAll(loadedWaypoints)
            routes.clear()
            routes.addAll(loadedRoutes)

            Log.d(TAG, "Loaded ${waypoints.size} waypoints and ${routes.size} routes.")

            // Redraw waypoints on the map
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
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load data", e)
            Toast.makeText(this, "Failed to load data", Toast.LENGTH_SHORT).show()
            // In case of error, delete the corrupted file to start fresh next time.
             file.delete()
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
                    Toast.makeText(this, "無法獲取當前位置", Toast.LENGTH_SHORT).show()
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
        }
        drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
        return true
    }

    private fun showSearchDialog() {
        val editText = EditText(this)
        editText.hint = "输入搜索关键词"
        AlertDialog.Builder(this)
            .setTitle("搜索地点")
            .setView(editText)
            .setPositiveButton("搜索") { _, _ ->
                val keyword = editText.text.toString().trim()
                if (keyword.isNotEmpty()) {
                    searchPOI(keyword)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showWaypointManagementDialog() {
        val waypointDisplayInfo = waypoints.map { waypoint ->
            val routesContainingWaypoint = routes.filter { it.waypoints.contains(waypoint) }
            val routeNames = if (routesContainingWaypoint.isNotEmpty()) {
                " (In routes: ${routesContainingWaypoint.joinToString { it.name }})"
            } else {
                ""
            }
            waypoint.name + routeNames
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("管理路点")
            .setItems(waypointDisplayInfo) { _, which ->
                showWaypointOptionsDialog(waypoints[which])
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showWaypointOptionsDialog(waypoint: Waypoint) {
        val options = arrayOf("设为目的地", "编辑名称", "删除")
        AlertDialog.Builder(this)
            .setTitle(waypoint.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> setTargetLocation(LatLng(waypoint.latitude, waypoint.longitude), waypoint.name)
                    1 -> showSaveWaypointDialog(LatLng(waypoint.latitude, waypoint.longitude), waypoint)
                    2 -> {
                        val routesContainingWaypoint = routes.filter { it.waypoints.contains(waypoint) }
                        if (routesContainingWaypoint.isNotEmpty()) {
                            val routeNames = routesContainingWaypoint.joinToString { it.name }
                            AlertDialog.Builder(this)
                                .setTitle("确认删除")
                                .setMessage("该路点正在被路线: $routeNames 使用。删除该路点将同时从这些路线中移除，并可能导致路线被删除。是否确认删除？")
                                .setPositiveButton("确认") { _, _ ->
                                    deleteWaypointSafely(waypoint)
                                }
                                .setNegativeButton("取消", null)
                                .show()
                        } else {
                            deleteWaypointSafely(waypoint)
                        }
                    }
                }
            }
            .show()
    }

    private fun showSkinSelectionDialog() {
        val skinNames = arrayOf("Default", "Forest", "Ocean")
        AlertDialog.Builder(this)
            .setTitle("Select Skin")
            .setItems(skinNames) { _, which ->
                val selectedSkin = DefaultSkins.skins[which]
                radarView.setSkin(selectedSkin)
                saveSkinPreference(skinNames[which])
            }
            .setPositiveButton("Import Skin") { _, _ ->
                openFilePicker()
            }
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
                    newRoute?.let {
                        routes.add(it)
                        saveData()
                        Toast.makeText(this, "Route '${it.name}' saved", Toast.LENGTH_SHORT).show()
                    }
                    val waypointWrapper = data.getSerializableExtra("waypoints_wrapper") as? WaypointListWrapper
                    waypointWrapper?.waypoints?.forEach { waypoint ->
                        addWaypoint(LatLng(waypoint.latitude, waypoint.longitude), waypoint.name)
                    }
                }
                EDIT_ROUTE_REQUEST_CODE -> {
                    val updatedRoute = data.getSerializableExtra("new_route") as? Route
                    updatedRoute?.let {
                        val index = routes.indexOfFirst { r -> r.id == it.id }
                        if (index != -1) {
                            routes[index] = it
                            saveData()
                            Toast.makeText(this, "Route '${it.name}' updated", Toast.LENGTH_SHORT).show()
                            // If the updated route is the one currently displayed, redraw it
                            if (currentRoute?.id == it.id) {
                                drawRouteOnMap(it.waypoints)
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
                Toast.makeText(this, "Skin imported and applied", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to import skin", Toast.LENGTH_SHORT).show()
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
