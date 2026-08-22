package com.growsnova.compassor.data.repository

import com.growsnova.compassor.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RouteRepository @Inject constructor(
    private val routeDao: RouteDao
) {
    suspend fun getRoutesWithWaypoints(): List<Route> {
        val routesWithWaypointsList = routeDao.getRoutesWithWaypoints()
        if (routesWithWaypointsList.isEmpty()) return emptyList()

        val allCrossRefsGrouped = routeDao.getAllCrossRefs().groupBy { it.routeId }

        return routesWithWaypointsList.map { routeWithWaypoints ->
            val route = routeWithWaypoints.route
            val crossRefs = allCrossRefsGrouped[route.id] ?: emptyList()
            val waypointMap = routeWithWaypoints.waypoints.associateBy { it.id }
            val sortedWaypoints = crossRefs.mapNotNull { crossRef -> waypointMap[crossRef.waypointId] }

            route.waypoints.clear()
            route.waypoints.addAll(sortedWaypoints)
            route
        }
    }

    suspend fun getRouteWithWaypoints(routeId: Long): Route? {
        return routeDao.getRouteWithWaypoints(routeId)?.let { routeWithWaypoints ->
            val route = routeWithWaypoints.route
            val crossRefs = routeDao.getCrossRefsForRoute(route.id)
            val waypointMap = routeWithWaypoints.waypoints.associateBy { it.id }
            val sortedWaypoints = crossRefs.mapNotNull { crossRef -> waypointMap[crossRef.waypointId] }

            route.waypoints.clear()
            route.waypoints.addAll(sortedWaypoints)
            route
        }
    }

    suspend fun insertRoute(route: Route): Long {
        return routeDao.insertRoute(route)
    }

    suspend fun updateRoute(route: Route) {
        routeDao.updateRoute(route)
    }

    suspend fun deleteRoute(route: Route) {
        routeDao.deleteRoute(route)
    }

    suspend fun insertRouteWaypointCrossRef(crossRef: RouteWaypointCrossRef) {
        routeDao.insertRouteWaypointCrossRef(crossRef)
    }

    suspend fun deleteRouteWaypointCrossRef(crossRef: RouteWaypointCrossRef) {
        routeDao.deleteRouteWaypointCrossRef(crossRef)
    }

    suspend fun deleteCrossRefsForRoute(routeId: Long) {
        routeDao.deleteCrossRefsForRoute(routeId)
    }
}
