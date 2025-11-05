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

class RadarCompassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), SensorEventListener {

    // 位置数据
    private var myLocation: LatLng = LatLng(0.0, 0.0)
    private var targetLocation: LatLng = LatLng(0.0, 0.0)
    private var distance: Float = 0.0f
    private var bearing: Float = 0.0f // 目标方位角
    private var deviceAzimuth: Float = 0.0f // 设备朝向角

    // 传感器
    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    // 画笔
    private val radarBackgroundPaint = Paint().apply {
        color = Color.parseColor("#1A1A1A")
        style = Paint.Style.FILL
    }

    private val radarCirclePaint = Paint().apply {
        color = Color.parseColor("#00FF00")
        style = Paint.Style.STROKE
        strokeWidth = 2f
        alpha = 100
    }

    private val radarLinePaint = Paint().apply {
        color = Color.parseColor("#00FF00")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val targetPaint = Paint().apply {
        color = Color.parseColor("#FF0000")
        style = Paint.Style.FILL
    }

    private val targetLinePaint = Paint().apply {
        color = Color.parseColor("#FFFF00")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 40f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    private val smallTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 32f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    private val directionTextPaint = Paint().apply {
        color = Color.parseColor("#00FF00")
        textSize = 28f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    init {
        // 注册传感器监听
        registerSensors()
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
                Sensor.TYPE_ACCELEROMETER -> {
                    System.arraycopy(it.values, 0, gravity, 0, it.values.size)
                }
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    System.arraycopy(it.values, 0, geomagnetic, 0, it.values.size)
                }
            }

            // 计算旋转矩阵和方向
            if (SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) {
                SensorManager.getOrientation(rotationMatrix, orientation)
                deviceAzimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
                if (deviceAzimuth < 0) {
                    deviceAzimuth += 360f
                }
                invalidate() // 触发重绘
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 不需要处理
    }

    fun updateTarget(myLoc: LatLng, targetLoc: LatLng) {
        this.myLocation = myLoc
        this.targetLocation = targetLoc
        this.distance = calculateDistance(myLoc, targetLoc)
        this.bearing = calculateBearing(myLoc, targetLoc)
        invalidate() // 触发重绘
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = minOf(centerX, centerY) * 0.7f

        // 1. 绘制背景
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), radarBackgroundPaint)

        // 2. 绘制同心圆雷达背景
        for (i in 1..4) {
            val circleRadius = radius * i / 4
            canvas.drawCircle(centerX, centerY, circleRadius, radarCirclePaint)
        }

        // 3. 绘制十字扫描线
        canvas.drawLine(centerX, centerY - radius, centerX, centerY + radius, radarLinePaint)
        canvas.drawLine(centerX - radius, centerY, centerX + radius, centerY, radarLinePaint)

        // 4. 绘制方向标识（N, S, E, W）
        drawDirectionMarkers(canvas, centerX, centerY, radius)

        // 5. 计算相对方位角（目标方位 - 设备朝向）
        var relativeBearing = bearing - deviceAzimuth
        if (relativeBearing < 0) relativeBearing += 360f
        if (relativeBearing >= 360) relativeBearing -= 360f

        // 6. 根据相对方位角绘制目标指示线
        val radian = Math.toRadians(relativeBearing.toDouble())
        val targetX = centerX + radius * 0.8f * sin(radian).toFloat()
        val targetY = centerY - radius * 0.8f * cos(radian).toFloat()

        // 绘制指向目标的线
        canvas.drawLine(centerX, centerY, targetX, targetY, targetLinePaint)

        // 绘制目标点
        canvas.drawCircle(targetX, targetY, 20f, targetPaint)

        // 7. 显示距离信息
        val distanceText = formatDistance(distance)
        canvas.drawText(distanceText, centerX, centerY + radius + 60, textPaint)

        // 8. 显示方位角信息
        val bearingText = "方位: ${bearing.toInt()}°"
        canvas.drawText(bearingText, centerX, centerY + radius + 100, smallTextPaint)

        // 9. 显示相对方位
        val relativeText = "相对: ${relativeBearing.toInt()}°"
        canvas.drawText(relativeText, centerX, centerY + radius + 140, smallTextPaint)
    }

    private fun drawDirectionMarkers(canvas: Canvas, centerX: Float, centerY: Float, radius: Float) {
        val textRadius = radius + 30

        // 北 (N) - 考虑设备朝向
        var northAngle = -deviceAzimuth
        var northRad = Math.toRadians(northAngle.toDouble())
        var northX = centerX + textRadius * sin(northRad).toFloat()
        var northY = centerY - textRadius * cos(northRad).toFloat()
        canvas.drawText("N", northX, northY, directionTextPaint)

        // 东 (E)
        var eastAngle = 90 - deviceAzimuth
        var eastRad = Math.toRadians(eastAngle.toDouble())
        var eastX = centerX + textRadius * sin(eastRad).toFloat()
        var eastY = centerY - textRadius * cos(eastRad).toFloat()
        canvas.drawText("E", eastX, eastY, directionTextPaint)

        // 南 (S)
        var southAngle = 180 - deviceAzimuth
        var southRad = Math.toRadians(southAngle.toDouble())
        var southX = centerX + textRadius * sin(southRad).toFloat()
        var southY = centerY - textRadius * cos(southRad).toFloat()
        canvas.drawText("S", southX, southY, directionTextPaint)

        // 西 (W)
        var westAngle = 270 - deviceAzimuth
        var westRad = Math.toRadians(westAngle.toDouble())
        var westX = centerX + textRadius * sin(westRad).toFloat()
        var westY = centerY - textRadius * cos(westRad).toFloat()
        canvas.drawText("W", westX, westY, directionTextPaint)
    }

    private fun formatDistance(distanceInMeters: Float): String {
        return when {
            distanceInMeters < 1000 -> "距离: %.0f米".format(distanceInMeters)
            distanceInMeters < 10000 -> "距离: %.2f公里".format(distanceInMeters / 1000)
            else -> "距离: %.1f公里".format(distanceInMeters / 1000)
        }
    }

    // 计算两点间距离（米）
    private fun calculateDistance(from: LatLng, to: LatLng): Float {
        val results = FloatArray(1)
        Location.distanceBetween(
            from.latitude, from.longitude,
            to.latitude, to.longitude,
            results
        )
        return results[0]
    }

    // 计算从from指向to的方位角（正北为0度，顺时针增加）
    private fun calculateBearing(from: LatLng, to: LatLng): Float {
        val results = FloatArray(2)
        Location.distanceBetween(
            from.latitude, from.longitude,
            to.latitude, to.longitude,
            results
        )
        var bearing = results[1]
        if (bearing < 0) bearing += 360f
        return bearing
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        registerSensors()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        unregisterSensors()
    }
}
