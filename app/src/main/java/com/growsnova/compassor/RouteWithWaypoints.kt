package com.growsnova.compassor

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Junction
import androidx.room.Relation

@Entity(
    tableName = "route_waypoint_cross_ref",
    primaryKeys = ["routeId", "waypointId"],
    indices = [
        Index(value = ["routeId"]),
        Index(value = ["waypointId"])
    ]
)
data class RouteWaypointCrossRef(
    val routeId: Long,
    val waypointId: Long
)

data class RouteWithWaypoints(
    @Embedded val route: Route,
    @Relation(
        parentColumn = "id", // Refers to the primary key of the Route entity
        entityColumn = "id", // Refers to the primary key of the Waypoint entity
        associateBy = Junction(
            value = RouteWaypointCrossRef::class,
            parentColumn = "routeId", // Column in the junction table that points to the Route
            entityColumn = "waypointId" // Column in the junction table that points to the Waypoint
        )
    )
    val waypoints: List<Waypoint>
)
