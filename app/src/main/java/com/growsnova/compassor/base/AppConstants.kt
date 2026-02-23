package com.growsnova.compassor.base

object AppConstants {
    const val PREFS_NAME = "CompassorPrefs"

    // Preference Keys
    const val PREF_SKIN_NAME = "SkinName"
    const val PREF_THEME_MODE = "ThemeMode"
    const val PREF_NAV_ROUTE_ID = "NavRouteId"
    const val PREF_NAV_INDEX = "NavIndex"
    const val PREF_NAV_TARGET_LAT = "NavTargetLat"
    const val PREF_NAV_TARGET_LNG = "NavTargetLng"
    const val PREF_NAV_TARGET_NAME = "NavTargetName"

    // Location & Navigation
    const val LOCATION_UPDATE_INTERVAL = 3000L
    const val LOCATION_UPDATE_MIN_DISTANCE = 5f
    const val NAVIGATION_PROXIMITY_THRESHOLD = 20f
    const val FOLLOW_MODE_THRESHOLD = 5f
    const val POI_SEARCH_RADIUS = 10000 // 10km

    // Permission Codes
    const val LOCATION_PERMISSION_REQUEST_CODE = 1001
}
