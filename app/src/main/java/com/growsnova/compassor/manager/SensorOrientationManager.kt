package com.growsnova.compassor.manager

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SensorOrientationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    @Volatile
    private var declination = 0f

    private var smoothedAzimuth = 0f
    private val alphaFilter = 0.15f

    // Complementary filter state
    private var lastGyroTimestamp = 0L
    private var fusedAzimuth = 0f
    private var isFusedAzimuthInitialized = false
    private val gyroAlpha = 0.92f // Complementary filter weight for gyro

    fun updateLocation(location: Location) {
        try {
            val geoField = GeomagneticField(
                location.latitude.toFloat(),
                location.longitude.toFloat(),
                location.altitude.toFloat(),
                if (location.time > 0) location.time else System.currentTimeMillis()
            )
            declination = geoField.declination
        } catch (e: Exception) {
            // Keep existing declination on exception
        }
    }

    fun getOrientationFlow(delay: Int = SensorManager.SENSOR_DELAY_UI): Flow<Float> = callbackFlow {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event ?: return

                var rawAzimuth: Float? = null

                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        SensorManager.getOrientation(rotationMatrix, orientation)
                        var azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
                        azimuth += declination
                        rawAzimuth = normalizeDegree(azimuth)
                    }

                    Sensor.TYPE_ACCELEROMETER -> {
                        System.arraycopy(event.values, 0, gravity, 0, event.values.size)
                        processAccelMag()?.let { rawAzimuth = it }
                    }

                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        System.arraycopy(event.values, 0, geomagnetic, 0, event.values.size)
                        processAccelMag()?.let { rawAzimuth = it }
                    }

                    Sensor.TYPE_GYROSCOPE -> {
                        if (rotationVectorSensor == null && accelerometerSensor != null && magnetometerSensor != null) {
                            processGyro(event)?.let { rawAzimuth = it }
                        }
                    }
                }

                rawAzimuth?.let { azimuth ->
                    smoothedAzimuth = smoothRotation(smoothedAzimuth, azimuth, alphaFilter)
                    trySend(smoothedAzimuth)
                }
            }

            private fun processAccelMag(): Float? {
                if (SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) {
                    SensorManager.getOrientation(rotationMatrix, orientation)
                    var azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
                    azimuth += declination
                    val normAzimuth = normalizeDegree(azimuth)

                    if (!isFusedAzimuthInitialized) {
                        fusedAzimuth = normAzimuth
                        isFusedAzimuthInitialized = true
                    } else if (gyroscopeSensor != null) {
                        var diff = normAzimuth - fusedAzimuth
                        if (diff > 180) diff -= 360
                        else if (diff < -180) diff += 360
                        fusedAzimuth = normalizeDegree(fusedAzimuth + (1f - gyroAlpha) * diff)
                    } else {
                        fusedAzimuth = normAzimuth
                    }
                    return fusedAzimuth
                }
                return null
            }

            private fun processGyro(event: SensorEvent): Float? {
                if (!isFusedAzimuthInitialized) return null
                if (lastGyroTimestamp != 0L) {
                    val dt = (event.timestamp - lastGyroTimestamp) * 1.0e-9f // seconds
                    val gyroZ = Math.toDegrees(event.values[2].toDouble()).toFloat()
                    val deltaAzimuth = -gyroZ * dt
                    val predictedAzimuth = normalizeDegree(fusedAzimuth + deltaAzimuth)

                    var diff = predictedAzimuth - fusedAzimuth
                    if (diff > 180) diff -= 360
                    else if (diff < -180) diff += 360

                    fusedAzimuth = normalizeDegree(fusedAzimuth + gyroAlpha * diff)
                }
                lastGyroTimestamp = event.timestamp
                return fusedAzimuth
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        if (rotationVectorSensor != null) {
            sensorManager.registerListener(listener, rotationVectorSensor, delay)
        } else {
            accelerometerSensor?.let { sensorManager.registerListener(listener, it, delay) }
            magnetometerSensor?.let { sensorManager.registerListener(listener, it, delay) }
            gyroscopeSensor?.let { sensorManager.registerListener(listener, it, delay) }
        }

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }

    private fun normalizeDegree(degree: Float): Float {
        var deg = degree % 360f
        if (deg < 0) deg += 360f
        return deg
    }

    private fun smoothRotation(current: Float, target: Float, alpha: Float): Float {
        var diff = target - current
        if (diff > 180) diff -= 360
        else if (diff < -180) diff += 360
        return normalizeDegree(current + alpha * diff)
    }
}
