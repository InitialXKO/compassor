package com.growsnova.compassor.manager

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject

@ActivityScoped
class SensorOrientationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private var smoothedAzimuth = 0f
    private val alphaFilter = 0.15f

    fun getOrientationFlow(delay: Int = SensorManager.SENSOR_DELAY_UI): Flow<Float> = callbackFlow {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.let {
                    when (it.sensor.type) {
                        Sensor.TYPE_ACCELEROMETER -> System.arraycopy(it.values, 0, gravity, 0, it.values.size)
                        Sensor.TYPE_MAGNETIC_FIELD -> System.arraycopy(it.values, 0, geomagnetic, 0, it.values.size)
                    }

                    if (SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) {
                        SensorManager.getOrientation(rotationMatrix, orientation)
                        var azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
                        if (azimuth < 0) azimuth += 360f

                        smoothedAzimuth = smoothRotation(smoothedAzimuth, azimuth, alphaFilter)
                        trySend(smoothedAzimuth)
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        accelerometer?.let { sensorManager.registerListener(listener, it, delay) }
        magnetometer?.let { sensorManager.registerListener(listener, it, delay) }

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }

    private fun smoothRotation(current: Float, target: Float, alpha: Float): Float {
        var diff = target - current
        if (diff > 180) diff -= 360
        else if (diff < -180) diff += 360
        return current + alpha * diff
    }
}
