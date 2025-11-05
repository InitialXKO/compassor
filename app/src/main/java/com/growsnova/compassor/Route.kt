package com.growsnova.compassor

data class Route(
    val id: Long = 0,
    var name: String,
    val waypoints: MutableList<Waypoint> = mutableListOf()
)
