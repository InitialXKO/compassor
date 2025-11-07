package com.growsnova.compassor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Collections

class SelectedWaypointsAdapter(
    private var waypoints: MutableList<Waypoint>
) : RecyclerView.Adapter<SelectedWaypointsAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_waypoint, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val waypoint = waypoints[position]
        holder.waypointName.text = waypoint.name
    }

    override fun getItemCount(): Int = waypoints.size

    fun updateWaypoints(newWaypoints: List<Waypoint>) {
        waypoints.clear()
        waypoints.addAll(newWaypoints)
        notifyDataSetChanged()
    }

    fun getWaypointAt(position: Int): Waypoint {
        return waypoints[position]
    }

    fun onItemMove(fromPosition: Int, toPosition: Int) {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(waypoints, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(waypoints, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val waypointName: TextView = itemView.findViewById(R.id.waypoint_name)
    }
}
