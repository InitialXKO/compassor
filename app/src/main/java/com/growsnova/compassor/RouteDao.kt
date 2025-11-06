package com.growsnova.compassor

import androidx.room.*

@Dao
interface RouteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoute(route: Route): Long

    @Update
    suspend fun updateRoute(route: Route)

    @Delete
    suspend fun deleteRoute(route: Route)

    @Transaction
    @Query("SELECT * FROM routes")
    suspend fun getRoutesWithWaypoints(): List<RouteWithWaypoints>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRouteWaypointCrossRef(crossRef: RouteWaypointCrossRef)

    @Delete
    suspend fun deleteRouteWaypointCrossRef(crossRef: RouteWaypointCrossRef)

    @Query("DELETE FROM route_waypoint_cross_ref WHERE routeId = :routeId")
    suspend fun deleteCrossRefsForRoute(routeId: Long)
}
