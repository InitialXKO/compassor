package com.growsnova.compassor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class SavedWaypointsFragment : Fragment() {

    private var waypoints: ArrayList<Waypoint> = arrayListOf()
    private val viewModel: CreateRouteViewModel by activityViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            val waypointWrapper = it.getSerializable(ARG_WAYPOINTS) as? WaypointListWrapper
            waypoints = waypointWrapper?.waypoints ?: arrayListOf()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_saved_waypoints, container, false)
        val recyclerView = view.findViewById<RecyclerView>(R.id.savedWaypointsRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = WaypointSelectionAdapter(waypoints) { waypoint ->
            viewModel.addWaypoint(waypoint)
        }
        return view
    }

    companion object {
        private const val ARG_WAYPOINTS = "waypoints"

        @JvmStatic
        fun newInstance(waypointsWrapper: WaypointListWrapper) =
            SavedWaypointsFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_WAYPOINTS, waypointsWrapper)
                }
            }
    }
}

class WaypointSelectionAdapter(
    private val waypoints: List<Waypoint>,
    private val onWaypointClicked: (Waypoint) -> Unit
) : RecyclerView.Adapter<WaypointSelectionAdapter.WaypointViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WaypointViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return WaypointViewHolder(view)
    }

    override fun onBindViewHolder(holder: WaypointViewHolder, position: Int) {
        holder.bind(waypoints[position], onWaypointClicked)
    }

    override fun getItemCount() = waypoints.size

    class WaypointViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(waypoint: Waypoint, onWaypointClicked: (Waypoint) -> Unit) {
            (itemView as TextView).text = waypoint.name
            itemView.setOnClickListener { onWaypointClicked(waypoint) }
        }
    }
}
