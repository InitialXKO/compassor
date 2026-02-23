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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
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

    // Navigation UI
    private lateinit var navigationStatusCard: MaterialCardView
    private lateinit var navTargetText: android.widget.TextView
    private lateinit var navDistanceText: android.widget.TextView
    private lateinit var stopNavButton: MaterialButton
    private lateinit var skipNavButton: MaterialButton
    private lateinit var prevNavButton: MaterialButton

    private lateinit var searchBottomSheet: com.google.android.material.card.MaterialCardView
    private lateinit var bottomSheetBehavior: com.google.android.material.bottomsheet.BottomSheetBehavior<View>
    private lateinit var searchResultsAdapter: PoiListAdapter

    private val locationViewModel: LocationViewModel by viewModels()
    private val mapViewModel: MapViewModel by viewModels()
    private val navigationViewModel: NavigationViewModel by viewModels()

    @Inject
    lateinit var mapManager: MapManager

    @Inject
    lateinit var navigationRepository: NavigationRepository

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

    override fun onCreate(savedInstanceState: Bundle?) {
        loadThemePreference()
        super.onCreate(savedInstanceState)

        AMapLocationClient.updatePrivacyShow(this, true, true)
        AMapLocationClient.updatePrivacyAgree(this, true)

        setContentView(R.layout.activity_main)

        initViews(savedInstanceState)
        setupObservers()
        checkAndRequestPermissions()
        handleNavigationIntent()
    }

    private fun loadThemePreference() {
        val themeMode = navigationRepository.getThemeMode()
        if (themeMode != -1 && AppCompatDelegate.getDefaultNightMode() != themeMode) {
            AppCompatDelegate.setDefaultNightMode(themeMode)
        }
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

        initSearchBottomSheet()

        navigationStatusCard = findViewById(R.id.navigationStatusCard)
        navTargetText = findViewById(R.id.navTargetText)
        navDistanceText = findViewById(R.id.navDistanceText)
        stopNavButton = findViewById(R.id.stopNavButton)
        skipNavButton = findViewById(R.id.skipNavButton)
        prevNavButton = findViewById(R.id.prevNavButton)
        
        stopNavButton.applyTouchScale()
        stopNavButton.setOnClickListener { navigationViewModel.stopNavigation() }
        skipNavButton.applyTouchScale()
        skipNavButton.setOnClickListener { navigationViewModel.skipNextWaypoint() }
        prevNavButton.applyTouchScale()
        prevNavButton.setOnClickListener { navigationViewModel.goToPreviousWaypoint() }

        mapView.onCreate(savedInstanceState)
        aMap = mapView.map
        mapManager.initialize(aMap)

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
        searchResultsAdapter = PoiListAdapter(emptyList()) { poiItem ->
            val latLng = LatLng(poiItem.latLonPoint.latitude, poiItem.latLonPoint.longitude)
            navigationViewModel.setTarget(latLng, poiItem.title)
            bottomSheetBehavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN
        }
        recyclerView.adapter = searchResultsAdapter
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    locationViewModel.currentLocation.collectLatest { location ->
                        location?.let {
                            mapManager.updateMyLocation(it)
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
                            mapManager.setTargetLocation(target.first, target.second)
                            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        } else {
                            mapManager.clearTarget()
                            mapManager.clearRoute()
                            navigationStatusCard.visibility = View.GONE
                            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                    }
                }

                launch {
                    navigationViewModel.currentRoute.collectLatest { route ->
                        if (route != null) {
                            mapManager.drawRoute(route.waypoints, navigationViewModel.currentWaypointIndex.value,
                                getThemeColor(com.google.android.material.R.attr.colorPrimary),
                                getThemeColor(com.google.android.material.R.attr.colorOutline))
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
                            searchResultsAdapter.updateData(results)
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

    private fun updateNavigationStatusUI(update: NavigationManager.NavigationUpdate) {
        if (navigationStatusCard.visibility != View.VISIBLE) {
            navigationStatusCard.alpha = 0f
            navigationStatusCard.visibility = View.VISIBLE
            navigationStatusCard.animate().alpha(1f).setDuration(300).start()
        }
        navTargetText.text = getString(R.string.nav_target_format, update.targetName)
        
        val distanceStr = if (update.distance < 1000) "${update.distance.toInt()}m" else "%.1fkm".format(update.distance / 1000f)
        navDistanceText.text = getString(R.string.nav_distance_format, distanceStr)

        val route = navigationViewModel.currentRoute.value
        skipNavButton.visibility = if (route != null) View.VISIBLE else View.GONE
        prevNavButton.visibility = if (route != null) View.VISIBLE else View.GONE
        
        route?.let {
            val index = navigationViewModel.currentWaypointIndex.value
            val canSkip = it.isLooping || index < it.waypoints.size - 1
            val canPrev = it.isLooping || index > 0
            skipNavButton.isEnabled = canSkip
            prevNavButton.isEnabled = canPrev
            skipNavButton.alpha = if (canSkip) 1.0f else 0.5f
            prevNavButton.alpha = if (canPrev) 1.0f else 0.5f
        }

        if (update.nextWaypointReached) {
            DialogUtils.showToast(this, getString(R.string.next_waypoint_notification, update.targetName))
        }
        if (update.routeCompleted) {
            DialogUtils.showToast(this, getString(R.string.route_completed))
        }
    }

    private fun handleNavigationIntent() {
        val route = intent.getSerializableExtraCompat<Route>("start_navigation_route")
        route?.let {
            mapView.postDelayed({ if (it.waypoints.isNotEmpty()) navigationViewModel.startRouteNavigation(it) }, 1000)
        }
    }

    private fun checkAndRequestPermissions() {
        val required = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        val needed = required.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), AppConstants.LOCATION_PERMISSION_REQUEST_CODE)
        } else {
            setupAMapLocationStyle()
        }
    }

    private fun setupAMapLocationStyle() {
        val primaryColor = getThemeColor(com.google.android.material.R.attr.colorPrimary)
        aMap.myLocationStyle = com.amap.api.maps.model.MyLocationStyle().apply {
            myLocationType(com.amap.api.maps.model.MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE_NO_CENTER)
            strokeColor(primaryColor)
            radiusFillColor(primaryColor and 0x30FFFFFF)
        }
        aMap.isMyLocationEnabled = true
    }

    private fun getThemeColor(attr: Int): Int {
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == AppConstants.LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                setupAMapLocationStyle()
            } else {
                DialogUtils.showErrorToast(this, getString(R.string.location_permission_denied))
            }
        }
    }

    private fun showMapLongClickOptionsDialog(latLng: LatLng) {
        val options = arrayOf(getString(R.string.save_location), getString(R.string.set_destination))
        DialogUtils.showOptionsDialog(this, getString(R.string.select_action), options) { which ->
            when (which) {
                0 -> navigationViewModel.reverseGeocode(latLng) { name -> showSaveWaypointDialog(latLng, defaultName = name) }
                1 -> navigationViewModel.reverseGeocode(latLng) { name -> navigationViewModel.setTarget(latLng, name) }
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
            onPositive = { name ->
                if (waypointToEdit == null) {
                    addWaypoint(latLng, name)
                } else {
                    updateWaypoint(waypointToEdit, name)
                }
            }
        )
    }

    private fun addWaypoint(latLng: LatLng, name: String) {
        val existing = mapViewModel.waypoints.value.find {
            val dist = FloatArray(1)
            android.location.Location.distanceBetween(latLng.latitude, latLng.longitude, it.latitude, it.longitude, dist)
            (it.name.equals(name, ignoreCase = true) || it.name.contains(name, ignoreCase = true)) && dist[0] < 10
        }

        if (existing != null) {
            DialogUtils.showConfirmationDialog(this, getString(R.string.update_waypoint_title),
                getString(R.string.update_waypoint_message, existing.name, name),
                onPositive = { updateWaypoint(existing, name, latLng) }
            )
        } else {
            navigationViewModel.addWaypoint(Waypoint(name = name, latitude = latLng.latitude, longitude = latLng.longitude))
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
            R.id.nav_save_location -> {
                locationViewModel.currentLocation.value?.let { latLng ->
                    navigationViewModel.reverseGeocode(latLng) { name -> showSaveWaypointDialog(latLng, defaultName = name) }
                } ?: DialogUtils.showErrorToast(this, getString(R.string.location_unavailable))
            }
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
        }.toTypedArray()

        DialogUtils.showOptionsDialog(this, getString(R.string.manage_waypoints_title), displayInfo) { which ->
            showWaypointOptionsDialog(waypoints[which])
        }
    }

    private fun showWaypointOptionsDialog(waypoint: Waypoint) {
        val options = arrayOf(getString(R.string.set_destination), getString(R.string.edit_name), getString(R.string.delete))
        DialogUtils.showOptionsDialog(this, waypoint.name, options) { which ->
            when (which) {
                0 -> navigationViewModel.setTarget(LatLng(waypoint.latitude, waypoint.longitude), waypoint.name)
                1 -> showSaveWaypointDialog(LatLng(waypoint.latitude, waypoint.longitude), waypoint)
                2 -> confirmDeleteWaypoint(waypoint)
            }
        }
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
        val names = routes.map { it.name }.toTypedArray()
        DialogUtils.showListDialog(this, getString(R.string.manage_routes), names,
            onItemSelected = { showRouteOptionsDialog(routes[it]) },
            positiveButtonText = R.string.create_route,
            onPositive = { launchCreateRoute() }
        )
    }

    private fun showRouteOptionsDialog(route: Route) {
        val options = arrayOf(getString(R.string.start_navigation), getString(R.string.edit_route), getString(R.string.delete_route))
        DialogUtils.showOptionsDialog(this, route.name, options) { which ->
            when (which) {
                0 -> navigationViewModel.startRouteNavigation(route)
                1 -> launchEditRoute(route)
                2 -> navigationViewModel.deleteRoute(route)
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
        val skinNames = arrayOf(getString(R.string.skin_default), getString(R.string.skin_forest), getString(R.string.skin_ocean), getString(R.string.import_skin))
        val skinKeys = arrayOf("Default", "Forest", "Ocean", "Import")
        val options = skinNames.mapIndexed { i, name -> if (i < 3) "$name - ${getSkinDesc(i)}" else name }.toTypedArray()
        
        DialogUtils.showOptionsDialog(this, getString(R.string.select_skin), options) { which ->
            if (which < 3) {
                val key = skinKeys[which]
                navigationRepository.saveSkinName(key)
                applySkin(key)
            } else {
                skinPickerLauncher.launch(arrayOf("application/json"))
            }
        }
    }

    private fun getSkinDesc(i: Int) = when(i) {
        0 -> getString(R.string.skin_default_desc)
        1 -> getString(R.string.skin_forest_desc)
        2 -> getString(R.string.skin_ocean_desc)
        else -> ""
    }

    private fun applySkin(skinName: String) {
        val skin = when (skinName) {
            "Forest" -> DefaultSkins.forest
            "Ocean" -> DefaultSkins.ocean
            else -> DefaultSkins.default
        }
        radarView.setSkin(skin)
        simpleCompassView.setSkin(skin)
    }

    private fun showSettingsDialog() {
        val currentMode = navigationRepository.getThemeMode().let { if (it == -1) AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM else it }
        val themeOptions = arrayOf(getString(R.string.system_default), getString(R.string.light_mode), getString(R.string.dark_mode))
        val currentSelection = when (currentMode) {
            AppCompatDelegate.MODE_NIGHT_NO -> 1
            AppCompatDelegate.MODE_NIGHT_YES -> 2
            else -> 0
        }
        
        MaterialAlertDialogBuilder(this).setTitle(R.string.theme_settings).setSingleChoiceItems(themeOptions, currentSelection) { dialog, which ->
            val newMode = when (which) { 1 -> AppCompatDelegate.MODE_NIGHT_NO; 2 -> AppCompatDelegate.MODE_NIGHT_YES; else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM }
            navigationRepository.saveThemeMode(newMode)
            AppCompatDelegate.setDefaultNightMode(newMode)
            dialog.dismiss()
        }.setNegativeButton(R.string.cancel, null).show()
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
        R.id.action_search -> { showSearchDialog(); true }; else -> super.onOptionsItemSelected(item)
    }

    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onDestroy() { super.onDestroy(); mapView.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState) }
}
