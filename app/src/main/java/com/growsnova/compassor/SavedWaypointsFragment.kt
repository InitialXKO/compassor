package com.growsnova.compassor

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.growsnova.compassor.data.repository.WaypointRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SavedWaypointsFragment : Fragment() {

    @Inject lateinit var waypointRepository: WaypointRepository

    private val viewModel: CreateRouteViewModel by activityViewModels()
    private lateinit var adapter: WaypointSelectionAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_saved_waypoints, container, false)
        val recyclerView = view.findViewById<RecyclerView>(R.id.savedWaypointsRecyclerView)
        val emptyStateButton = view.findViewById<View>(R.id.addFirstWaypointButton)
        
        emptyStateButton?.applyTouchScale()
        
        recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = WaypointSelectionAdapter(emptyList()) { waypoint ->
            viewModel.addWaypoint(waypoint)
        }
        recyclerView.adapter = adapter

        emptyStateButton?.setOnClickListener {
            val intent = Intent(requireContext(), MainActivity::class.java)
            intent.putExtra("open_add_waypoint", true)
            startActivity(intent)
        }

        setupObservers()
        return view
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                waypointRepository.getAllWaypointsFlow().collectLatest { waypoints ->
                    adapter.updateWaypoints(waypoints)
                    updateEmptyState(waypoints.isEmpty())
                }
            }
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        val view = view ?: return
        val emptyState = view.findViewById<View>(R.id.emptyState)
        val recyclerView = view.findViewById<RecyclerView>(R.id.savedWaypointsRecyclerView)
        
        if (isEmpty) {
            emptyState?.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyState?.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    companion object {
        @JvmStatic
        fun newInstance() = SavedWaypointsFragment()
    }
}

class WaypointSelectionAdapter(
    private var waypoints: List<Waypoint>,
    private val onWaypointClicked: (Waypoint) -> Unit
) : RecyclerView.Adapter<WaypointSelectionAdapter.WaypointViewHolder>() {

    fun updateWaypoints(newWaypoints: List<Waypoint>) {
        waypoints = newWaypoints
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WaypointViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_waypoint, parent, false)
        return WaypointViewHolder(view)
    }

    override fun onBindViewHolder(holder: WaypointViewHolder, position: Int) {
        holder.bind(waypoints[position], onWaypointClicked)
    }

    override fun getItemCount() = waypoints.size

    class WaypointViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameView: TextView = itemView.findViewById(R.id.waypointName)

        init {
            itemView.applyTouchScale()
        }

        fun bind(waypoint: Waypoint, onWaypointClicked: (Waypoint) -> Unit) {
            nameView.text = waypoint.name
            itemView.setOnClickListener { onWaypointClicked(waypoint) }
        }
    }
}
