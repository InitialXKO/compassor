package com.growsnova.compassor.wear

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.growsnova.compassor.common.WearConstants

class WearMainActivity : AppCompatActivity(), DataClient.OnDataChangedListener {

    private lateinit var targetText: TextView
    private lateinit var distanceText: TextView
    private lateinit var bearingText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wear_main)

        targetText = findViewById(R.id.wearTargetText)
        distanceText = findViewById(R.id.wearDistanceText)
        bearingText = findViewById(R.id.wearBearingText)
    }

    override fun onResume() {
        super.onResume()
        Wearable.getDataClient(this).addListener(this)
    }

    override fun onPause() {
        super.onPause()
        Wearable.getDataClient(this).removeListener(this)
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                val dataItem = event.dataItem
                if (dataItem.uri.path == WearConstants.PATH_NAVIGATION) {
                    val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
                    val targetName = dataMap.getString(WearConstants.KEY_TARGET_NAME) ?: "目的地"
                    val distance = dataMap.getFloat(WearConstants.KEY_DISTANCE, -1f)
                    val bearing = dataMap.getFloat(WearConstants.KEY_BEARING, 0f)
                    val azimuth = dataMap.getFloat(WearConstants.KEY_AZIMUTH, 0f)

                    runOnUiThread {
                        updateUI(targetName, distance, bearing, azimuth)
                    }
                }
            }
        }
    }

    private fun updateUI(targetName: String, distance: Float, bearing: Float, azimuth: Float) {
        targetText.text = targetName
        val distanceStr = if (distance < 0) {
            "距离: --"
        } else if (distance < 1000) {
            "距离: ${distance.toInt()}m"
        } else {
            "距离: %.1fkm".format(distance / 1000f)
        }
        distanceText.text = distanceStr
        bearingText.text = "方位角: %.0f°".format(azimuth)
    }
}
