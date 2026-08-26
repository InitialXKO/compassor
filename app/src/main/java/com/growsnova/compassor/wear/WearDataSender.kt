package com.growsnova.compassor.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WearDataSender @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "WearDataSender"
        const val PATH_NAVIGATION = "/navigation_update"

        const val KEY_TARGET_NAME = "target_name"
        const val KEY_DISTANCE = "distance"
        const val KEY_BEARING = "bearing"
        const val KEY_AZIMUTH = "azimuth"
        const val KEY_TIMESTAMP = "timestamp"
    }

    private val dataClient by lazy { Wearable.getDataClient(context) }

    fun sendNavigationData(targetName: String, distance: Float, bearing: Float, azimuth: Float) {
        try {
            val putDataMapReq = PutDataMapRequest.create(PATH_NAVIGATION)
            putDataMapReq.dataMap.putString(KEY_TARGET_NAME, targetName)
            putDataMapReq.dataMap.putFloat(KEY_DISTANCE, distance)
            putDataMapReq.dataMap.putFloat(KEY_BEARING, bearing)
            putDataMapReq.dataMap.putFloat(KEY_AZIMUTH, azimuth)
            putDataMapReq.dataMap.putLong(KEY_TIMESTAMP, System.currentTimeMillis())

            val putDataReq = putDataMapReq.asPutDataRequest().setUrgent()
            dataClient.putDataItem(putDataReq)
                .addOnSuccessListener { Log.d(TAG, "Wear DataLayer updated successfully") }
                .addOnFailureListener { e -> Log.e(TAG, "Failed to update Wear DataLayer", e) }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending navigation data to Wear OS", e)
        }
    }
}
