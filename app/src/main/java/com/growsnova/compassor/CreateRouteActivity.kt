package com.growsnova.compassor

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
            title = "Edit Route"
            viewModel.selectedWaypoints.value = routeToEdit.waypoints
        }

        waypoints = intent.getSerializableExtra("waypoints") as? ArrayList<Waypoint> ?: arrayListOf()
        currentLatLng = intent.getParcelableExtra("current_latlng")

        val viewPager = findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewPager)
        val tabLayout = findViewById<com.google.android.material.tabs.TabLayout>(R.id.tabLayout)

        viewPager.adapter = CreateRoutePagerAdapter(this, waypoints, currentLatLng)

        com.google.android.material.tabs.TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Saved"
                1 -> "Nearby"
                else -> "Search"
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
                viewModel.moveWaypoint(viewHolder.adapterPosition, target.adapterPosition)
                return true
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
                Toast.makeText(this, "Please select at least two waypoints", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val editText = android.widget.EditText(this)
            editText.hint = "Enter route name"
            if (selectedWaypoints.size >= 2) {
                editText.setText("${selectedWaypoints.first().name} to ${selectedWaypoints.last().name}")
            }

            android.app.AlertDialog.Builder(this)
                .setTitle("Save Route")
                .setView(editText)
                .setPositiveButton("Save") { _, _ ->
                    val routeName = editText.text.toString().trim()
                    if (routeName.isNotEmpty()) {
                        val route = Route(
                            id = System.currentTimeMillis(),
                            name = routeName,
                            waypoints = selectedWaypoints
                        )
                        val resultIntent = android.content.Intent()
                        resultIntent.putExtra("new_route", route)
                        setResult(RESULT_OK, resultIntent)
                        finish()
                    } else {
                        Toast.makeText(this, "Route name cannot be empty", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
}
