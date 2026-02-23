package com.growsnova.compassor

import androidx.room.*

@Dao
interface WaypointDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(waypoint: Waypoint): Long

    @Update
    suspend fun update(waypoint: Waypoint)

    @Delete
    suspend fun delete(waypoint: Waypoint)

    @Query("SELECT * FROM waypoints")
    suspend fun getAllWaypoints(): List<Waypoint>

    @Query("SELECT * FROM waypoints")
    fun getAllWaypointsFlow(): kotlinx.coroutines.flow.Flow<List<Waypoint>>

    @Query("SELECT * FROM waypoints WHERE id = :id")
    suspend fun getWaypointById(id: Long): Waypoint?
}
