package com.growsnova.compassor.data.repository

import com.growsnova.compassor.Waypoint
import com.growsnova.compassor.WaypointDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WaypointRepository @Inject constructor(
    private val waypointDao: WaypointDao
) {
    fun getAllWaypointsFlow(): Flow<List<Waypoint>> = waypointDao.getAllWaypointsFlow()

    suspend fun getAllWaypoints(): List<Waypoint> = waypointDao.getAllWaypoints()

    suspend fun insertWaypoint(waypoint: Waypoint): Long = waypointDao.insert(waypoint)

    suspend fun updateWaypoint(waypoint: Waypoint) = waypointDao.update(waypoint)

    suspend fun deleteWaypoint(waypoint: Waypoint) = waypointDao.delete(waypoint)

    suspend fun getWaypointById(id: Long): Waypoint? = waypointDao.getWaypointById(id)
}
