package com.growsnova.compassor.data.repository

import android.content.SharedPreferences
import com.growsnova.compassor.*
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RouteRepository @Inject constructor(
    private val routeDao: RouteDao,
    private val prefs: SharedPreferences,
    private val gson: Gson
) {
    fun getAllRoutesWithWaypointsFlow(): Flow<List<RouteWithWaypoints>> = routeDao.getRoutesWithWaypointsFlow()

    suspend fun insertRoute(route: Route): Result<Long> {
        return try {
            val id = routeDao.insert(route)
            Result.Success(id)
        } catch (e: Exception) {
            Result.Error(CompassorException.DatabaseException("Failed to insert route", e))
        }
    }

    suspend fun insertRouteWaypointCrossRef(crossRef: RouteWaypointCrossRef) {
        routeDao.insertRouteWaypointCrossRef(crossRef)
    }

    suspend fun deleteRoute(route: Route): Result<Unit> {
        return try {
            routeDao.delete(route)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(CompassorException.DatabaseException("Failed to delete route", e))
        }
    }

    // Navigation Persistence
    fun saveNavigationState(route: Route?, index: Int) {
        prefs.edit().apply {
            if (route != null) {
                putString(AppConstants.PREF_NAV_ROUTE, gson.toJson(route))
                putLong(AppConstants.PREF_NAV_ROUTE_ID, route.id)
                putInt(AppConstants.PREF_NAV_INDEX, index)
            } else {
                remove(AppConstants.PREF_NAV_ROUTE)
                remove(AppConstants.PREF_NAV_ROUTE_ID)
                remove(AppConstants.PREF_NAV_INDEX)
            }
            apply()
        }
    }

    fun getSavedNavigationRoute(): Route? {
        val routeJson = prefs.getString(AppConstants.PREF_NAV_ROUTE, null)
        return if (routeJson != null) {
            try {
                gson.fromJson(routeJson, Route::class.java)
            } catch (e: Exception) {
                null
            }
        } else null
    }

    fun getSavedNavigationIndex(): Int = prefs.getInt(AppConstants.PREF_NAV_INDEX, 0)

    fun saveTargetLocation(lat: Double, lng: Double, name: String) {
        prefs.edit().apply {
            putLong(AppConstants.PREF_NAV_TARGET_LAT, java.lang.Double.doubleToRawLongBits(lat))
            putLong(AppConstants.PREF_NAV_TARGET_LNG, java.lang.Double.doubleToRawLongBits(lng))
            putString(AppConstants.PREF_NAV_TARGET_NAME, name)
            apply()
        }
    }

    fun clearNavigationState() {
        prefs.edit().apply {
            remove(AppConstants.PREF_NAV_ROUTE)
            remove(AppConstants.PREF_NAV_ROUTE_ID)
            remove(AppConstants.PREF_NAV_INDEX)
            remove(AppConstants.PREF_NAV_TARGET_LAT)
            remove(AppConstants.PREF_NAV_TARGET_LNG)
            remove(AppConstants.PREF_NAV_TARGET_NAME)
            apply()
        }
    }
}
