package com.growsnova.compassor

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.amap.api.maps.model.LatLng

class CreateRouteActivity : AppCompatActivity() {

    private lateinit var waypoints: List<Waypoint>
    private var currentLatLng: LatLng? = null
    private val viewModel: CreateRouteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_route)

        val routeToEdit = intent.getSerializableExtra("route_to_edit") as? Route
        if (routeToEdit != null) {
            title = getString(R.string.edit_route)
            viewModel.selectedWaypoints.value = routeToEdit.waypoints
        }

        val waypointWrapper = intent.getSerializableExtra("waypoints_wrapper") as? WaypointListWrapper
        waypoints = waypointWrapper?.waypoints ?: arrayListOf()
        currentLatLng = intent.getParcelableExtra("current_latlng")

        val viewPager = findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewPager)
        val tabLayout = findViewById<com.google.android.material.tabs.TabLayout>(R.id.tabLayout)

        viewPager.adapter = CreateRoutePagerAdapter(this, waypoints, currentLatLng)

        com.google.android.material.tabs.TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.saved_waypoints)
                1 -> getString(R.string.nearby_pois)
                else -> getString(R.string.search_location)
            }
        }.attach()

        val selectedWaypointsRecyclerView = findViewById<RecyclerView>(R.id.selectedWaypointsRecyclerView)
        val adapter = SelectedWaypointsAdapter(mutableListOf())
        selectedWaypointsRecyclerView.adapter = adapter
        selectedWaypointsRecyclerView.layoutManager = LinearLayoutManager(this)

        viewModel.selectedWaypoints.observe(this) { waypoints ->
            adapter.updateWaypoints(waypoints)
        }

        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.Callback() {
            override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
                val swipeFlags = ItemTouchHelper.START or ItemTouchHelper.END
                return makeMovementFlags(dragFlags, swipeFlags)
            }

            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition
                adapter.onItemMove(fromPosition, toPosition)
                return true
            }
            
            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                val currentWaypoints = adapter.getWaypoints()
                viewModel.setWaypoints(currentWaypoints)
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                viewModel.removeWaypoint(adapter.getWaypointAt(viewHolder.adapterPosition))
            }
        })
        itemTouchHelper.attachToRecyclerView(selectedWaypointsRecyclerView)

        val saveRouteButton = findViewById<android.widget.Button>(R.id.saveRouteButton)
        saveRouteButton.setOnClickListener {
            val selectedWaypoints = viewModel.selectedWaypoints.value
            if (selectedWaypoints.isNullOrEmpty() || selectedWaypoints.size < 2) {
                DialogUtils.showErrorToast(this, "需要至少选择2个收藏地点来保存路线")
                return@setOnClickListener
            }

            val editText = android.widget.EditText(this)
            editText.hint = getString(R.string.route_name_hint)

            // If editing, pre-fill the existing route name
            val routeToEdit = intent.getSerializableExtra("route_to_edit") as? Route
            if (routeToEdit != null) {
                editText.setText(routeToEdit.name)
            } else if (selectedWaypoints.size >= 2) {
                editText.setText(getString(R.string.save_route_hint, selectedWaypoints.first().name, selectedWaypoints.last().name))
            }

            android.app.AlertDialog.Builder(this)
                .setTitle(if (routeToEdit == null) getString(R.string.create_route) else getString(R.string.edit_route))
                .setView(editText)
                .setPositiveButton(getString(R.string.save)) { _, _ ->
                    val routeName = editText.text.toString().trim()
                    if (routeName.isNotEmpty()) {
                        val newRoute = Route(
                            id = routeToEdit?.id ?: System.currentTimeMillis(),
                            name = routeName,
                            waypoints = selectedWaypoints,
                            isLooping = routeToEdit?.isLooping ?: false
                        )
                        
                        // Set result first to ensure save happens
                        val resultIntent = android.content.Intent()
                        resultIntent.putExtra("new_route", newRoute)
                        resultIntent.putExtra("waypoints_wrapper", WaypointListWrapper(ArrayList(selectedWaypoints)))
                        setResult(RESULT_OK, resultIntent)
                        
                        // Then ask if user wants to start navigation for new routes
                        if (routeToEdit == null) {
                            askToStartNavigation(newRoute)
                        } else {
                            finish()
                        }
                    } else {
                        Toast.makeText(this, getString(R.string.waypoint_name_empty), Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }
    }

    private fun askToStartNavigation(route: Route) {
        android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.start_navigation))
            .setMessage("是否开始导航路线: ${route.name}?")
            .setPositiveButton(getString(R.string.confirm)) { _, _ ->
                // Start navigation in MainActivity
                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra("start_navigation_route", route)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton(getString(R.string.cancel)) { _, _ ->
                finish()
            }
            .show()
    }
}
