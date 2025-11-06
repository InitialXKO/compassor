package com.growsnova.compassor

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "routes")
data class Route(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    var name: String = "",
    @Ignore
    val waypoints: MutableList<Waypoint> = mutableListOf(),
    var isLooping: Boolean = false
) : Serializable
