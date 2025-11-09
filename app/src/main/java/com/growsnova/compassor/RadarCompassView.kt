package com.growsnova.compassor

import android.animation.ValueAnimator
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

class RadarCompassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), SensorEventListener {

    // Location data
    private var myLocation: LatLng = LatLng(0.0, 0.0)
    private var targetLocation: LatLng = LatLng(0.0, 0.0)
    private var distance: Float = 0.0f
    private var bearing: Float = 0.0f
    private var deviceAzimuth: Float = 0.0f

    // Sensors
    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    // New properties for animations and refined drawing
    private var scannerAnimator: ValueAnimator? = null
    private var pulseAnimator: ValueAnimator? = null
    private var scannerAngle = 0f
    private var pulseRadius = 0f
    private var pulseAlpha = 0

    private var skin: RadarSkin = RadarSkin()

    // Paint objects
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val compassRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        alpha = 180
    }
    private val innerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        alpha = 120
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        alpha = 50
    }
    private val scannerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val crosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val targetRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val targetLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        alpha = 200
        pathEffect = DashPathEffect(floatArrayOf(10f, 5f), 0f)
    }
    private val distanceTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 48f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val infoTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }
    private val directionTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 36f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    init {
        setSkin(this.skin)
        registerSensors()
        startScannerAnimation()
        startPulseAnimation()
    }

    fun setSkin(skin: RadarSkin) {
        this.skin = skin
        backgroundPaint.color = skin.backgroundColor
        compassRingPaint.color = skin.compassRingColor
        innerRingPaint.color = skin.innerRingColor
        gridPaint.color = skin.innerRingColor
        crosshairPaint.color = skin.crosshairColor
        targetPaint.color = skin.targetColor
        targetRingPaint.color = skin.targetRingColor
        targetLinePaint.color = skin.targetLineColor
        distanceTextPaint.color = skin.distanceTextColor
        infoTextPaint.color = skin.infoTextColor
        directionTextPaint.color = skin.directionTextColor
        tickPaint.color = skin.tickColor
        invalidate()
    }

    private fun registerSensors() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        magnetometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    private fun unregisterSensors() {
        sensorManager.unregisterListener(this)
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

                // Smooth the azimuth value
                var diff = azimuth - deviceAzimuth
                if (diff > 180) diff -= 360
                if (diff < -180) diff += 360

                deviceAzimuth += diff * 0.4f // Damping factor
                if (deviceAzimuth < 0) deviceAzimuth += 360
                if (deviceAzimuth >= 360) deviceAzimuth -= 360

                invalidate() // Trigger redraw
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed
    }

    fun updateTarget(myLoc: LatLng, targetLoc: LatLng) {
        this.myLocation = myLoc
        this.targetLocation = targetLoc
        this.distance = calculateDistance(myLoc, targetLoc)
        this.bearing = calculateBearing(myLoc, targetLoc)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = minOf(centerX, centerY) * 0.7f

        drawBackground(canvas, centerX, centerY, radius)
        drawGrid(canvas, centerX, centerY, radius)
        drawScanner(canvas, centerX, centerY, radius)
        drawCompassRing(canvas, centerX, centerY, radius)
        drawCrosshair(canvas, centerX, centerY)
        drawDirectionMarkers(canvas, centerX, centerY, radius)

        var relativeBearing = bearing - deviceAzimuth
        if (relativeBearing < 0) relativeBearing += 360f
        if (relativeBearing >= 360) relativeBearing -= 360f

        if (distance > 0) {
            drawTargetWaypoint(canvas, centerX, centerY, radius, relativeBearing)
        }

        drawInfoPanel(canvas, centerX, centerY, radius, relativeBearing)
    }

    private fun drawBackground(canvas: Canvas, centerX: Float, centerY: Float, radius: Float) {
        val centerColor = skin.backgroundColor
        val edgeColor = Color.argb(
            Color.alpha(centerColor),
            (Color.red(centerColor) * 0.8).toInt(),
            (Color.green(centerColor) * 0.8).toInt(),
            (Color.blue(centerColor) * 0.8).toInt()
        )
        backgroundPaint.shader = RadialGradient(centerX, centerY, radius, centerColor, edgeColor, Shader.TileMode.CLAMP)
        canvas.drawCircle(centerX, centerY, radius * 1.5f, backgroundPaint)
    }

    private fun drawGrid(canvas: Canvas, centerX: Float, centerY: Float, radius: Float) {
        gridPaint.alpha = 50
        canvas.drawCircle(centerX, centerY, radius * 0.25f, gridPaint)
        canvas.drawCircle(centerX, centerY, radius * 0.5f, gridPaint)
        canvas.drawCircle(centerX, centerY, radius * 0.75f, gridPaint)
    }

    private fun drawScanner(canvas: Canvas, centerX: Float, centerY: Float, radius: Float) {
        val scannerColor = Color.argb(100, Color.red(skin.compassRingColor), Color.green(skin.compassRingColor), Color.blue(skin.compassRingColor))
        scannerPaint.shader = SweepGradient(centerX, centerY, Color.TRANSPARENT, scannerColor)
        canvas.save()
        canvas.rotate(scannerAngle - 90, centerX, centerY) // Offset by -90 to start from top
        canvas.drawCircle(centerX, centerY, radius, scannerPaint)
        canvas.restore()
    }

    private fun drawCompassRing(canvas: Canvas, centerX: Float, centerY: Float, radius: Float) {
        canvas.drawCircle(centerX, centerY, radius, compassRingPaint)
        canvas.drawCircle(centerX, centerY, radius * 0.85f, innerRingPaint)

        for (angle in 0 until 360 step 10) {
            val adjustedAngle = angle - deviceAzimuth
            val radian = Math.toRadians(adjustedAngle.toDouble())
            val (startRadius, paint) = if (angle % 90 == 0) {
                Pair(radius * 0.92f, compassRingPaint)
            } else {
                Pair(radius * 0.95f, tickPaint)
            }
            val endRadius = radius
            val startX = centerX + startRadius * sin(radian).toFloat()
            val startY = centerY - startRadius * cos(radian).toFloat()
            val endX = centerX + endRadius * sin(radian).toFloat()
            val endY = centerY - endRadius * cos(radian).toFloat()
            canvas.drawLine(startX, startY, endX, endY, paint)
        }
    }

    private fun drawCrosshair(canvas: Canvas, centerX: Float, centerY: Float) {
        val crossSize = 25f
        val gap = 8f
        canvas.drawLine(centerX, centerY - gap, centerX, centerY - gap - crossSize, crosshairPaint)
        canvas.drawLine(centerX, centerY + gap, centerX, centerY + gap + crossSize, crosshairPaint)
        canvas.drawLine(centerX - gap, centerY, centerX - gap - crossSize, centerY, crosshairPaint)
        canvas.drawLine(centerX + gap, centerY, centerX + gap + crossSize, centerY, crosshairPaint)
        canvas.drawCircle(centerX, centerY, 3f, crosshairPaint)
    }

    private fun drawTargetWaypoint(canvas: Canvas, centerX: Float, centerY: Float, radius: Float, relativeBearing: Float) {
        val radian = Math.toRadians(relativeBearing.toDouble())
        val targetRadius = radius * 0.75f
        val targetX = centerX + targetRadius * sin(radian).toFloat()
        val targetY = centerY - targetRadius * cos(radian).toFloat()

        canvas.drawLine(centerX, centerY, targetX, targetY, targetLinePaint)

        targetRingPaint.alpha = pulseAlpha
        canvas.drawCircle(targetX, targetY, 12f + pulseRadius, targetRingPaint)

        canvas.drawCircle(targetX, targetY, 12f, targetPaint)

        val distanceText = if (distance < 1000) "${distance.toInt()}m" else "%.1fkm".format(distance / 1000)
        canvas.drawText(distanceText, targetX, targetY - 35f, distanceTextPaint)
    }

    private fun drawInfoPanel(canvas: Canvas, centerX: Float, centerY: Float, radius: Float, relativeBearing: Float) {
        val infoY = centerY + radius + 80
        canvas.drawText(formatDistance(distance), centerX, infoY, distanceTextPaint)
        canvas.drawText("${bearing.toInt()}°", centerX, infoY + 50, infoTextPaint)
        canvas.drawText(getRelativeDirection(relativeBearing), centerX, infoY + 85, infoTextPaint)
    }

    private fun getRelativeDirection(angle: Float): String {
        return when {
            angle < 22.5 || angle >= 337.5 -> "正前方"
            angle < 67.5 -> "右前方"
            angle < 112.5 -> "右侧"
            angle < 157.5 -> "右后方"
            angle < 202.5 -> "正后方"
            angle < 247.5 -> "左后方"
            angle < 292.5 -> "左侧"
            else -> "左前方"
        }
    }

    private fun drawDirectionMarkers(canvas: Canvas, centerX: Float, centerY: Float, radius: Float) {
        val textRadius = radius + 45
        val directions = listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f)

        for ((direction, baseAngle) in directions) {
            val adjustedAngle = baseAngle - deviceAzimuth
            val radian = Math.toRadians(adjustedAngle.toDouble())
            val x = centerX + textRadius * sin(radian).toFloat()
            val y = centerY - textRadius * cos(radian).toFloat() + 12f

            directionTextPaint.color = if (direction == "N") Color.parseColor("#58A6FF") else Color.parseColor("#8B949E")
            directionTextPaint.alpha = if (direction == "N") 255 else 180

            canvas.drawText(direction, x, y, directionTextPaint)
        }
    }

    private fun formatDistance(distanceInMeters: Float): String {
        return when {
            distanceInMeters < 1000 -> "距离: %.0f米".format(distanceInMeters)
            distanceInMeters < 10000 -> "距离: %.2f公里".format(distanceInMeters / 1000)
            else -> "距离: %.1f公里".format(distanceInMeters / 1000)
        }
    }

    private fun calculateDistance(from: LatLng, to: LatLng): Float {
        val results = FloatArray(1)
        Location.distanceBetween(from.latitude, from.longitude, to.latitude, to.longitude, results)
        return results[0]
    }

    private fun calculateBearing(from: LatLng, to: LatLng): Float {
        val results = FloatArray(2)
        Location.distanceBetween(from.latitude, from.longitude, to.latitude, to.longitude, results)
        var bearingValue = results[1]
        if (bearingValue < 0) bearingValue += 360f
        return bearingValue
    }

    private fun startScannerAnimation() {
        scannerAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 3000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            addUpdateListener { animation ->
                scannerAngle = animation.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun startPulseAnimation() {
        pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            addUpdateListener { animation ->
                val fraction = animation.animatedValue as Float
                pulseRadius = 20f * fraction
                pulseAlpha = (255 * (1 - fraction)).toInt()
                invalidate()
            }
            start()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        registerSensors()
        scannerAnimator?.start()
        pulseAnimator?.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        unregisterSensors()
        scannerAnimator?.cancel()
        pulseAnimator?.cancel()
    }
}
