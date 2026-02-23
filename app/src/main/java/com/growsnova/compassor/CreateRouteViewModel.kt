package com.growsnova.compassor

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class CreateRouteViewModel @Inject constructor() : ViewModel() {
    val selectedWaypoints = MutableLiveData<MutableList<Waypoint>>(mutableListOf())

    fun addWaypoint(waypoint: Waypoint) {
        val currentList = selectedWaypoints.value ?: mutableListOf()
        currentList.add(waypoint)
        selectedWaypoints.value = currentList
    }

    fun removeWaypoint(waypoint: Waypoint) {
        val currentList = selectedWaypoints.value ?: mutableListOf()
        currentList.remove(waypoint)
        selectedWaypoints.value = currentList
    }

    fun moveWaypoint(fromPosition: Int, toPosition: Int) {
        val currentList = selectedWaypoints.value ?: mutableListOf()
        if (fromPosition in currentList.indices && toPosition in currentList.indices) {
            val movedItem = currentList.removeAt(fromPosition)
            currentList.add(toPosition, movedItem)
            selectedWaypoints.value = currentList
        }
    }
    
    fun setWaypoints(waypoints: List<Waypoint>) {
        selectedWaypoints.value = waypoints.toMutableList()
    }
}
