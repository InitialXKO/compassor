package com.growsnova.compassor

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "waypoints")
data class Waypoint(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    var name: String = "",
    var latitude: Double = 0.0,
    var longitude: Double = 0.0
) : Serializable
