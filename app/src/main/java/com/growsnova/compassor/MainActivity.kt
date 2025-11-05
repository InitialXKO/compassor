package com.growsnova.compassor

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
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

class MainActivity : AppCompatActivity(), AMapLocationListener {

    // 视图组件
    private lateinit var mapView: MapView
    private lateinit var aMap: AMap
    private lateinit var radarView: RadarCompassView
    private lateinit var searchEditText: EditText
    private lateinit var searchButton: Button

    // 定位组件
    private var locationClient: AMapLocationClient? = null
    private var locationOption: AMapLocationClientOption? = null
    private var myCurrentLatLng: LatLng? = null

    // POI搜索
    private var poiSearch: PoiSearch? = null
    private var targetLatLng: LatLng? = null
    private var targetMarker: Marker? = null

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
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
        poiSearch?.setOnPoiSearchListener(object : PoiSearch.OnPoiSearchListener {
            override fun onPoiSearched(result: PoiResult?, errorCode: Int) {
                if (errorCode == 1000) {
                    result?.pois?.let { pois ->
                        if (pois.isNotEmpty()) {
                            val targetPoi = pois[0] // 取第一个结果
                            val latLng = LatLng(
                                targetPoi.latLonPoint.latitude,
                                targetPoi.latLonPoint.longitude
                            )

                            // 设置目标位置
                            setTargetLocation(latLng, targetPoi.title)

                            Toast.makeText(
                                this@MainActivity,
                                "找到: ${targetPoi.title}",
                                Toast.LENGTH_SHORT
                            ).show()
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
}
