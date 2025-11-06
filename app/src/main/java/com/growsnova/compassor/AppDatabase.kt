package com.growsnova.compassor

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Waypoint::class, Route::class, RouteWaypointCrossRef::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun waypointDao(): WaypointDao
    abstract fun routeDao(): RouteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "compassor_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
