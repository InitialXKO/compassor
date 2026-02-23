package com.growsnova.compassor.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.amap.api.maps.model.LatLng
import com.growsnova.compassor.base.AppConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NavigationRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)

    fun saveNavigationState(routeId: Long, index: Int) {
        prefs.edit().apply {
            putLong(AppConstants.PREF_NAV_ROUTE_ID, routeId)
            putInt(AppConstants.PREF_NAV_INDEX, index)
            remove(AppConstants.PREF_NAV_TARGET_LAT)
            remove(AppConstants.PREF_NAV_TARGET_LNG)
            remove(AppConstants.PREF_NAV_TARGET_NAME)
        }.apply()
    }

    fun saveTargetState(latLng: LatLng, name: String?) {
        prefs.edit().apply {
            putLong(AppConstants.PREF_NAV_TARGET_LAT, java.lang.Double.doubleToRawLongBits(latLng.latitude))
            putLong(AppConstants.PREF_NAV_TARGET_LNG, java.lang.Double.doubleToRawLongBits(latLng.longitude))
            putString(AppConstants.PREF_NAV_TARGET_NAME, name)
            remove(AppConstants.PREF_NAV_ROUTE_ID)
            remove(AppConstants.PREF_NAV_INDEX)
        }.apply()
    }

    fun clearNavigationState() {
        prefs.edit().apply {
            remove(AppConstants.PREF_NAV_ROUTE_ID)
            remove(AppConstants.PREF_NAV_INDEX)
            remove(AppConstants.PREF_NAV_TARGET_LAT)
            remove(AppConstants.PREF_NAV_TARGET_LNG)
            remove(AppConstants.PREF_NAV_TARGET_NAME)
        }.apply()
    }

    fun getNavRouteId(): Long = prefs.getLong(AppConstants.PREF_NAV_ROUTE_ID, -1L)
    fun getNavIndex(): Int = prefs.getInt(AppConstants.PREF_NAV_INDEX, 0)

    fun getNavTargetLatLng(): LatLng? {
        val latBits = prefs.getLong(AppConstants.PREF_NAV_TARGET_LAT, 0L)
        val lngBits = prefs.getLong(AppConstants.PREF_NAV_TARGET_LNG, 0L)
        if (latBits == 0L || lngBits == 0L) return null
        return LatLng(
            java.lang.Double.longBitsToDouble(latBits),
            java.lang.Double.longBitsToDouble(lngBits)
        )
    }

    fun getNavTargetName(): String? = prefs.getString(AppConstants.PREF_NAV_TARGET_NAME, null)

    fun saveSkinName(skinName: String) {
        prefs.edit().putString(AppConstants.PREF_SKIN_NAME, skinName).apply()
    }

    fun getSkinName(): String = prefs.getString(AppConstants.PREF_SKIN_NAME, "Default") ?: "Default"

    fun saveThemeMode(mode: Int) {
        prefs.edit().putInt(AppConstants.PREF_THEME_MODE, mode).apply()
    }

    fun getThemeMode(): Int = prefs.getInt(AppConstants.PREF_THEME_MODE, -1)
}
