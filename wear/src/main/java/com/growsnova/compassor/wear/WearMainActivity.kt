package com.growsnova.compassor.wear

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.growsnova.compassor.common.WearConstants

class WearMainActivity : AppCompatActivity(), DataClient.OnDataChangedListener, SensorEventListener {

    private lateinit var radarContent: View
    private lateinit var radarCompassView: WearRadarCompassView
    private lateinit var arrowCompassView: WearArrowCompassView
    private lateinit var targetText: TextView
    private var isFlipped = false

    private lateinit var sensorManager: SensorManager
    private var rotationSensor: Sensor? = null
    private var watchAzimuth = 0f
    private var targetBearing = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wear_main)

        radarContent = findViewById(R.id.wearRadarContent)
        radarCompassView = findViewById(R.id.wearRadarCompassView)
        arrowCompassView = findViewById(R.id.wearArrowCompassView)
        targetText = findViewById(R.id.wearTargetText)

        radarContent.setOnClickListener { flipCard() }

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        // Restore saved skin
        val savedSkinKey = getSharedPreferences("wear_prefs", MODE_PRIVATE).getString("skin_key", "Default") ?: "Default"
        applySkinKey(savedSkinKey)
    }

    private fun applySkinKey(skinKey: String) {
        val skin = WearSkins.getSkinByName(skinKey)
        radarCompassView.setSkin(skin)
        arrowCompassView.setSkin(skin)
        radarContent.setBackgroundColor(skin.backgroundColor)
        targetText.setTextColor(skin.distanceTextColor)

        getSharedPreferences("wear_prefs", MODE_PRIVATE).edit().putString("skin_key", skinKey).apply()
    }

    override fun onResume() {
        super.onResume()
        Wearable.getDataClient(this).addListener(this)
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        Wearable.getDataClient(this).removeListener(this)
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return

        val rotationMatrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        val orientation = FloatArray(3)
        SensorManager.getOrientation(rotationMatrix, orientation)

        // Azimuth in degrees [0, 360)
        watchAzimuth = (Math.toDegrees(orientation[0].toDouble()).toFloat() + 360f) % 360f

        radarCompassView.setAzimuth(watchAzimuth)
        arrowCompassView.setAzimuth(watchAzimuth)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                val dataItem = event.dataItem
                if (dataItem.uri.path == WearConstants.PATH_NAVIGATION) {
                    val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
                    val targetName = dataMap.getString(WearConstants.KEY_TARGET_NAME) ?: "目的地"
                    val distance = dataMap.getFloat(WearConstants.KEY_DISTANCE, -1f)
                    val bearing = dataMap.getFloat(WearConstants.KEY_BEARING, 0f)
                    val skinKey = dataMap.getString(WearConstants.KEY_SKIN_KEY)

                    runOnUiThread {
                        skinKey?.let { applySkinKey(it) }
                        updateNavigationData(targetName, distance, bearing)
                    }
                }
            }
        }
    }

    private fun updateNavigationData(targetName: String, distance: Float, bearing: Float) {
        targetText.text = targetName
        this.targetBearing = bearing

        if (distance >= 0) {
            radarCompassView.updateTarget(distance, bearing)
            arrowCompassView.updateTarget(distance, bearing)
        } else {
            radarCompassView.clearTarget()
            arrowCompassView.clearTarget()
        }
    }

    private fun flipCard() {
        val root = radarContent
        val front = radarCompassView
        val back = arrowCompassView
        root.cameraDistance = 8000 * resources.displayMetrics.density

        val outAnim = ObjectAnimator.ofFloat(root, "rotationY", if (isFlipped) 180f else 0f, 90f)
        val inAnim = ObjectAnimator.ofFloat(root, "rotationY", if (isFlipped) -90f else 270f, if (isFlipped) 0f else 180f)

        outAnim.duration = 150
        inAnim.duration = 150

        outAnim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (isFlipped) {
                    back.visibility = View.GONE
                    front.visibility = View.VISIBLE
                    root.rotationY = 0f
                    front.scaleX = 1f
                } else {
                    front.visibility = View.GONE
                    back.visibility = View.VISIBLE
                    root.rotationY = 180f
                    back.scaleX = -1f
                }
                isFlipped = !isFlipped
                inAnim.start()
            }
        })
        outAnim.start()
    }
}
