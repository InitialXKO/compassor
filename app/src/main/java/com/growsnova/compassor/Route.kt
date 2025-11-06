package com.growsnova.compassor

import java.io.Serializable

data class Route(
    val id: Long = 0,
    var name: String = "",
    val waypoints: MutableList<Waypoint> = mutableListOf(),
    var isLooping: Boolean = false
) : Serializable
