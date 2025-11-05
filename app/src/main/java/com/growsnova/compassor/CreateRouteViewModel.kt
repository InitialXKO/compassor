package com.growsnova.compassor

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class CreateRouteViewModel : ViewModel() {
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
        val movedItem = currentList.removeAt(fromPosition)
        currentList.add(toPosition, movedItem)
        selectedWaypoints.value = currentList
    }
}
