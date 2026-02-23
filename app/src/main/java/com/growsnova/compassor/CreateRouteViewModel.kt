package com.growsnova.compassor

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class CreateRouteViewModel @Inject constructor() : ViewModel() {
    private val _selectedWaypoints = MutableStateFlow<MutableList<Waypoint>>(mutableListOf())
    val selectedWaypoints: StateFlow<List<Waypoint>> = _selectedWaypoints.asStateFlow()

    fun addWaypoint(waypoint: Waypoint) {
        val currentList = _selectedWaypoints.value.toMutableList()
        currentList.add(waypoint)
        _selectedWaypoints.value = currentList
    }

    fun removeWaypoint(waypoint: Waypoint) {
        val currentList = _selectedWaypoints.value.toMutableList()
        currentList.remove(waypoint)
        _selectedWaypoints.value = currentList
    }

    fun moveWaypoint(fromPosition: Int, toPosition: Int) {
        val currentList = _selectedWaypoints.value.toMutableList()
        if (fromPosition in currentList.indices && toPosition in currentList.indices) {
            val movedItem = currentList.removeAt(fromPosition)
            currentList.add(toPosition, movedItem)
            _selectedWaypoints.value = currentList
        }
    }
    
    fun setWaypoints(waypoints: List<Waypoint>) {
        _selectedWaypoints.value = waypoints.toMutableList()
    }
}
