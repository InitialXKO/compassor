package com.growsnova.compassor.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.growsnova.compassor.common.WearConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WearDataSender @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "WearDataSender"
    }

    private val dataClient by lazy { Wearable.getDataClient(context) }

    fun sendNavigationData(targetName: String, distance: Float, bearing: Float, skinKey: String? = null) {
        try {
            val putDataMapReq = PutDataMapRequest.create(WearConstants.PATH_NAVIGATION)
            putDataMapReq.dataMap.putString(WearConstants.KEY_TARGET_NAME, targetName)
            putDataMapReq.dataMap.putFloat(WearConstants.KEY_DISTANCE, distance)
            putDataMapReq.dataMap.putFloat(WearConstants.KEY_BEARING, bearing)
            skinKey?.let { putDataMapReq.dataMap.putString(WearConstants.KEY_SKIN_KEY, it) }
            putDataMapReq.dataMap.putLong(WearConstants.KEY_TIMESTAMP, System.currentTimeMillis())

            val putDataReq = putDataMapReq.asPutDataRequest().setUrgent()
            dataClient.putDataItem(putDataReq)
                .addOnSuccessListener { Log.d(TAG, "Wear DataLayer updated successfully") }
                .addOnFailureListener { e -> Log.e(TAG, "Failed to update Wear DataLayer", e) }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending navigation data to Wear OS", e)
        }
    }

    fun sendSkinKey(skinKey: String) {
        try {
            val putDataMapReq = PutDataMapRequest.create(WearConstants.PATH_NAVIGATION)
            putDataMapReq.dataMap.putString(WearConstants.KEY_SKIN_KEY, skinKey)
            putDataMapReq.dataMap.putLong(WearConstants.KEY_TIMESTAMP, System.currentTimeMillis())

            val putDataReq = putDataMapReq.asPutDataRequest().setUrgent()
            dataClient.putDataItem(putDataReq)
                .addOnSuccessListener { Log.d(TAG, "Wear DataLayer skin updated to $skinKey") }
                .addOnFailureListener { e -> Log.e(TAG, "Failed to update Wear DataLayer skin", e) }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending skin key to Wear OS", e)
        }
    }
}
