package com.growsnova.compassor

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CompassorApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        setupGlobalExceptionHandler()
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
