package com.growsnova.compassor

import java.io.Serializable

data class Waypoint(
    val id: Long = 0,
    var name: String = "",
    var latitude: Double = 0.0,
    var longitude: Double = 0.0
) : Serializable
