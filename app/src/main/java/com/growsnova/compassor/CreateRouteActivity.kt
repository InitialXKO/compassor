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
    private var routeBeingEdited: Route? = null
    private val viewModel: CreateRouteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_route)

        routeBeingEdited = intent.getSerializableExtraCompat<Route>("route_to_edit")
        routeBeingEdited?.let {
            title = getString(R.string.edit_route)
            viewModel.selectedWaypoints.value = it.waypoints
        }

        val waypointWrapper = intent.getSerializableExtraCompat<WaypointListWrapper>("waypoints_wrapper")
        waypoints = waypointWrapper?.waypoints ?: arrayListOf()
        currentLatLng = intent.getParcelableExtraCompat<LatLng>("current_latlng")

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
                val fromPosition = viewHolder.bindingAdapterPosition
                val toPosition = target.bindingAdapterPosition
                if (fromPosition == RecyclerView.NO_POSITION || toPosition == RecyclerView.NO_POSITION) {
                    return false
                }
                adapter.onItemMove(fromPosition, toPosition)
                return true
            }
            
            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                val currentWaypoints = adapter.getWaypoints()
                viewModel.setWaypoints(currentWaypoints)
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) {
                    return
                }
                val waypoint = adapter.getWaypointAt(position)
                viewModel.removeWaypoint(waypoint)
            }
            
            override fun onChildDraw(
                c: android.graphics.Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    val itemView = viewHolder.itemView
                    val paint = android.graphics.Paint()
                    paint.color = android.graphics.Color.RED
                    
                    if (dX > 0) {
                        c.drawRect(itemView.left.toFloat(), itemView.top.toFloat(), dX, itemView.bottom.toFloat(), paint)
                    } else {
                        c.drawRect(itemView.right.toFloat() + dX, itemView.top.toFloat(), itemView.right.toFloat(), itemView.bottom.toFloat(), paint)
                    }
                    
                    val alpha = 1.0f - Math.abs(dX) / itemView.width.toFloat()
                    itemView.alpha = alpha
                }
                
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        })
        itemTouchHelper.attachToRecyclerView(selectedWaypointsRecyclerView)

        val saveRouteButton = findViewById<android.widget.Button>(R.id.saveRouteButton)
        saveRouteButton.applyTouchScale()
        saveRouteButton.setOnClickListener {
            val selectedWaypoints = viewModel.selectedWaypoints.value
            if (selectedWaypoints.isNullOrEmpty() || selectedWaypoints.size < 2) {
                DialogUtils.showErrorToast(this, "需要至少选择2个收藏地点来保存路线")
                return@setOnClickListener
            }

            val initialName = if (routeBeingEdited != null) {
                routeBeingEdited?.name.orEmpty()
            } else if (selectedWaypoints.size >= 2) {
                getString(R.string.save_route_hint, selectedWaypoints.first().name, selectedWaypoints.last().name)
            } else {
                ""
            }

            DialogUtils.showInputDialog(
                context = this,
                title = if (routeBeingEdited == null) getString(R.string.create_route) else getString(R.string.edit_route),
                hint = getString(R.string.route_name_hint),
                initialValue = initialName,
                onPositive = { routeName ->
                    val newRoute = Route(
                        id = routeBeingEdited?.id ?: System.currentTimeMillis(),
                        name = routeName,
                        waypoints = selectedWaypoints,
                        isLooping = routeBeingEdited?.isLooping ?: false
                    )

                    val resultIntent = Intent().apply {
                        putExtra("new_route", newRoute)
                        putExtra("waypoints_wrapper", WaypointListWrapper(ArrayList(selectedWaypoints)))
                    }

                    if (routeBeingEdited == null) {
                        askToStartNavigation(newRoute, resultIntent)
                    } else {
                        setResult(RESULT_OK, resultIntent)
                        finish()
                    }
                }
            )
        }
    }

    private fun askToStartNavigation(route: Route, resultIntent: Intent) {
        DialogUtils.showConfirmationDialog(
            context = this,
            title = getString(R.string.start_navigation),
            message = "是否开始导航路线: ${route.name}?",
            onPositive = {
                resultIntent.putExtra("start_navigation", true)
                setResult(RESULT_OK, resultIntent)
                finish()
            },
            onNegative = {
                setResult(RESULT_OK, resultIntent)
                finish()
            }
        )
    }
}
