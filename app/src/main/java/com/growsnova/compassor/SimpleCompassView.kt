package com.growsnova.compassor

import android.content.Context
import android.graphics.*
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.util.AttributeSet
import android.view.View
import com.amap.api.maps.model.LatLng
import kotlin.math.cos
import kotlin.math.sin

class SimpleCompassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), SensorEventListener {

    private var myLocation: LatLng = LatLng(0.0, 0.0)
    private var targetLocation: LatLng = LatLng(0.0, 0.0)
    private var distance: Float = 0.0f
    private var bearing: Float = 0.0f
    private var deviceAzimuth: Float = 0.0f
    
    private val sensorManager: SensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private var smoothedAzimuth: Float = 0f
    private val alphaFilter = 0.15f

    private var skin: RadarSkin = RadarSkin()

    private val arrowPaint = Paint(Paint.ANTI_ALIAS_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val secondaryTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private val arrowPath = Path()

    init {
        // Initialize arrow shape
        arrowPath.moveTo(0f, -100f)
        arrowPath.lineTo(40f, 20f)
        arrowPath.lineTo(0f, 0f)
        arrowPath.lineTo(-40f, 20f)
        arrowPath.close()
    }

    fun setSkin(skin: RadarSkin) {
        this.skin = skin
        arrowPaint.color = skin.targetColor
        textPaint.color = skin.distanceTextColor
        secondaryTextPaint.color = skin.infoTextColor
        invalidate()
    }

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
                deviceAzimuth = smoothedAzimuth
                invalidate()
            }
        }
    }

    private fun smoothRotation(current: Float, target: Float, alpha: Float): Float {
        var diff = target - current
        if (diff > 180) diff -= 360
        else if (diff < -180) diff += 360
        return current + alpha * diff
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun updateTarget(myLoc: LatLng, targetLoc: LatLng) {
        this.myLocation = myLoc
        this.targetLocation = targetLoc
        val results = FloatArray(2)
        Location.distanceBetween(myLoc.latitude, myLoc.longitude, targetLoc.latitude, targetLoc.longitude, results)
        this.distance = results[0]
        this.bearing = if (results[1] < 0) results[1] + 360f else results[1]
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = minOf(centerX, centerY) * 0.8f

        // Draw distance
        textPaint.textSize = height * 0.15f
        val distStr = if (distance < 1000) "${distance.toInt()}m" else "%.1fkm".format(distance / 1000)
        canvas.drawText(distStr, centerX, centerY + height * 0.35f, textPaint)

        // Draw arrow
        var relativeBearing = bearing - deviceAzimuth
        canvas.save()
        canvas.translate(centerX, centerY - height * 0.1f)
        canvas.rotate(relativeBearing)
        val scale = height * 0.004f
        canvas.scale(scale, scale)
        canvas.drawPath(arrowPath, arrowPaint)
        canvas.restore()
        
        // Draw relative direction hint
        secondaryTextPaint.textSize = height * 0.08f
        val hint = getRelativeDirection(relativeBearing)
        canvas.drawText(hint, centerX, centerY + height * 0.45f, secondaryTextPaint)
    }

    private fun getRelativeDirection(angle: Float): String {
        var a = angle % 360
        if (a < 0) a += 360
        return when {
            a < 22.5 || a >= 337.5 -> "正前方"
            a < 67.5 -> "右前方"
            a < 112.5 -> "右侧"
            a < 157.5 -> "右后方"
            a < 202.5 -> "正后方"
            a < 247.5 -> "左后方"
            a < 292.5 -> "左侧"
            a < 337.5 -> "左前方"
            else -> ""
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        magnetometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        sensorManager.unregisterListener(this)
    }
}
