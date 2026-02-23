package com.growsnova.compassor.data.repository

import com.growsnova.compassor.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RouteRepository @Inject constructor(
    private val routeDao: RouteDao
) {
    suspend fun getRoutesWithWaypoints(): List<Route> {
        return routeDao.getRoutesWithWaypoints().map {
            val route = it.route
            route.waypoints.clear()
            route.waypoints.addAll(it.waypoints)
            route
        }
    }

    suspend fun getRouteWithWaypoints(routeId: Long): Route? {
        return routeDao.getRouteWithWaypoints(routeId)?.let {
            val route = it.route
            route.waypoints.clear()
            route.waypoints.addAll(it.waypoints)
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
