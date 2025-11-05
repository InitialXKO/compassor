package com.growsnova.compassor

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
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
    private lateinit var searchEditText: EditText
    private lateinit var searchButton: Button
    private lateinit var drawerLayout: androidx.drawerlayout.widget.DrawerLayout
    private lateinit var navigationView: com.google.android.material.navigation.NavigationView
    private lateinit var toolbar: androidx.appcompat.widget.Toolbar

    // 定位组件
    private var locationClient: AMapLocationClient? = null
    private var locationOption: AMapLocationClientOption? = null
    private var myCurrentLatLng: LatLng? = null

    // POI搜索
    private var poiSearch: PoiSearch? = null
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
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private const val DATA_FILENAME = "compassor_data.json"
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化视图
        initViews(savedInstanceState)

        // 加载数据
        loadData()

        // 请求权限
        checkAndRequestPermissions()

        // 设置搜索按钮点击事件
        searchButton.setOnClickListener {
            val keyword = searchEditText.text.toString().trim()
            if (keyword.isNotEmpty()) {
                searchPOI(keyword)
            } else {
                Toast.makeText(this, "请输入搜索关键词", Toast.LENGTH_SHORT).show()
            }
        }
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

        mapView = findViewById(R.id.mapView)
        radarView = findViewById(R.id.radarView)
        searchEditText = findViewById(R.id.searchEditText)
        searchButton = findViewById(R.id.searchButton)

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
                showSaveWaypointDialog(marker.position, waypoint)
            }
            true
        }

        // 设置地图长按事件
        aMap.setOnMapLongClickListener { latLng ->
            showSaveWaypointDialog(latLng)
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
        AMapLocationClient.updatePrivacyShow(this, true, true)
        AMapLocationClient.updatePrivacyAgree(this, true)

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
                if (targetLatLng == null) {
                    aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(myCurrentLatLng, 15f))
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
                    showSaveWaypointDialog(latLng, waypointToEdit = null)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun addWaypoint(latLng: LatLng, name: String) {
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

    private fun showSaveWaypointDialog(latLng: LatLng, waypointToEdit: Waypoint? = null) {
        val editText = EditText(this)
        editText.hint = getString(R.string.waypoint_name_hint)
        waypointToEdit?.let { editText.setText(it.name) }

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
                deleteWaypoint(waypointToEdit)
            }
        }

        builder.show()
    }

    private fun updateWaypoint(waypoint: Waypoint, newName: String) {
        val oldName = waypoint.name
        waypoint.name = newName
        val marker = waypointMarkers.find { it.position.latitude == waypoint.latitude && it.position.longitude == waypoint.longitude }
        marker?.title = newName
        saveData()
        Toast.makeText(this, "路点已更新", Toast.LENGTH_SHORT).show()
    }

    private fun deleteWaypoint(waypoint: Waypoint) {
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

    private fun showRouteCreationDialog(routeToEdit: Route?) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_route, null)
        val routeNameEditText = dialogView.findViewById<EditText>(R.id.routeNameEditText)
        val waypointsRecyclerView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.waypointsRecyclerView)

        // Populate route name if editing
        routeToEdit?.let { routeNameEditText.setText(it.name) }

        // Setup RecyclerView with waypoints
        val waypointAdapter = WaypointSelectionAdapter(waypoints, routeToEdit?.waypoints ?: mutableListOf())
        waypointsRecyclerView.adapter = waypointAdapter
        waypointsRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)

        AlertDialog.Builder(this)
            .setTitle(if (routeToEdit == null) "创建路线" else "编辑路线")
            .setView(dialogView)
            .setPositiveButton(R.string.save) { _, _ ->
                val routeName = routeNameEditText.text.toString().trim()
                val selectedWaypoints = waypointAdapter.getSelectedWaypoints()

                if (routeName.isNotEmpty() && selectedWaypoints.isNotEmpty()) {
                    if (routeToEdit == null) {
                        // Create new route
                        val newRoute = Route(id = System.currentTimeMillis(), name = routeName, waypoints = selectedWaypoints)
                        routes.add(newRoute)
                        Toast.makeText(this, "路线已创建", Toast.LENGTH_SHORT).show()
                    } else {
                        // Update existing route
                        routeToEdit.name = routeName
                        routeToEdit.waypoints.clear()
                        routeToEdit.waypoints.addAll(selectedWaypoints)
                        Toast.makeText(this, "路线已更新", Toast.LENGTH_SHORT).show()
                    }
                    saveData()
                    drawRouteOnMap(selectedWaypoints)
                } else {
                    Toast.makeText(this, "路线名称和路点不能为空", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showRouteManagementDialog() {
        val routeNames = routes.map { it.name }.toTypedArray()

        val builder = AlertDialog.Builder(this)
            .setTitle("管理路线")
            .setItems(routeNames) { _, which ->
                showRouteOptionsDialog(routes[which])
            }
            .setPositiveButton(R.string.create_route) { _, _ ->
                showRouteCreationDialog(null)
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
                    1 -> showRouteCreationDialog(route)
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
        val waypoints: List<Waypoint>,
        val routes: List<Route>
    )

    private fun saveData() {
        val dataBundle = DataBundle(waypoints, routes)
        val json = com.google.gson.Gson().toJson(dataBundle)
        try {
            openFileOutput(DATA_FILENAME, MODE_PRIVATE).use {
                it.write(json.toByteArray())
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to save data", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadData() {
        try {
            val file = getFileStreamPath(DATA_FILENAME)
            if (file.exists()) {
                val json = file.reader().readText()
                val dataBundle = com.google.gson.Gson().fromJson(json, DataBundle::class.java)
                waypoints.clear()
                waypoints.addAll(dataBundle.waypoints)
                routes.clear()
                routes.addAll(dataBundle.routes)
                // Redraw waypoints on the map
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
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to load data", Toast.LENGTH_SHORT).show()
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

    override fun onNavigationItemSelected(item: android.view.MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_save_location -> {
                myCurrentLatLng?.let { latLng ->
                    showSaveWaypointDialog(latLng)
                } ?: run {
                    Toast.makeText(this, "無法獲取當前位置", Toast.LENGTH_SHORT).show()
                }
            }
            R.id.nav_manage_routes -> {
                showRouteManagementDialog()
            }
        }
        drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
        return true
    }
}
