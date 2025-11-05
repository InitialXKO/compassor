package com.growsnova.compassor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class WaypointSelectionAdapter(
    private val allWaypoints: List<Waypoint>,
    private val selectedWaypoints: MutableList<Waypoint>
) : RecyclerView.Adapter<WaypointSelectionAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_waypoint_selection, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val waypoint = allWaypoints[position]
        holder.waypointName.text = waypoint.name
        holder.waypointCheckBox.isChecked = selectedWaypoints.contains(waypoint)

        holder.itemView.setOnClickListener {
            holder.waypointCheckBox.isChecked = !holder.waypointCheckBox.isChecked
        }

        holder.waypointCheckBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!selectedWaypoints.contains(waypoint)) {
                    selectedWaypoints.add(waypoint)
                }
            } else {
                selectedWaypoints.remove(waypoint)
            }
        }
    }

    override fun getItemCount(): Int = allWaypoints.size

    fun getSelectedWaypoints(): MutableList<Waypoint> {
        return selectedWaypoints
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val waypointName: TextView = itemView.findViewById(R.id.waypointName)
        val waypointCheckBox: CheckBox = itemView.findViewById(R.id.waypointCheckBox)
    }
}
