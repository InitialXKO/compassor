package com.growsnova.compassor

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
import com.amap.api.maps.model.*
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.geocoder.RegeocodeResult
import com.amap.api.services.poisearch.PoiResult
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationView
import com.growsnova.compassor.data.repository.RouteRepository
import com.growsnova.compassor.data.repository.SearchRepository
import com.growsnova.compassor.data.repository.WaypointRepository
import com.growsnova.compassor.manager.*
import com.growsnova.compassor.viewmodel.LocationViewModel
import com.growsnova.compassor.viewmodel.MapViewModel
import com.growsnova.compassor.viewmodel.NavigationViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private val locationViewModel: LocationViewModel by viewModels()
    private val navigationViewModel: NavigationViewModel by viewModels()
    private val mapViewModel: MapViewModel by viewModels()

    @Inject lateinit var mapManager: MapManager
    @Inject lateinit var waypointRepository: WaypointRepository
    @Inject lateinit var routeRepository: RouteRepository
    @Inject lateinit var searchRepository: SearchRepository
    @Inject lateinit var locationProvider: LocationProvider
    @Inject lateinit var searchManager: SearchManager

    private lateinit var mapView: MapView
    private lateinit var aMap: AMap
    private lateinit var radarView: RadarCompassView
    private lateinit var simpleCompassView: SimpleCompassView
    private lateinit var radarContent: View
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toolbar: androidx.appcompat.widget.Toolbar

    private lateinit var navigationStatusCard: MaterialCardView
    private lateinit var navTargetText: android.widget.TextView
    private lateinit var navDistanceText: android.widget.TextView
    private lateinit var stopNavButton: MaterialButton
    private lateinit var skipNavButton: MaterialButton
    private lateinit var prevNavButton: MaterialButton

    private var isRadarFlipped = false
    private var isFollowMode = true

    private val createRouteLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val route = result.data?.getSerializableExtraCompat<Route>("created_route")
            val startNav = result.data?.getBooleanExtra("start_navigation", false) ?: false
            route?.let {
                if (startNav) navigationViewModel.startNavigation(it)
            }
        }
    }

    private val editRouteLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val route = result.data?.getSerializableExtraCompat<Route>("updated_route")
            val startNav = result.data?.getBooleanExtra("start_navigation", false) ?: false
            route?.let {
                if (startNav) navigationViewModel.startNavigation(it)
            }
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
    }

    private fun initViews(savedInstanceState: Bundle?) {
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
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

        mapView = findViewById(R.id.mapView)
        radarView = findViewById(R.id.radarView)
        simpleCompassView = findViewById(R.id.simpleCompassView)
        radarContent = findViewById(R.id.radarContent)
        
        radarContent.setOnClickListener { flipRadarCard() }

        navigationStatusCard = findViewById(R.id.navigationStatusCard)
        navTargetText = findViewById(R.id.navTargetText)
        navDistanceText = findViewById(R.id.navDistanceText)
        stopNavButton = findViewById(R.id.stopNavButton)
        skipNavButton = findViewById(R.id.skipNavButton)
        prevNavButton = findViewById(R.id.prevNavButton)
        
        stopNavButton.applyTouchScale()
        stopNavButton.setOnClickListener { navigationViewModel.stopNavigation() }
        skipNavButton.applyTouchScale()
        skipNavButton.setOnClickListener { navigationViewModel.nextWaypoint() }
        prevNavButton.applyTouchScale()
        prevNavButton.setOnClickListener { navigationViewModel.previousWaypoint() }

        mapView.onCreate(savedInstanceState)
        aMap = mapView.map
        mapManager.initialize(aMap)

        aMap.setLocationSource(object : LocationSource {
            override fun activate(listener: LocationSource.OnLocationChangedListener) {
                lifecycleScope.launch {
                    locationViewModel.currentLocation.collectLatest { location ->
                        location?.let { listener.onLocationChanged(it) }
                    }
                }
            }
            override fun deactivate() {}
        })

        aMap.uiSettings.isZoomControlsEnabled = true
        aMap.uiSettings.isCompassEnabled = true
        aMap.uiSettings.isMyLocationButtonEnabled = true
        aMap.isMyLocationEnabled = true
        aMap.mapType = AMap.MAP_TYPE_NORMAL

        aMap.setOnMarkerClickListener { marker ->
            val waypoint = mapViewModel.allWaypoints.value.find {
                it.latitude == marker.position.latitude && it.longitude == marker.position.longitude
            }
            waypoint?.let { showWaypointOptionsDialog(it) }
            true
        }

        aMap.setOnMapLongClickListener { latLng -> showMapLongClickOptionsDialog(latLng) }
        aMap.setOnMapTouchListener { event ->
            if (event.action == MotionEvent.ACTION_DOWN) isFollowMode = false
        }

        aMap.setOnCameraChangeListener(object : AMap.OnCameraChangeListener {
            override fun onCameraChange(p0: CameraPosition?) {}
            override fun onCameraChangeFinish(position: CameraPosition?) {
                position?.target?.let { target ->
                    locationViewModel.currentLocation.value?.let { loc ->
                        val results = FloatArray(1)
                        Location.distanceBetween(loc.latitude, loc.longitude, target.latitude, target.longitude, results)
                        if (results[0] < AppConstants.FOLLOW_MODE_THRESHOLD_METERS) isFollowMode = true
                    }
                }
            }
        })
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    locationViewModel.currentLocation.collectLatest { location ->
                        location?.let { handleLocationUpdate(it) }
                    }
                }
                launch {
                    locationViewModel.azimuth.collectLatest { azimuth ->
                        radarView.updateAzimuth(azimuth)
                        simpleCompassView.updateAzimuth(azimuth)
                    }
                }
                launch {
                    navigationViewModel.targetWaypoint.collectLatest { waypoint ->
                        updateNavigationUI(waypoint)
                    }
                }
                launch {
                    mapViewModel.allWaypoints.collectLatest { waypoints ->
                        mapManager.updateWaypoints(waypoints)
                    }
                }
                launch {
                    navigationViewModel.currentRoute.collectLatest { route ->
                        route?.let { mapManager.drawRoute(it.waypoints) } ?: mapManager.clearRoute()
                    }
                }
            }
        }
    }

    private fun handleLocationUpdate(location: Location) {
        val latLng = LatLng(location.latitude, location.longitude)
        if (isFollowMode) {
            mapManager.moveCamera(latLng)
        }
        
        navigationViewModel.targetWaypoint.value?.let { target ->
            val targetLatLng = LatLng(target.latitude, target.longitude)
            radarView.updateTarget(latLng, targetLatLng)
            simpleCompassView.updateTarget(latLng, targetLatLng)

            val distance = FloatArray(1)
            Location.distanceBetween(location.latitude, location.longitude, target.latitude, target.longitude, distance)

            val dist = distance[0]
            navDistanceText.text = formatDistance(dist)

            if (dist < AppConstants.ARRIVAL_RADIUS_METERS) {
                if (navigationViewModel.currentRoute.value != null) {
                    navigationViewModel.nextWaypoint()
                    DialogUtils.showSuccessToast(this, getString(R.string.arrival_notification, target.name))
                }
            }
        }
    }

    private fun updateNavigationUI(target: Waypoint?) {
        if (target != null) {
            navigationStatusCard.visibility = View.VISIBLE
            navTargetText.text = getString(R.string.nav_target_format, target.name)

            val location = locationViewModel.currentLocation.value
            if (location != null) {
                val distance = FloatArray(1)
                Location.distanceBetween(location.latitude, location.longitude, target.latitude, target.longitude, distance)
                navDistanceText.text = formatDistance(distance[0])
            }
        } else {
            navigationStatusCard.visibility = View.GONE
        }
    }

    private fun formatDistance(distance: Float): String {
        return if (distance < 1000) "${distance.toInt()}m" else "%.1fkm".format(distance / 1000f)
    }

    private fun flipRadarCard() {
        val visibleView = if (!isRadarFlipped) radarView else simpleCompassView
        val hiddenView = if (!isRadarFlipped) simpleCompassView else radarView
        
        visibleView.animate().rotationY(90f).setDuration(150).withEndAction {
            visibleView.visibility = View.GONE
            hiddenView.visibility = View.VISIBLE
            hiddenView.rotationY = -90f
            hiddenView.animate().rotationY(0f).setDuration(150).start()
        }.start()
        isRadarFlipped = !isRadarFlipped
    }

    private fun handleNavigationIntent() {
        val route = intent.getSerializableExtraCompat<Route>("start_navigation_route")
        route?.let { navigationViewModel.startNavigation(it) }
    }

    private fun checkAndRequestPermissions() {
        val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        val needed = permissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), AppConstants.LOCATION_PERMISSION_REQUEST_CODE)
        } else {
            locationViewModel.startUpdates()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == AppConstants.LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                locationViewModel.startUpdates()
            } else {
                DialogUtils.showErrorToast(this, getString(R.string.location_permission_denied))
            }
        }
    }

    override fun onNavigationItemSelected(item: android.view.MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_search -> showSearchDialog()
            R.id.nav_save_location -> {
                locationViewModel.currentLocation.value?.let { loc ->
                    searchManager.reverseGeocode(LatLonPoint(loc.latitude, loc.longitude), object : SearchManager.GeocodeListener {
                        override fun onRegeocodeSearched(result: RegeocodeResult?, errorCode: Int) {
                            val name = result?.regeocodeAddress?.formatAddress ?: "我的位置"
                            showSaveWaypointDialog(LatLng(loc.latitude, loc.longitude), name)
                        }
                    })
                }
            }
            R.id.nav_manage_waypoints -> showWaypointManagementDialog()
            R.id.nav_manage_routes -> showRouteManagementDialog()
            R.id.nav_change_skin -> showSkinSelectionDialog()
            R.id.nav_settings -> showSettingsDialog()
        }
        drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
        return true
    }

    private fun showSearchDialog() {
        lifecycleScope.launch {
            val history = searchRepository.getSearchHistoryFlow().first()
            val view = layoutInflater.inflate(R.layout.dialog_search_with_history, null)
            val editText = view.findViewById<EditText>(R.id.searchEditText)
            val historyRecyclerView = view.findViewById<RecyclerView>(R.id.historyRecyclerView)
            val clearHistoryButton = view.findViewById<Button>(R.id.clearHistoryButton)

            clearHistoryButton.applyTouchScale()
            historyRecyclerView.layoutManager = LinearLayoutManager(this@MainActivity)

            val performSearchAction = { keyword: String, dialog: androidx.appcompat.app.AlertDialog ->
                if (keyword.isNotEmpty()) {
                    searchPOI(keyword)
                    lifecycleScope.launch { searchRepository.insertSearchHistory(keyword) }
                    dialog.dismiss()
                }
            }

            val dialog = MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle(getString(R.string.search_location))
                .setView(view)
                .setPositiveButton(getString(R.string.search), null)
                .setNegativeButton(R.string.cancel, null)
                .create()

            val historyAdapter = SearchHistoryAdapter(history.toMutableList(),
                onItemClick = { h -> performSearchAction(h.query, dialog) },
                onDeleteClick = { h ->
                    lifecycleScope.launch {
                        searchRepository.deleteSearchHistory(h.id)
                        val updated = searchRepository.getSearchHistoryFlow().first()
                        (historyRecyclerView.adapter as? SearchHistoryAdapter)?.updateData(updated)
                    }
                }
            )
            historyRecyclerView.adapter = historyAdapter

            if (history.isEmpty()) {
                historyRecyclerView.visibility = View.GONE
                clearHistoryButton.visibility = View.GONE
            }

            clearHistoryButton.setOnClickListener {
                lifecycleScope.launch {
                    searchRepository.clearSearchHistory()
                    (historyRecyclerView.adapter as? SearchHistoryAdapter)?.updateData(emptyList())
                    historyRecyclerView.visibility = View.GONE
                    clearHistoryButton.visibility = View.GONE
                }
            }

            editText.requestFocus()
            dialog.setOnShowListener {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
            }
            dialog.show()
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                performSearchAction(editText.text.toString().trim(), dialog)
            }
        }
    }

    private fun searchPOI(keyword: String) {
        val location = locationViewModel.currentLocation.value
        val point = location?.let { LatLonPoint(it.latitude, it.longitude) }
        searchManager.search(keyword, nearbyPoint = point, listener = object : SearchManager.SearchListener {
            override fun onPoiSearched(result: PoiResult?, errorCode: Int) {
                if (errorCode == 1000 && result != null && result.pois.isNotEmpty()) {
                    val poi = result.pois[0]
                    val latLng = LatLng(poi.latLonPoint.latitude, poi.latLonPoint.longitude)
                    navigationViewModel.setTarget(Waypoint(name = poi.title, latitude = latLng.latitude, longitude = latLng.longitude))
                    mapManager.moveCamera(latLng)
                } else {
                    DialogUtils.showErrorToast(this@MainActivity, getString(R.string.no_result))
                }
            }
        })
    }

    private fun showMapLongClickOptionsDialog(latLng: LatLng) {
        val options = arrayOf(getString(R.string.save_location), getString(R.string.set_destination))
        DialogUtils.showListDialog(this, getString(R.string.quick_action), options, { which ->
            searchManager.reverseGeocode(LatLonPoint(latLng.latitude, latLng.longitude), object : SearchManager.GeocodeListener {
                override fun onRegeocodeSearched(result: RegeocodeResult?, errorCode: Int) {
                    val name = result?.regeocodeAddress?.formatAddress ?: "未知地点"
                    when (which) {
                        0 -> showSaveWaypointDialog(latLng, name)
                        1 -> navigationViewModel.setTarget(Waypoint(name = name, latitude = latLng.latitude, longitude = latLng.longitude))
                    }
                }
            })
        })
    }

    private fun showSaveWaypointDialog(latLng: LatLng, defaultName: String) {
        DialogUtils.showInputDialog(this, getString(R.string.save_location), getString(R.string.waypoint_name_hint), defaultName, { name ->
            lifecycleScope.launch {
                waypointRepository.insertWaypoint(Waypoint(name = name, latitude = latLng.latitude, longitude = latLng.longitude))
                DialogUtils.showSuccessToast(this@MainActivity, getString(R.string.waypoint_saved, name))
            }
        })
    }

    private fun showWaypointOptionsDialog(waypoint: Waypoint) {
        val options = arrayOf(getString(R.string.set_destination), getString(R.string.edit), getString(R.string.delete))
        DialogUtils.showListDialog(this, waypoint.name, options, { which ->
            when (which) {
                0 -> navigationViewModel.setTarget(waypoint)
                1 -> showEditWaypointDialog(waypoint)
                2 -> showDeleteWaypointConfirmation(waypoint)
            }
        })
    }

    private fun showEditWaypointDialog(waypoint: Waypoint) {
        DialogUtils.showInputDialog(this, getString(R.string.edit_name), getString(R.string.waypoint_name_hint), waypoint.name, { newName ->
            lifecycleScope.launch {
                waypoint.name = newName
                waypointRepository.updateWaypoint(waypoint)
                DialogUtils.showSuccessToast(this@MainActivity, getString(R.string.waypoint_updated))
            }
        })
    }

    private fun showDeleteWaypointConfirmation(waypoint: Waypoint) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.confirm_delete)
            .setMessage(R.string.confirm_delete)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    waypointRepository.deleteWaypoint(waypoint)
                    DialogUtils.showSuccessToast(this@MainActivity, getString(R.string.waypoint_deleted))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showRouteManagementDialog() {
        val routes = mapViewModel.allRoutes.value
        val names = routes.map { it.route.name }.toTypedArray()
        DialogUtils.showListDialog(this, getString(R.string.manage_routes), names, { which ->
            showRouteOptionsDialog(routes[which])
        }, R.string.create_route, {
            val intent = Intent(this, CreateRouteActivity::class.java).apply {
                putExtra("waypoints_wrapper", WaypointListWrapper(ArrayList(mapViewModel.allWaypoints.value)))
                locationViewModel.currentLocation.value?.let { putExtra("current_latlng", LatLng(it.latitude, it.longitude)) }
            }
            createRouteLauncher.launch(intent)
        })
    }

    private fun showRouteOptionsDialog(routeWithWaypoints: RouteWithWaypoints) {
        val options = arrayOf(getString(R.string.start_navigation), getString(R.string.edit), getString(R.string.delete))
        DialogUtils.showListDialog(this, routeWithWaypoints.route.name, options, { which ->
            when (which) {
                0 -> {
                    val route = routeWithWaypoints.route.copy(waypoints = routeWithWaypoints.waypoints.toMutableList())
                    navigationViewModel.startNavigation(route)
                }
                1 -> {
                    val intent = Intent(this, CreateRouteActivity::class.java).apply {
                        val route = routeWithWaypoints.route.copy(waypoints = routeWithWaypoints.waypoints.toMutableList())
                        putExtra("route_to_edit", route)
                        putExtra("waypoints_wrapper", WaypointListWrapper(ArrayList(mapViewModel.allWaypoints.value)))
                        locationViewModel.currentLocation.value?.let { putExtra("current_latlng", LatLng(it.latitude, it.longitude)) }
                    }
                    editRouteLauncher.launch(intent)
                }
                2 -> {
                    lifecycleScope.launch {
                        routeRepository.deleteRoute(routeWithWaypoints.route)
                        DialogUtils.showSuccessToast(this@MainActivity, getString(R.string.route_deleted))
                    }
                }
            }
        })
    }

    private fun showWaypointManagementDialog() {
        val waypoints = mapViewModel.allWaypoints.value
        val names = waypoints.map { it.name }.toTypedArray()
        DialogUtils.showListDialog(this, getString(R.string.manage_waypoints), names, { which ->
            showWaypointOptionsDialog(waypoints[which])
        })
    }

    private fun showSkinSelectionDialog() {
        val skins = DefaultSkins.skins
        val skinNames = skins.map { it.name }.toTypedArray()
        DialogUtils.showListDialog(this, getString(R.string.select_skin), skinNames, { which ->
            val selectedSkin = skins[which]
            radarView.setSkin(selectedSkin)
            simpleCompassView.setSkin(selectedSkin)
            getSharedPreferences(AppConstants.PREFS_NAME, MODE_PRIVATE).edit()
                .putString(AppConstants.PREF_SKIN_NAME, selectedSkin.name).apply()
        })
    }

    private fun showSettingsDialog() {
        val options = arrayOf(getString(R.string.light_mode), getString(R.string.dark_mode), getString(R.string.system_default))
        DialogUtils.showListDialog(this, getString(R.string.theme_settings), options, { which ->
            val mode = when (which) {
                0 -> AppCompatDelegate.MODE_NIGHT_NO
                1 -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            AppCompatDelegate.setDefaultNightMode(mode)
            getSharedPreferences(AppConstants.PREFS_NAME, MODE_PRIVATE).edit()
                .putInt(AppConstants.PREF_THEME_MODE, mode).apply()
        })
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        locationViewModel.startUpdates()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        locationViewModel.stopUpdates()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
        mapManager.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onSupportNavigateUp(): Boolean {
        if (drawerLayout.isDrawerOpen(androidx.core.view.GravityCompat.START)) {
            drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
        } else {
            drawerLayout.openDrawer(androidx.core.view.GravityCompat.START)
        }
        return true
    }
}
