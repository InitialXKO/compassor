package com.growsnova.compassor

import com.google.gson.Gson
import com.google.gson.GsonBuilder

data class ExportedWaypoint(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val remarks: String? = null,
    val floor: Int? = null
)

data class ExportedRoute(
    val name: String,
    val isLooping: Boolean = false,
    val waypoints: List<ExportedWaypoint>
)

object RouteJsonUtils {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    fun exportRouteToJson(route: Route): String {
        val exportedWaypoints = route.waypoints.map {
            ExportedWaypoint(
                name = it.name,
                latitude = it.latitude,
                longitude = it.longitude,
                remarks = it.remarks,
                floor = it.floor
            )
        }
        val exportedRoute = ExportedRoute(
            name = route.name,
            isLooping = route.isLooping,
            waypoints = exportedWaypoints
        )
        return gson.toJson(exportedRoute)
    }

    fun importRouteFromJson(jsonStr: String): Pair<Route, List<Waypoint>>? {
        return try {
            val exported = gson.fromJson(jsonStr, ExportedRoute::class.java) ?: return null
            if (exported.waypoints.isEmpty()) return null

            val waypoints = exported.waypoints.map {
                Waypoint(
                    id = 0L,
                    name = it.name,
                    latitude = it.latitude,
                    longitude = it.longitude,
                    remarks = it.remarks,
                    floor = it.floor
                )
            }
            val route = Route(
                id = 0L,
                name = exported.name,
                isLooping = exported.isLooping,
                waypoints = waypoints.toMutableList()
            )
            Pair(route, waypoints)
        } catch (e: Exception) {
            null
        }
    }
}
