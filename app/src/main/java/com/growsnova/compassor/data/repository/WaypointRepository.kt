package com.growsnova.compassor.data.repository

import com.growsnova.compassor.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WaypointRepository @Inject constructor(
    private val waypointDao: WaypointDao
) {
    fun getAllWaypointsFlow(): Flow<List<Waypoint>> = waypointDao.getAllWaypointsFlow()

    suspend fun getAllWaypoints(): List<Waypoint> = waypointDao.getAllWaypoints()

    suspend fun insertWaypoint(waypoint: Waypoint): Result<Long> {
        return try {
            val id = waypointDao.insertWaypoint(waypoint)
            Result.Success(id)
        } catch (e: Exception) {
            Result.Error(CompassorException.DatabaseException("Failed to insert waypoint", e))
        }
    }

    suspend fun updateWaypoint(waypoint: Waypoint): Result<Unit> {
        return try {
            waypointDao.updateWaypoint(waypoint)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(CompassorException.DatabaseException("Failed to update waypoint", e))
        }
    }

    suspend fun deleteWaypoint(waypoint: Waypoint): Result<Unit> {
        return try {
            waypointDao.deleteWaypoint(waypoint)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(CompassorException.DatabaseException("Failed to delete waypoint", e))
        }
    }
}
