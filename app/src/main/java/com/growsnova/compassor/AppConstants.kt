package com.growsnova.compassor

object AppConstants {
    const val PREFS_NAME = "CompassorPrefs"
    const val PREF_SKIN_NAME = "SkinName"
    const val PREF_THEME_MODE = "ThemeMode"
    const val PREF_NAV_ROUTE = "NavRoute"
    const val PREF_NAV_ROUTE_ID = "NavRouteId"
    const val PREF_NAV_INDEX = "NavIndex"
    const val PREF_NAV_TARGET_LAT = "NavTargetLat"
    const val PREF_NAV_TARGET_LNG = "NavTargetLng"
    const val PREF_NAV_TARGET_NAME = "NavTargetName"

    const val LOCATION_PERMISSION_REQUEST_CODE = 1001

    // Default values
    const val DEFAULT_TARGET_NAME = "目的地"

    // Navigation constants
    const val ARRIVAL_RADIUS_METERS = 15f
    const val FOLLOW_MODE_THRESHOLD_METERS = 5f
    const val NEARBY_POI_RADIUS_METERS = 10000 // 10km
    const val ROUTE_WIDTH = 12f
    const val TRAVELED_ROUTE_WIDTH = 10f
}
