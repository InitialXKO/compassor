package com.growsnova.compassor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.amap.api.location.AMapLocationClient
import com.amap.api.maps.AMap
import com.amap.api.maps.LocationSource
import com.amap.api.maps.MapView
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.core.PoiItem
import com.amap.api.services.geocoder.GeocodeSearch
import com.amap.api.services.geocoder.RegeocodeQuery
import com.amap.api.services.geocoder.RegeocodeResult
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationView
import com.growsnova.compassor.base.AppConstants
import com.growsnova.compassor.data.repository.NavigationRepository
import com.growsnova.compassor.manager.MapManager
import com.growsnova.compassor.manager.NavigationManager
import com.growsnova.compassor.ui.viewmodel.LocationViewModel
import com.growsnova.compassor.ui.viewmodel.MapViewModel
import com.growsnova.compassor.ui.viewmodel.NavigationViewModel
import com.growsnova.compassor.CoordTransform
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var mapView: MapView
    private lateinit var aMap: AMap
    private lateinit var radarView: RadarCompassView
    private lateinit var simpleCompassView: SimpleCompassView
    private lateinit var radarContent: View
    private var isRadarFlipped = false
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toolbar: androidx.appcompat.widget.Toolbar
    
    private var currentPhotoPath: String? = null
    private var waypointPhotoImageView: ImageView? = null

    // Navigation UI
    private lateinit var navigationStatusCard: MaterialCardView
    private lateinit var navTargetText: android.widget.TextView
    private lateinit var navDistanceText: android.widget.TextView
    private lateinit var navFloorText: android.widget.TextView
    private lateinit var stopNavButton: MaterialButton
    private lateinit var skipNavButton: MaterialButton
    private lateinit var prevNavButton: MaterialButton

    private lateinit var searchBottomSheet: com.google.android.material.card.MaterialCardView
    private lateinit var bottomSheetBehavior: com.google.android.material.bottomsheet.BottomSheetBehavior<View>
    private lateinit var searchResultsAdapter: PoiListAdapter

    private val locationViewModel: LocationViewModel by viewModels()
    private val mapViewModel: MapViewModel by viewModels()
    private val navigationViewModel: NavigationViewModel by viewModels()

    private var locationDisabledSnackbar: com.google.android.material.snackbar.Snackbar? = null

    @Inject
    lateinit var mapManager: MapManager

    @Inject
    lateinit var navigationRepository: NavigationRepository

    @Inject
    lateinit var soundManager: com.growsnova.compassor.manager.SoundManager

    @Inject
    lateinit var wearDataSender: com.growsnova.compassor.wear.WearDataSender

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

    private var routeToExport: Route? = null
    private val exportRouteLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { route ->
            routeToExport?.let { exportRouteToFile(it, uri) }
        }
    }

    private val importRouteLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importRouteFromFile(it) }
    }

    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            currentPhotoPath?.let { path ->
                waypointPhotoImageView?.let { imageView ->
                    Glide.with(this).load(path).centerCrop().into(imageView)
                }
            }
        }
    }

    private val requestCameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            DialogUtils.showErrorToast(this, getString(R.string.camera_permission_denied))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AMapLocationClient.updatePrivacyShow(this, true, true)
        AMapLocationClient.updatePrivacyAgree(this, true)

        setContentView(R.layout.activity_main)

        initViews(savedInstanceState)
        setupObservers()
        checkAndRequestPermissions()
        handleNavigationIntent()
        handleIncomingLocationIntent(intent)
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingLocationIntent(intent)
    }

    private fun handleIncomingLocationIntent(intent: android.content.Intent?) {
        val parsed = CoordinateParser.parseIntent(intent)
        if (parsed != null) {
            if (parsed.gcj02LatLng.latitude == 0.0 && parsed.gcj02LatLng.longitude == 0.0) {
                // Incoming text address without coordinates: trigger POI search automatically
                navigationViewModel.searchPOI(parsed.name, locationViewModel.currentLocation.value)
            } else {
                val options = arrayOf(getString(R.string.start_navigation), getString(R.string.save_location))
                DialogUtils.showOptionsDialog(
                    this,
                    getString(R.string.incoming_location_dialog_title, parsed.name),
                    options
                ) { which ->
                    when (which) {
                        0 -> {
                            navigationViewModel.setTarget(parsed.gcj02LatLng, parsed.name)
                            DialogUtils.showSuccessToast(this, getString(R.string.nav_target_format, parsed.name))
                        }
                        1 -> {
                            showSaveWaypointDialog(parsed.gcj02LatLng, defaultName = parsed.name)
                        }
                    }
                }
            }
        }
    }

    private fun initViews(savedInstanceState: Bundle?) {
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

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
        simpleCompassView = findViewById(R.id.simpleCompassView)
        radarContent = findViewById(R.id.radarContent)
        
        radarContent.setOnClickListener { flipRadarCard() }

        initSearchBottomSheet()

        navigationStatusCard = findViewById(R.id.navigationStatusCard)
        navTargetText = findViewById(R.id.navTargetText)
        navDistanceText = findViewById(R.id.navDistanceText)
        navFloorText = findViewById(R.id.navFloorText)
        stopNavButton = findViewById(R.id.stopNavButton)
        skipNavButton = findViewById(R.id.skipNavButton)
        prevNavButton = findViewById(R.id.prevNavButton)
        
        navTargetText.isSelected = true

        stopNavButton.applyTouchScale()
        stopNavButton.setOnClickListener {
            DialogUtils.showConfirmationDialog(
                this,
                getString(R.string.stop_navigation),
                getString(R.string.confirm_stop_navigation),
                onPositive = { navigationViewModel.stopNavigation() }
            )
        }
        skipNavButton.applyTouchScale()
        skipNavButton.setOnClickListener { navigationViewModel.skipNextWaypoint() }
        prevNavButton.applyTouchScale()
        prevNavButton.setOnClickListener { navigationViewModel.goToPreviousWaypoint() }

        mapView.onCreate(savedInstanceState)
        aMap = mapView.map
        mapManager.initialize(aMap)
        mapManager.setOnMyLocationClickListener { showMyLocationOptionsDialog() }
        mapManager.setOnPoiClickListener { poi ->
            val latLng = LatLng(poi.coordinate.latitude, poi.coordinate.longitude)
            showPoiOptionsDialog(poi.name, latLng)
        }

        aMap.setOnMapLongClickListener { latLng -> showMapLongClickOptionsDialog(latLng) }
        aMap.setOnMapTouchListener { event ->
            if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                mapViewModel.setFollowMode(false)
            }
        }

        applySkin(navigationRepository.getSkinName())
    }

    private fun initSearchBottomSheet() {
        searchBottomSheet = findViewById(R.id.searchBottomSheet)
        bottomSheetBehavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(searchBottomSheet)
        bottomSheetBehavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN

        val recyclerView = findViewById<RecyclerView>(R.id.searchResultsRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        searchResultsAdapter = PoiListAdapter(
            poiItems = emptyList(),
            userLocation = locationViewModel.currentLocation.value,
            hasMoreRadius = navigationViewModel.hasMoreRadiusTiers.value,
            onLoadMoreClicked = { navigationViewModel.expandSearchRadius() },
            onAddPoiClicked = { poiItem ->
                val latLng = LatLng(poiItem.latLonPoint.latitude, poiItem.latLonPoint.longitude)
                val floor = FloorUtils.extractFloorFromPoi(poiItem)
                showSaveWaypointDialog(latLng, defaultName = poiItem.title, defaultFloor = floor)
            }
        ) { poiItem ->
            val latLng = LatLng(poiItem.latLonPoint.latitude, poiItem.latLonPoint.longitude)
            val floor = FloorUtils.extractFloorFromPoi(poiItem)
            val displayName = if (floor != null) {
                "${poiItem.title} (${FloorUtils.formatFloor(floor, this)})"
            } else {
                poiItem.title
            }
            navigationViewModel.setTarget(latLng, displayName)
            bottomSheetBehavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN
        }
        recyclerView.adapter = searchResultsAdapter
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    locationViewModel.isLocationAvailable.collectLatest { available ->
                        if (!available) {
                            showLocationDisabledSnackbar()
                        } else {
                            hideLocationDisabledSnackbar()
                            if (locationViewModel.currentLocation.value == null) {
                                locationViewModel.retryLocation()
                            }
                        }
                    }
                }

                launch {
                    locationViewModel.currentLocation.collectLatest { location ->
                        location?.let {
                            hideLocationDisabledSnackbar()
                            mapManager.updateMyLocation(it, locationViewModel.azimuth.value)
                            if (mapViewModel.isFollowMode.value) {
                                mapManager.animateToLocation(it)
                            }
                            navigationViewModel.updateLocation(it)

                            navigationViewModel.targetLocation.value?.let { target ->
                                radarView.updateTarget(it, target.first)
                                simpleCompassView.updateTarget(it, target.first)
                                mapManager.updateGuidanceLine(it, target.first, getThemeColor(com.google.android.material.R.attr.colorPrimary))
                            }
                        }
                    }
                }

                launch {
                    locationViewModel.azimuth.collectLatest { azimuth ->
                        radarView.setAzimuth(azimuth)
                        simpleCompassView.setAzimuth(azimuth)
                        mapManager.updateMyLocationAzimuth(azimuth)
                    }
                }

                launch {
                    mapViewModel.waypoints.collectLatest { waypoints ->
                        mapManager.updateWaypoints(waypoints) { showWaypointOptionsDialog(it) }
                    }
                }

                launch {
                    navigationViewModel.targetLocation.collectLatest { target ->
                        if (target != null) {
                            updateNavButtonsVisibility(navigationViewModel.currentRoute.value)
                            val startLoc = locationViewModel.currentLocation.value
                            val routeWaypoints = navigationViewModel.currentRoute.value?.waypoints?.map { LatLng(it.latitude, it.longitude) }
                            mapManager.setTargetLocation(target.first, target.second, startLocation = startLoc) {
                                showTargetMarkerOptionsDialog(target.first, target.second)
                            }
                            if (routeWaypoints != null && routeWaypoints.isNotEmpty()) {
                                mapManager.zoomToFitStartAndTargets(startLoc, routeWaypoints)
                            }
                            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                            // 动态为地图设置 Padding 避免导航卡片遮挡原生地图控件
                            val radarHeight = (220 * resources.displayMetrics.density).toInt()
                            mapManager.updatePadding(bottom = radarHeight)

                            // 确保导航卡片立即显示并设置初始目标与楼层
                            if (navigationStatusCard.visibility != View.VISIBLE) {
                                navigationStatusCard.alpha = 0f
                                navigationStatusCard.visibility = View.VISIBLE
                                navigationStatusCard.animate().alpha(1f).setDuration(300).start()
                            }
                            navTargetText.text = getString(R.string.nav_target_format, target.second)
                            navTargetText.isSelected = true
                            val currentFloor = navigationViewModel.currentRoute.value?.waypoints?.getOrNull(navigationViewModel.currentWaypointIndex.value)?.floor
                            val floorString = FloorUtils.formatFloor(currentFloor, this@MainActivity)
                            if (floorString != null) {
                                navFloorText.text = floorString
                                navFloorText.visibility = View.VISIBLE
                            } else {
                                navFloorText.visibility = View.GONE
                            }

                            // 立即使用最后已知位置绘制导向线并更新雷达/罗盘，避免等待下一个定位样本导致延迟
                            val lastLoc = locationViewModel.currentLocation.value
                            if (lastLoc != null) {
                                navigationViewModel.updateLocation(lastLoc)
                                radarView.updateTarget(lastLoc, target.first)
                                simpleCompassView.updateTarget(lastLoc, target.first)
                                mapManager.updateGuidanceLine(lastLoc, target.first, getThemeColor(com.google.android.material.R.attr.colorPrimary))
                            }
                        } else {
                            mapManager.clearAllNavigation()
                            radarView.clearTarget()
                            simpleCompassView.clearTarget()
                            navigationStatusCard.visibility = View.GONE
                            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                            val radarHeight = (220 * resources.displayMetrics.density).toInt()
                            mapManager.updatePadding(bottom = radarHeight)

                            com.growsnova.compassor.service.NavigationService.stop(this@MainActivity)
                        }
                    }
                }

                launch {
                    combine(
                        navigationViewModel.currentRoute,
                        navigationViewModel.currentWaypointIndex,
                        navigationViewModel.navStartLocation,
                        locationViewModel.currentLocation
                    ) { route, index, startLoc, userLoc ->
                        listOf(route, index, startLoc, userLoc)
                    }.collectLatest { state ->
                        val route = state[0] as? Route
                        val index = (state[1] as? Int) ?: 0
                        val startLoc = state[2] as? LatLng
                        val userLoc = state[3] as? LatLng

                        updateNavButtonsVisibility(route)
                        if (route != null) {
                            mapManager.drawRoute(
                                waypoints = route.waypoints,
                                currentIndex = index,
                                navStartLocation = startLoc,
                                userLocation = userLoc,
                                primaryColor = getThemeColor(com.google.android.material.R.attr.colorPrimary),
                                traveledColor = getThemeColor(com.google.android.material.R.attr.colorOutline)
                            )
                        } else {
                            mapManager.clearRoute()
                        }
                    }
                }

                launch {
                    navigationViewModel.navigationUpdate.collectLatest { update ->
                        update?.let { updateNavigationStatusUI(it) }
                    }
                }

                launch {
                    navigationViewModel.searchResults.collectLatest { results ->
                        if (results.isNotEmpty()) {
                            searchResultsAdapter.updateData(
                                results,
                                locationViewModel.currentLocation.value,
                                navigationViewModel.hasMoreRadiusTiers.value
                            )
                            bottomSheetBehavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
                        } else {
                            DialogUtils.showToast(this@MainActivity, getString(R.string.no_result))
                        }
                    }
                }

                launch {
                    navigationViewModel.errorFlow.collect { error ->
                        DialogUtils.showErrorToast(this@MainActivity, error)
                    }
                }
            }
        }
    }

    private fun updateNavButtonsVisibility(route: Route?) {
        if (route != null) {
            skipNavButton.visibility = View.VISIBLE
            prevNavButton.visibility = View.VISIBLE
            val index = navigationViewModel.currentWaypointIndex.value
            val canSkip = route.isLooping || index < route.waypoints.size - 1
            val canPrev = route.isLooping || index > 0
            skipNavButton.isEnabled = canSkip
            prevNavButton.isEnabled = canPrev
            skipNavButton.alpha = if (canSkip) 1.0f else 0.5f
            prevNavButton.alpha = if (canPrev) 1.0f else 0.5f
        } else {
            skipNavButton.visibility = View.GONE
            prevNavButton.visibility = View.GONE
        }
    }

    private fun updateNavigationStatusUI(update: NavigationManager.NavigationUpdate) {
        if (navigationStatusCard.visibility != View.VISIBLE) {
            navigationStatusCard.alpha = 0f
            navigationStatusCard.visibility = View.VISIBLE
            navigationStatusCard.animate().alpha(1f).setDuration(300).start()
        }
        navTargetText.text = getString(R.string.nav_target_format, update.targetName)
        navTargetText.isSelected = true
        
        val distanceStr = if (update.distance < 1000) "${update.distance.toInt()}m" else "%.1fkm".format(update.distance / 1000f)
        navDistanceText.text = distanceStr

        val floorString = FloorUtils.formatFloor(update.targetFloor, this)
        if (floorString != null) {
            navFloorText.text = floorString
            navFloorText.visibility = View.VISIBLE
        } else {
            navFloorText.visibility = View.GONE
        }

        updateNavButtonsVisibility(navigationViewModel.currentRoute.value)

        // Update foreground navigation notification & Wear OS DataLayer
        val azimuth = locationViewModel.azimuth.value
        val skinKey = navigationRepository.getSkinName()
        val target = navigationViewModel.targetLocation.value
        val myLoc = locationViewModel.currentLocation.value
        val bearing = if (target != null && myLoc != null) {
            val results = FloatArray(2)
            android.location.Location.distanceBetween(myLoc.latitude, myLoc.longitude, target.first.latitude, target.first.longitude, results)
            (results[1] + 360f) % 360f
        } else {
            0f
        }

        if (navigationViewModel.targetLocation.value != null) {
            com.growsnova.compassor.service.NavigationService.startOrUpdate(this, update.targetName, update.distance, azimuth)
        }
        wearDataSender.sendNavigationData(update.targetName, update.distance, bearing, skinKey)

        if (update.nextWaypointReached) {
            soundManager.playArrivalTone()
            DialogUtils.showToast(this, getString(R.string.next_waypoint_notification, update.targetName))
        }
        if (update.routeCompleted) {
            soundManager.playArrivalTone()
            DialogUtils.showToast(this, getString(R.string.route_completed))
        }
    }

    private fun handleNavigationIntent() {
        val route = intent.getSerializableExtraCompat<Route>("start_navigation_route")
        route?.let {
            mapView.postDelayed({ if (it.waypoints.isNotEmpty()) navigationViewModel.startRouteNavigation(it, locationViewModel.currentLocation.value) }, 1000)
        }
    }

    private fun showLocationDisabledSnackbar() {
        if (locationDisabledSnackbar == null) {
            val rootView = findViewById<View>(android.R.id.content)
            locationDisabledSnackbar = com.google.android.material.snackbar.Snackbar.make(
                rootView,
                R.string.location_disabled_hint,
                com.google.android.material.snackbar.Snackbar.LENGTH_INDEFINITE
            ).setAction(R.string.open_settings) {
                try {
                    startActivity(android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
        if (locationDisabledSnackbar?.isShown != true) {
            locationDisabledSnackbar?.show()
        }
    }

    private fun hideLocationDisabledSnackbar() {
        locationDisabledSnackbar?.dismiss()
        locationDisabledSnackbar = null
    }

    private fun checkAndRequestPermissions() {
        val required = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        val needed = required.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), AppConstants.LOCATION_PERMISSION_REQUEST_CODE)
        } else {
            setupAMapLocationStyle()
            if (locationViewModel.currentLocation.value == null) {
                locationViewModel.retryLocation()
            }
        }
    }

    private fun setupAMapLocationStyle() {
        if (!::aMap.isInitialized) return
        try {
            val transparentBitmap = android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
            aMap.myLocationStyle = com.amap.api.maps.model.MyLocationStyle().apply {
                myLocationType(com.amap.api.maps.model.MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE_NO_CENTER)
                myLocationIcon(BitmapDescriptorFactory.fromBitmap(transparentBitmap))
                strokeColor(android.graphics.Color.TRANSPARENT)
                radiusFillColor(android.graphics.Color.TRANSPARENT)
            }
            aMap.isMyLocationEnabled = true
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to setup location style", e)
        }
    }

    private fun getThemeColor(attr: Int): Int {
        val typedValue = android.util.TypedValue()
        if (theme.resolveAttribute(attr, typedValue, true)) {
            return if (typedValue.resourceId != 0) {
                ContextCompat.getColor(this, typedValue.resourceId)
            } else {
                typedValue.data
            }
        }
        return android.graphics.Color.BLACK
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == AppConstants.LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                setupAMapLocationStyle()
                if (locationViewModel.currentLocation.value == null) {
                    locationViewModel.retryLocation()
                }
            } else {
                DialogUtils.showErrorToast(this, getString(R.string.location_permission_denied))
            }
        }
    }

    private fun showTargetMarkerOptionsDialog(latLng: LatLng, name: String) {
        val options = arrayOf(getString(R.string.save_location), getString(R.string.share_location), getString(R.string.open_in_external_maps), getString(R.string.stop_navigation))
        DialogUtils.showOptionsDialog(this, name, options) { which ->
            when (which) {
                0 -> showSaveWaypointDialog(latLng, defaultName = name)
                1 -> ShareUtils.shareWaypointText(this, name, latLng.latitude, latLng.longitude)
                2 -> ShareUtils.openInMaps(this, name, latLng.latitude, latLng.longitude)
                3 -> navigationViewModel.stopNavigation()
            }
        }
    }

    private fun showMyLocationOptionsDialog() {
        val userLoc = locationViewModel.currentLocation.value ?: run {
            DialogUtils.showErrorToast(this, getString(R.string.location_unavailable))
            return
        }
        val options = arrayOf(getString(R.string.save_location), getString(R.string.share_location), getString(R.string.open_in_external_maps))
        navigationViewModel.reverseGeocode(userLoc) { name ->
            DialogUtils.showOptionsDialog(this, name, options) { which ->
                when (which) {
                    0 -> showSaveWaypointDialog(userLoc, defaultName = name)
                    1 -> ShareUtils.shareWaypointText(this, name, userLoc.latitude, userLoc.longitude)
                    2 -> ShareUtils.openInMaps(this, name, userLoc.latitude, userLoc.longitude)
                }
            }
        }
    }

    private fun showPoiOptionsDialog(name: String, latLng: LatLng) {
        val options = arrayOf(getString(R.string.set_destination), getString(R.string.save_location), getString(R.string.share_location), getString(R.string.open_in_external_maps))
        DialogUtils.showOptionsDialog(this, name, options) { which ->
            when (which) {
                0 -> navigationViewModel.setTarget(latLng, name)
                1 -> showSaveWaypointDialog(latLng, defaultName = name)
                2 -> ShareUtils.shareWaypointText(this, name, latLng.latitude, latLng.longitude)
                3 -> ShareUtils.openInMaps(this, name, latLng.latitude, latLng.longitude)
            }
        }
    }

    private fun showMapLongClickOptionsDialog(latLng: LatLng) {
        val options = arrayOf(getString(R.string.save_location), getString(R.string.set_destination), getString(R.string.open_in_external_maps))
        DialogUtils.showOptionsDialog(this, getString(R.string.select_action), options) { which ->
            when (which) {
                0 -> navigationViewModel.reverseGeocode(latLng) { name -> showSaveWaypointDialog(latLng, defaultName = name) }
                1 -> navigationViewModel.reverseGeocode(latLng) { name -> navigationViewModel.setTarget(latLng, name) }
                2 -> navigationViewModel.reverseGeocode(latLng) { name -> ShareUtils.openInMaps(this, name, latLng.latitude, latLng.longitude) }
            }
        }
    }

    private fun startCamera() {
        val photoFile = try {
            createImageFile()
        } catch (ex: Exception) {
            null
        }
        photoFile?.also {
            val photoURI = FileProvider.getUriForFile(this, "${packageName}.fileprovider", it)
            takePhotoLauncher.launch(photoURI)
        }
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir).apply {
            currentPhotoPath = absolutePath
        }
    }

    private fun showSaveWaypointDialog(latLng: LatLng, waypointToEdit: Waypoint? = null, defaultName: String? = null, defaultFloor: Int? = null) {
        val title = if (waypointToEdit == null) getString(R.string.save_location) else getString(R.string.waypoint_details)
        val view = layoutInflater.inflate(R.layout.dialog_waypoint_details, null)
        
        val nameEditText = view.findViewById<EditText>(R.id.nameEditText)
        val remarksEditText = view.findViewById<EditText>(R.id.remarksEditText)
        val floorEditText = view.findViewById<EditText>(R.id.floorEditText)
        val photoImageView = view.findViewById<ImageView>(R.id.waypointPhoto)
        val takePhotoButton = view.findViewById<MaterialButton>(R.id.takePhotoButton)
        val coordinatesText = view.findViewById<TextView>(R.id.coordinatesText)

        waypointPhotoImageView = photoImageView
        currentPhotoPath = waypointToEdit?.photoPath
        
        nameEditText.setText(waypointToEdit?.name ?: defaultName ?: "")
        remarksEditText.setText(waypointToEdit?.remarks ?: "")
        floorEditText.setText(waypointToEdit?.floor?.toString() ?: defaultFloor?.toString() ?: "")
        val wgs84 = CoordTransform.gcj02ToWgs84(latLng.latitude, latLng.longitude)
        coordinatesText.text = "Lat: %.6f, Lon: %.6f (WGS-84)".format(wgs84.first, wgs84.second)

        if (currentPhotoPath != null) {
            Glide.with(this).load(currentPhotoPath).centerCrop().into(photoImageView)
        }

        takePhotoButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(view)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = nameEditText.text.toString().trim()
                val remarks = remarksEditText.text.toString().trim()
                val floor = floorEditText.text.toString().trim().toIntOrNull()
                if (name.isNotEmpty()) {
                    if (waypointToEdit == null) {
                        addWaypoint(latLng, name, currentPhotoPath, remarks, floor)
                    } else {
                        waypointToEdit.name = name
                        waypointToEdit.photoPath = currentPhotoPath
                        waypointToEdit.remarks = remarks
                        waypointToEdit.floor = floor
                        updateWaypoint(waypointToEdit, name, latLng)
                    }
                } else {
                    DialogUtils.showErrorToast(this@MainActivity, getString(R.string.waypoint_name_empty))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun addWaypoint(latLng: LatLng, name: String) {
        addWaypoint(latLng, name, null, null, null)
    }

    private fun addWaypoint(latLng: LatLng, name: String, photoPath: String?, remarks: String?, floor: Int?) {
        val existing = mapViewModel.waypoints.value.find {
            val dist = FloatArray(1)
            android.location.Location.distanceBetween(latLng.latitude, latLng.longitude, it.latitude, it.longitude, dist)
            (it.name.equals(name, ignoreCase = true) || it.name.contains(name, ignoreCase = true)) && dist[0] < 10
        }

        if (existing != null) {
            DialogUtils.showConfirmationDialog(this, getString(R.string.update_waypoint_title),
                getString(R.string.update_waypoint_message, existing.name, name),
                onPositive = { 
                    existing.photoPath = photoPath
                    existing.remarks = remarks
                    existing.floor = floor
                    updateWaypoint(existing, name, latLng) 
                }
            )
        } else {
            navigationViewModel.addWaypoint(Waypoint(name = name, latitude = latLng.latitude, longitude = latLng.longitude, photoPath = photoPath, remarks = remarks, floor = floor))
            DialogUtils.showSuccessToast(this, getString(R.string.waypoint_saved, name))
        }
    }

    private fun updateWaypoint(waypoint: Waypoint, newName: String, newLatLng: LatLng? = null) {
        waypoint.name = newName
        newLatLng?.let {
            waypoint.latitude = it.latitude
            waypoint.longitude = it.longitude
        }
        navigationViewModel.updateWaypoint(waypoint)
        DialogUtils.showSuccessToast(this, getString(R.string.waypoint_updated))
    }

    override fun onNavigationItemSelected(item: android.view.MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_manage_waypoints -> showWaypointManagementDialog()
            R.id.nav_manage_routes -> showRouteManagementDialog()
            R.id.nav_change_skin -> showSkinSelectionDialog()
            R.id.nav_settings -> showSettingsDialog()
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun showWaypointManagementDialog() {
        val waypoints = mapViewModel.waypoints.value
        val displayInfo = waypoints.map { waypoint ->
            val routes = navigationViewModel.routes.value.filter { it.waypoints.any { w -> w.id == waypoint.id } }
            waypoint.name + if (routes.isNotEmpty()) getString(R.string.in_routes, routes.joinToString { it.name }) else ""
        }
        val combinedOptions = displayInfo + listOf(getString(R.string.export_waypoints), getString(R.string.import_waypoints))

        DialogUtils.showOptionsDialog(this, getString(R.string.manage_waypoints_title), combinedOptions.toTypedArray()) { which ->
            if (which < waypoints.size) {
                showWaypointOptionsDialog(waypoints[which])
            } else if (which == waypoints.size) {
                // Export waypoints option
                showExportWaypointsFormatDialog()
            } else {
                // Import waypoints option
                importWaypointsLauncher.launch(arrayOf("application/json", "application/xml", "text/xml", "*/*"))
            }
        }
    }

    private var exportWaypointsType: String? = null
    private val exportWaypointsLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        uri?.let { exportWaypointsToFile(it) }
    }

    private val importWaypointsLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importWaypointsFromFile(it) }
    }

    private fun showExportWaypointsFormatDialog() {
        val formats = arrayOf(getString(R.string.export_geojson), getString(R.string.export_kml))
        DialogUtils.showOptionsDialog(this, getString(R.string.export_waypoints), formats) { which ->
            when (which) {
                0 -> {
                    exportWaypointsType = "geojson"
                    exportWaypointsLauncher.launch("compassor_waypoints.geojson")
                }
                1 -> {
                    exportWaypointsType = "kml"
                    exportWaypointsLauncher.launch("compassor_waypoints.kml")
                }
            }
        }
    }

    private fun exportWaypointsToFile(uri: android.net.Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                val waypoints = mapViewModel.waypoints.value
                val content = if (exportWaypointsType == "kml") {
                    KmlUtils.exportWaypointsToKml(waypoints)
                } else {
                    GeoJsonUtils.exportWaypointsToGeoJson(waypoints)
                }
                outputStream.write(content.toByteArray(Charsets.UTF_8))
                DialogUtils.showSuccessToast(this, getString(R.string.waypoints_exported))
            }
        } catch (e: Exception) {
            DialogUtils.showErrorToast(this, getString(R.string.import_failed))
        }
    }

    private fun importWaypointsFromFile(uri: android.net.Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val content = inputStream.reader().readText()
                val imported = if (content.trim().startsWith("<?xml") || content.trim().startsWith("<kml")) {
                    KmlUtils.importKmlToWaypoints(content)
                } else {
                    GeoJsonUtils.importGeoJsonToWaypoints(content)
                }
                if (imported.isNotEmpty()) {
                    imported.forEach { navigationViewModel.addWaypoint(it) }
                    DialogUtils.showSuccessToast(this, getString(R.string.waypoints_imported, imported.size))
                } else {
                    DialogUtils.showErrorToast(this, getString(R.string.import_failed))
                }
            }
        } catch (e: Exception) {
            DialogUtils.showErrorToast(this, getString(R.string.import_failed))
        }
    }

    private fun showWaypointOptionsDialog(waypoint: Waypoint) {
        val view = layoutInflater.inflate(R.layout.dialog_waypoint_options, null)

        view.findViewById<TextView>(R.id.waypointName).text = waypoint.name

        // 楼层
        val floorView = view.findViewById<TextView>(R.id.waypointFloor)
        val floorText = FloorUtils.formatFloor(waypoint.floor, this)
        if (floorText != null) {
            floorView.text = floorText
            floorView.visibility = View.VISIBLE
        }

        // 照片
        if (!waypoint.photoPath.isNullOrEmpty()) {
            val photoView = view.findViewById<ImageView>(R.id.waypointPhoto)
            Glide.with(this).load(waypoint.photoPath).centerCrop().into(photoView)
            view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.photoCard).visibility = View.VISIBLE
        }

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setView(view).create()

        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSetDestination).apply {
            applyTouchScale()
            setOnClickListener {
                dialog.dismiss()
                navigationViewModel.setTarget(LatLng(waypoint.latitude, waypoint.longitude), waypoint.name)
            }
        }
        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnOpenInMaps).apply {
            applyTouchScale()
            setOnClickListener {
                dialog.dismiss()
                ShareUtils.openInMaps(this@MainActivity, waypoint.name, waypoint.latitude, waypoint.longitude)
            }
        }
        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnViewDetails).apply {
            applyTouchScale()
            setOnClickListener {
                dialog.dismiss()
                showSaveWaypointDialog(LatLng(waypoint.latitude, waypoint.longitude), waypoint)
            }
        }
        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnDelete).apply {
            applyTouchScale()
            setOnClickListener {
                dialog.dismiss()
                confirmDeleteWaypoint(waypoint)
            }
        }

        // Add options button for opening in external maps from waypoint card
        view.findViewById<View>(R.id.waypointName).setOnClickListener {
            val options = arrayOf(getString(R.string.open_in_external_maps), getString(R.string.share_location))
            DialogUtils.showOptionsDialog(this, waypoint.name, options) { which ->
                when (which) {
                    0 -> ShareUtils.openInMaps(this, waypoint.name, waypoint.latitude, waypoint.longitude)
                    1 -> ShareUtils.shareWaypointText(this, waypoint.name, waypoint.latitude, waypoint.longitude)
                }
            }
        }

        dialog.show()
    }

    private fun confirmDeleteWaypoint(waypoint: Waypoint) {
        val routes = navigationViewModel.routes.value.filter { it.waypoints.any { w -> w.id == waypoint.id } }
        if (routes.isNotEmpty()) {
            DialogUtils.showConfirmationDialog(this, getString(R.string.confirm_delete),
                getString(R.string.confirm_delete_waypoint_message, routes.joinToString { it.name }),
                onPositive = { navigationViewModel.deleteWaypoint(waypoint) }
            )
        } else {
            navigationViewModel.deleteWaypoint(waypoint)
        }
    }

    private fun showRouteManagementDialog() {
        val routes = navigationViewModel.routes.value
        val options = arrayOf(getString(R.string.create_route), getString(R.string.import_route))

        val dialogView = layoutInflater.inflate(R.layout.dialog_input, null)
        // Instead of plain list dialog, let's offer list of routes plus Import and Create buttons
        val routeNames = routes.map { it.name }.toTypedArray()
        if (routeNames.isEmpty()) {
            DialogUtils.showOptionsDialog(this, getString(R.string.manage_routes), options) { which ->
                when (which) {
                    0 -> launchCreateRoute()
                    1 -> importRouteLauncher.launch(arrayOf("application/json", "*/*"))
                }
            }
        } else {
            val combinedOptions = routeNames + arrayOf(getString(R.string.import_route))
            DialogUtils.showListDialog(this, getString(R.string.manage_routes), combinedOptions,
                onItemSelected = { which ->
                    if (which < routes.size) {
                        showRouteOptionsDialog(routes[which])
                    } else {
                        importRouteLauncher.launch(arrayOf("application/json", "*/*"))
                    }
                },
                positiveButtonText = R.string.create_route,
                onPositive = { launchCreateRoute() }
            )
        }
    }

    private fun showRouteOptionsDialog(route: Route) {
        val options = arrayOf(getString(R.string.start_navigation), getString(R.string.edit_route), getString(R.string.export_route), getString(R.string.delete_route))
        DialogUtils.showOptionsDialog(this, route.name, options) { which ->
            when (which) {
                0 -> navigationViewModel.startRouteNavigation(route, locationViewModel.currentLocation.value)
                1 -> launchEditRoute(route)
                2 -> {
                    routeToExport = route
                    exportRouteLauncher.launch("${route.name}.json")
                }
                3 -> navigationViewModel.deleteRoute(route)
            }
        }
    }

    private fun launchCreateRoute() {
        val intent = android.content.Intent(this, CreateRouteActivity::class.java).apply {
            putExtra("waypoints_wrapper", WaypointListWrapper(ArrayList(mapViewModel.waypoints.value)))
            locationViewModel.currentLocation.value?.let { putExtra("current_latlng", it) }
        }
        createRouteLauncher.launch(intent)
    }

    private fun launchEditRoute(route: Route) {
        val intent = android.content.Intent(this, CreateRouteActivity::class.java).apply {
            putExtra("route_to_edit", route)
            putExtra("waypoints_wrapper", WaypointListWrapper(ArrayList(mapViewModel.waypoints.value)))
            locationViewModel.currentLocation.value?.let { putExtra("current_latlng", it) }
        }
        editRouteLauncher.launch(intent)
    }

    private fun handleCreateRouteResult(data: android.content.Intent) {
        val route = data.getSerializableExtraCompat<Route>("new_route") ?: return
        val startNav = data.getBooleanExtra("start_navigation", false)
        navigationViewModel.saveNewRoute(route, route.waypoints, startNav)
    }

    private fun handleEditRouteResult(data: android.content.Intent) {
        val route = data.getSerializableExtraCompat<Route>("new_route") ?: return
        navigationViewModel.updateRoute(route, route.waypoints)
    }

    private fun showSkinSelectionDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_skin_selection, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.skinRecyclerView)
        val btnImport = dialogView.findViewById<MaterialButton>(R.id.btnImportSkin)

        recyclerView.layoutManager = LinearLayoutManager(this)

        val currentKey = navigationRepository.getSkinName()
        var selectedTheme: SkinTheme? = null

        val alertDialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton(R.string.confirm) { _, _ ->
                selectedTheme?.let {
                    navigationRepository.saveSkinName(it.key)
                    applySkin(it.key)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()

        val adapter = SkinAdapter(DefaultSkins.themes, currentKey) { theme ->
            selectedTheme = theme
            navigationRepository.saveSkinName(theme.key)
            applySkin(theme.key)
        }
        recyclerView.adapter = adapter

        btnImport.setOnClickListener {
            alertDialog.dismiss()
            skinPickerLauncher.launch(arrayOf("application/json"))
        }

        alertDialog.show()
    }

    private fun applySkin(skinName: String) {
        val skin = DefaultSkins.getSkinByName(skinName, this)
        radarView.setSkin(skin)
        simpleCompassView.setSkin(skin)

        // 1. Toolbar Frame
        toolbar.setBackgroundColor(skin.backgroundColor)
        toolbar.setTitleTextColor(skin.distanceTextColor)

        // 2. Navigation Status Card
        navigationStatusCard.setCardBackgroundColor(skin.backgroundColor)
        navigationStatusCard.strokeColor = skin.compassRingColor
        navTargetText.setTextColor(skin.distanceTextColor)
        navDistanceText.setTextColor(skin.infoTextColor)

        // 3. Navigation Drawer Layout, Menu & Selection Highlight Colors
        navigationView.setBackgroundColor(skin.backgroundColor)

        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(android.R.attr.state_pressed),
            intArrayOf()
        )

        navigationView.itemBackground = null

        val textColors = intArrayOf(skin.compassRingColor, skin.compassRingColor, skin.distanceTextColor)
        val iconColors = intArrayOf(skin.compassRingColor, skin.compassRingColor, skin.infoTextColor)

        navigationView.itemTextColor = android.content.res.ColorStateList(states, textColors)
        navigationView.itemIconTintList = android.content.res.ColorStateList(states, iconColors)

        // 4. Navigation Drawer Header
        if (navigationView.headerCount > 0) {
            val headerView = navigationView.getHeaderView(0)
            headerView.setBackgroundColor(skin.backgroundColor)
            headerView.findViewById<TextView>(R.id.appName)?.setTextColor(skin.distanceTextColor)
            headerView.findViewById<TextView>(R.id.appSubtitle)?.setTextColor(skin.infoTextColor)
        }

        // 5. Map Guidance Line & Night Mode Style
        val currentTarget = navigationViewModel.targetLocation.value
        val lastLoc = locationViewModel.currentLocation.value
        if (currentTarget != null && lastLoc != null) {
            mapManager.updateGuidanceLine(lastLoc, currentTarget.first, skin.targetColor)
        }

        val isNightMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        mapManager.applyMapStyle(isNightMode)

        // 6. Sync theme skin to Wear OS watch
        wearDataSender.sendSkinKey(skinName)
    }

    private fun showSettingsDialog() {
        val currentMode = navigationRepository.getThemeMode().let { if (it == -1) AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM else it }
        val themeOptions = arrayOf(getString(R.string.system_default), getString(R.string.light_mode), getString(R.string.dark_mode))
        var selectedMode = currentMode
        val currentSelection = when (currentMode) {
            AppCompatDelegate.MODE_NIGHT_NO -> 1
            AppCompatDelegate.MODE_NIGHT_YES -> 2
            else -> 0
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.theme_settings)
            .setSingleChoiceItems(themeOptions, currentSelection) { _, which ->
                selectedMode = when (which) {
                    1 -> AppCompatDelegate.MODE_NIGHT_NO
                    2 -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
            }
            .setPositiveButton(R.string.save) { dialog, _ ->
                navigationRepository.saveThemeMode(selectedMode)
                dialog.dismiss()
                if (AppCompatDelegate.getDefaultNightMode() != selectedMode) {
                    window.decorView.post {
                        AppCompatDelegate.setDefaultNightMode(selectedMode)
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun exportRouteToFile(route: Route, uri: android.net.Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                val json = RouteJsonUtils.exportRouteToJson(route)
                outputStream.write(json.toByteArray(Charsets.UTF_8))
                DialogUtils.showSuccessToast(this, getString(R.string.route_exported))
            }
        } catch (e: Exception) {
            DialogUtils.showErrorToast(this, getString(R.string.skin_import_failed))
        }
    }

    private fun importRouteFromFile(uri: android.net.Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val jsonStr = inputStream.reader().readText()
                val imported = RouteJsonUtils.importRouteFromJson(jsonStr)
                if (imported != null) {
                    val (route, waypoints) = imported
                    navigationViewModel.saveNewRoute(route, waypoints, startNav = false)
                    DialogUtils.showConfirmationDialog(
                        this,
                        getString(R.string.route_imported, route.name),
                        getString(R.string.start_navigation) + "?",
                        onPositive = {
                            navigationViewModel.startRouteNavigation(route, locationViewModel.currentLocation.value)
                        }
                    )
                } else {
                    DialogUtils.showErrorToast(this, getString(R.string.route_import_failed))
                }
            }
        } catch (e: Exception) {
            DialogUtils.showErrorToast(this, getString(R.string.route_import_failed))
        }
    }

    private fun importSkinFromFile(uri: android.net.Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val skin = com.google.gson.Gson().fromJson(inputStream.reader().readText(), RadarSkin::class.java)
                radarView.setSkin(skin)
                simpleCompassView.setSkin(skin)
                DialogUtils.showSuccessToast(this, getString(R.string.skin_imported))
            }
        } catch (e: Exception) { DialogUtils.showErrorToast(this, getString(R.string.skin_import_failed)) }
    }

    private fun flipRadarCard() {
        val root = radarContent
        val front = radarView
        val back = simpleCompassView
        root.cameraDistance = 8000 * resources.displayMetrics.density
        val outAnim = android.animation.ObjectAnimator.ofFloat(root, "rotationY", if (isRadarFlipped) 180f else 0f, 90f)
        val inAnim = android.animation.ObjectAnimator.ofFloat(root, "rotationY", if (isRadarFlipped) -90f else 270f, if (isRadarFlipped) 0f else 180f)
        outAnim.duration = 150
        inAnim.duration = 150
        outAnim.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                if (isRadarFlipped) {
                    back.visibility = View.GONE; front.visibility = View.VISIBLE; root.rotationY = 0f; front.scaleX = 1f
                } else {
                    front.visibility = View.GONE; back.visibility = View.VISIBLE; root.rotationY = 180f; back.scaleX = -1f
                }
                isRadarFlipped = !isRadarFlipped; inAnim.start()
            }
        })
        outAnim.start()
    }

    private fun showSearchDialog() {
        navigationViewModel.loadRecentSearches()

        val view = layoutInflater.inflate(R.layout.dialog_search_with_history, null)
        val editText = view.findViewById<EditText>(R.id.searchEditText)
        val historyRecyclerView = view.findViewById<RecyclerView>(R.id.historyRecyclerView)
        val clearHistoryButton = view.findViewById<Button>(R.id.clearHistoryButton)
        historyRecyclerView.layoutManager = LinearLayoutManager(this@MainActivity)

        val dialog = MaterialAlertDialogBuilder(this@MainActivity)
            .setTitle(R.string.search_location).setView(view)
            .setPositiveButton(R.string.search, null).setNegativeButton(R.string.cancel, null).create()

        val historyAdapter = SearchHistoryAdapter(navigationViewModel.recentSearches.value.toMutableList(),
            onItemClick = { editText.setText(it.query); performSearch(it.query, dialog) },
            onDeleteClick = { navigationViewModel.deleteSearchHistory(it.id) }
        )
        historyRecyclerView.adapter = historyAdapter

        // Update adapter when search history changes
        lifecycleScope.launch {
            navigationViewModel.recentSearches.collectLatest {
                historyAdapter.searchHistories = it
                historyAdapter.notifyDataSetChanged()
                historyRecyclerView.visibility = if (it.isEmpty()) View.GONE else View.VISIBLE
                clearHistoryButton.visibility = if (it.isEmpty()) View.GONE else View.VISIBLE
            }
        }

        clearHistoryButton.setOnClickListener { navigationViewModel.clearSearchHistory() }
        dialog.show()
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener { performSearch(editText.text.toString().trim(), dialog) }
        editText.setOnEditorActionListener { _, id, _ -> if (id == EditorInfo.IME_ACTION_SEARCH) { performSearch(editText.text.toString().trim(), dialog); true } else false }
    }

    private fun performSearch(query: String, dialog: androidx.appcompat.app.AlertDialog) {
        if (query.isNotEmpty()) {
            navigationViewModel.onSearchPerformed(query)
            navigationViewModel.searchPOI(query, locationViewModel.currentLocation.value)
            dialog.dismiss()
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean { menuInflater.inflate(R.menu.main_menu, menu); return true }
    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean = when (item.itemId) {
        R.id.action_reset_bearing -> { mapManager.resetBearing(); true }
        R.id.action_search -> { showSearchDialog(); true }; else -> super.onOptionsItemSelected(item)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // 动态刷新 UI 主题色与皮肤，无需销毁重启 Activity
        applySkin(navigationRepository.getSkinName())
        setupAMapLocationStyle()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        val hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasPermission && locationViewModel.currentLocation.value == null) {
            locationViewModel.retryLocation()
        }
    }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onDestroy() { super.onDestroy(); mapView.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState) }
}
