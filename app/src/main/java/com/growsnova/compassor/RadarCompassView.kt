package com.growsnova.compassor

import android.content.Context
import android.graphics.*
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
) : View(context, attrs, defStyleAttr) {

    // 位置数据
    private var myLocation: LatLng = LatLng(0.0, 0.0)
    private var targetLocation: LatLng = LatLng(0.0, 0.0)
    private var distance: Float = 0.0f
    private var bearing: Float = 0.0f // 目标方位角
    private var deviceAzimuth: Float = 0.0f // 设备朝向角
    private var scanAngle: Float = 0.0f // 扫描线当前角度
    private var lastDrawTime: Long = 0

    private var skin: RadarSkin = RadarSkin()

    // 画笔
    private val backgroundPaint = Paint().apply { style = Paint.Style.FILL }
    private val scanPaint = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
    private val glowPaint = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
    private val compassRingPaint = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 4f; alpha = 180; isAntiAlias = true }
    private val innerRingPaint = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 2f; alpha = 120; isAntiAlias = true }
    private val crosshairPaint = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 3f; isAntiAlias = true }
    private val targetPaint = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
    private val targetRingPaint = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 3f; alpha = 150; isAntiAlias = true }
    private val targetLinePaint = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 3f; alpha = 200; isAntiAlias = true; pathEffect = DashPathEffect(floatArrayOf(10f, 5f), 0f) }
    private val distanceTextPaint = Paint().apply { textSize = 48f; textAlign = Paint.Align.CENTER; isAntiAlias = true; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    private val infoTextPaint = Paint().apply { textSize = 28f; textAlign = Paint.Align.CENTER; isAntiAlias = true }
    private val directionTextPaint = Paint().apply { textSize = 36f; textAlign = Paint.Align.CENTER; isAntiAlias = true; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    private val tickPaint = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 2f; isAntiAlias = true }

    init {
        setSkin(this.skin)
    }

    fun setSkin(skin: RadarSkin) {
        this.skin = skin
        backgroundPaint.color = skin.backgroundColor
        compassRingPaint.color = skin.compassRingColor
        innerRingPaint.color = skin.innerRingColor
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

    fun updateAzimuth(azimuth: Float) {
        this.deviceAzimuth = azimuth
        invalidate()
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
        val currentTime = System.currentTimeMillis()
        if (lastDrawTime > 0) {
            val elapsed = currentTime - lastDrawTime
            scanAngle = (scanAngle + elapsed * 0.1f) % 360f
        }
        lastDrawTime = currentTime

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = minOf(centerX, centerY) * 0.7f

        drawScanGlow(canvas, centerX, centerY, radius)
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
        postInvalidateOnAnimation()
    }

    private fun drawScanGlow(canvas: Canvas, centerX: Float, centerY: Float, radius: Float) {
        canvas.save()
        canvas.rotate(scanAngle, centerX, centerY)
        val colors = intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, skin.compassRingColor and 0x00FFFFFF or 0x10000000, skin.compassRingColor and 0x00FFFFFF or 0x30000000)
        val positions = floatArrayOf(0f, 0.7f, 0.9f, 1f)
        scanPaint.shader = SweepGradient(centerX, centerY, colors, positions)
        canvas.drawCircle(centerX, centerY, radius, scanPaint)
        canvas.restore()
    }

    private fun drawCompassRing(canvas: Canvas, centerX: Float, centerY: Float, radius: Float) {
        canvas.drawCircle(centerX, centerY, radius, compassRingPaint)
        canvas.drawCircle(centerX, centerY, radius * 0.85f, innerRingPaint)
        for (angle in 0 until 360 step 10) {
            val adjustedAngle = angle - deviceAzimuth
            val radian = Math.toRadians(adjustedAngle.toDouble())
            val startRadius: Float
            val endRadius: Float
            val paint: Paint
            if (angle % 90 == 0) {
                startRadius = radius * 0.92f
                endRadius = radius * 1.0f
                paint = compassRingPaint
            } else {
                startRadius = radius * 0.95f
                endRadius = radius * 1.0f
                paint = tickPaint
            }
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
        val pulse = (sin(System.currentTimeMillis() / 200.0) * 0.5 + 0.5).toFloat()
        val glowSize = 40f + 20f * pulse
        val colors = intArrayOf(skin.targetColor and 0x00FFFFFF or 0x60000000, Color.TRANSPARENT)
        glowPaint.shader = RadialGradient(targetX, targetY, glowSize, colors, null, Shader.TileMode.CLAMP)
        canvas.drawCircle(targetX, targetY, glowSize, glowPaint)
        canvas.drawCircle(targetX, targetY, 25f + 5f * pulse, targetRingPaint)
        canvas.drawCircle(targetX, targetY, 20f, targetRingPaint)
        canvas.drawCircle(targetX, targetY, 12f, targetPaint)
        val distanceText = if (distance < 1000) "${distance.toInt()}m" else "%.1fkm".format(distance / 1000)
        val textY = targetY - 45f - 5f * pulse
        canvas.drawText(distanceText, targetX, textY, distanceTextPaint)
    }

    private fun drawInfoPanel(canvas: Canvas, centerX: Float, centerY: Float, radius: Float, relativeBearing: Float) {
        val infoY = centerY + radius + 80
        val distanceText = formatDistance(distance)
        canvas.drawText(distanceText, centerX, infoY, distanceTextPaint)
        val bearingText = "${bearing.toInt()}°"
        canvas.drawText(bearingText, centerX, infoY + 50, infoTextPaint)
        val relativeText = getRelativeDirection(relativeBearing)
        canvas.drawText(relativeText, centerX, infoY + 85, infoTextPaint)
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
            angle < 337.5 -> "左前方"
            else -> "未知"
        }
    }

    private fun drawDirectionMarkers(canvas: Canvas, centerX: Float, centerY: Float, radius: Float) {
        val textRadius = radius + 45
        val directions = listOf(Pair("N", 0f), Pair("E", 90f), Pair("S", 180f), Pair("W", 270f))
        for ((direction, baseAngle) in directions) {
            val adjustedAngle = baseAngle - deviceAzimuth
            val radian = Math.toRadians(adjustedAngle.toDouble())
            val x = centerX + textRadius * sin(radian).toFloat()
            val y = centerY - textRadius * cos(radian).toFloat() + 12f
            val paint = if (direction == "N") {
                directionTextPaint.apply { color = Color.parseColor("#58A6FF"); alpha = 255 }
            } else {
                directionTextPaint.apply { color = Color.parseColor("#8B949E"); alpha = 180 }
            }
            canvas.drawText(direction, x, y, paint)
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
        var bearing = results[1]
        if (bearing < 0) bearing += 360f
        return bearing
    }
}
