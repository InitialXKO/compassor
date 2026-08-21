package com.growsnova.compassor

import android.app.Application
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.growsnova.compassor.base.AppConstants
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CompassorApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        setupGlobalExceptionHandler()
        applySavedThemeMode()
    }

    private fun applySavedThemeMode() {
        val prefs = getSharedPreferences(AppConstants.PREFS_NAME, MODE_PRIVATE)
        val themeMode = prefs.getInt(AppConstants.PREF_THEME_MODE, -1)
        if (themeMode != -1) {
            AppCompatDelegate.setDefaultNightMode(themeMode)
        }
    }

    private fun setupGlobalExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("CompassorApp", "Uncaught exception in thread ${thread.name}", throwable)
            // Here you could add logic to report the crash to a service or show a custom error UI
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
