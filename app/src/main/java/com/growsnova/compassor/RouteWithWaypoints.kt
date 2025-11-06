package com.growsnova.compassor

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Junction
import androidx.room.Relation

@Entity(tableName = "route_waypoint_cross_ref", primaryKeys = ["routeId", "waypointId"])
data class RouteWaypointCrossRef(
    val routeId: Long,
    val waypointId: Long
)

data class RouteWithWaypoints(
    @Embedded val route: Route,
    @Relation(
        parentColumn = "id",
        entity = Waypoint::class,
        associateBy = Junction(
            value = RouteWaypointCrossRef::class,
            parentColumn = "routeId",
            entityColumn = "waypointId"
        )
    )
    val waypoints: List<Waypoint>
)
