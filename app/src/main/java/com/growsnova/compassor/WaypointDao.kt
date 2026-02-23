package com.growsnova.compassor

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WaypointDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWaypoint(waypoint: Waypoint): Long

    @Update
    suspend fun updateWaypoint(waypoint: Waypoint)

    @Delete
    suspend fun deleteWaypoint(waypoint: Waypoint)

    @Query("SELECT * FROM waypoints")
    suspend fun getAllWaypoints(): List<Waypoint>

    @Query("SELECT * FROM waypoints")
    fun getAllWaypointsFlow(): Flow<List<Waypoint>>
}
