package com.growsnova.compassor

import java.io.Serializable

data class Waypoint(
    val id: Long = 0,
    var name: String,
    val latitude: Double,
    val longitude: Double
) : Serializable
