package com.growsnova.compassor

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RouteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(route: Route): Long

    @Update
    suspend fun update(route: Route)

    @Delete
    suspend fun delete(route: Route)

    @Transaction
    @Query("SELECT * FROM routes")
    suspend fun getRoutesWithWaypoints(): List<RouteWithWaypoints>

    @Transaction
    @Query("SELECT * FROM routes")
    fun getRoutesWithWaypointsFlow(): Flow<List<RouteWithWaypoints>>

    @Transaction
    @Query("SELECT * FROM routes WHERE id = :routeId")
    suspend fun getRouteWithWaypoints(routeId: Long): RouteWithWaypoints?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRouteWaypointCrossRef(crossRef: RouteWaypointCrossRef)

    @Delete
    suspend fun deleteRouteWaypointCrossRef(crossRef: RouteWaypointCrossRef)

    @Query("DELETE FROM route_waypoint_cross_ref WHERE routeId = :routeId")
    suspend fun deleteCrossRefsForRoute(routeId: Long)
}
