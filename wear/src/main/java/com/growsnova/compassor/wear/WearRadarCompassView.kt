package com.growsnova.compassor.wear

import android.content.Context
import com.growsnova.compassor.RadarSkin
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

class WearRadarCompassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var distance: Float = 0.0f
    private var bearing: Float = 0.0f
    private var deviceAzimuth: Float = 0.0f
    private var scanAngle: Float = 0.0f
    private var lastDrawTime: Long = 0
    private var hasTarget: Boolean = false

    private var skin: RadarSkin = WearSkins.default

    private val backgroundPaint = Paint().apply {
        style = Paint.Style.FILL
    }

    private val scanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val compassRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        alpha = 180
    }

    private val innerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        alpha = 120
    }

    private val crosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val targetRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        alpha = 150
    }

    private val targetLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        alpha = 200
        pathEffect = DashPathEffect(floatArrayOf(8f, 4f), 0f)
    }

    private val distanceTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 28f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val directionTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 24f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    init {
        setSkin(skin)
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
        directionTextPaint.color = skin.directionTextColor
        invalidate()
    }

    fun setAzimuth(azimuth: Float) {
        this.deviceAzimuth = azimuth
        invalidate()
    }

    fun updateTarget(distance: Float, bearing: Float) {
        this.distance = distance
        this.bearing = bearing
        this.hasTarget = true
        invalidate()
    }

    fun clearTarget() {
        this.hasTarget = false
        this.distance = 0.0f
        this.bearing = 0.0f
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
        val radius = minOf(centerX, centerY) * 0.72f

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        // Scan glow
        canvas.save()
        canvas.rotate(scanAngle, centerX, centerY)
        val colors = intArrayOf(
            Color.TRANSPARENT,
            Color.TRANSPARENT,
            skin.compassRingColor and 0x00FFFFFF or 0x15000000,
            skin.compassRingColor and 0x00FFFFFF or 0x40000000
        )
        val positions = floatArrayOf(0f, 0.65f, 0.88f, 1f)
        scanPaint.shader = SweepGradient(centerX, centerY, colors, positions)
        canvas.drawCircle(centerX, centerY, radius, scanPaint)
        canvas.restore()

        // Compass rings
        canvas.drawCircle(centerX, centerY, radius, compassRingPaint)
        canvas.drawCircle(centerX, centerY, radius * 0.82f, innerRingPaint)

        // Crosshair
        val crossSize = 16f
        val gap = 6f
        canvas.drawLine(centerX, centerY - gap, centerX, centerY - gap - crossSize, crosshairPaint)
        canvas.drawLine(centerX, centerY + gap, centerX, centerY + gap + crossSize, crosshairPaint)
        canvas.drawLine(centerX - gap, centerY, centerX - gap - crossSize, centerY, crosshairPaint)
        canvas.drawLine(centerX + gap, centerY, centerX + gap + crossSize, centerY, crosshairPaint)

        // Cardinal N/E/S/W
        val textRadius = radius + 22f
        val directions = listOf(Pair("N", 0f), Pair("E", 90f), Pair("S", 180f), Pair("W", 270f))
        for ((direction, baseAngle) in directions) {
            val adjustedAngle = baseAngle - deviceAzimuth
            val radian = Math.toRadians(adjustedAngle.toDouble())
            val x = centerX + textRadius * sin(radian).toFloat()
            val y = centerY - textRadius * cos(radian).toFloat() + 8f

            directionTextPaint.color = if (direction == "N") skin.directionTextColor else skin.infoTextColor
            canvas.drawText(direction, x, y, directionTextPaint)
        }

        if (hasTarget && distance >= 0) {
            var relativeBearing = bearing - deviceAzimuth
            if (relativeBearing < 0) relativeBearing += 360f
            if (relativeBearing >= 360) relativeBearing -= 360f

            val radian = Math.toRadians(relativeBearing.toDouble())
            val targetRadius = radius * 0.75f
            val targetX = centerX + targetRadius * sin(radian).toFloat()
            val targetY = centerY - targetRadius * cos(radian).toFloat()

            canvas.drawLine(centerX, centerY, targetX, targetY, targetLinePaint)

            val pulse = (sin(System.currentTimeMillis() / 200.0) * 0.5 + 0.5).toFloat()
            val glowSize = 25f + 12f * pulse
            val gColors = intArrayOf(skin.targetColor and 0x00FFFFFF or 0x60000000, Color.TRANSPARENT)
            glowPaint.shader = RadialGradient(targetX, targetY, glowSize, gColors, null, Shader.TileMode.CLAMP)
            canvas.drawCircle(targetX, targetY, glowSize, glowPaint)

            canvas.drawCircle(targetX, targetY, 16f + 4f * pulse, targetRingPaint)
            canvas.drawCircle(targetX, targetY, 8f, targetPaint)

            val distStr = if (distance < 1000) "${distance.toInt()}m" else "%.1fkm".format(distance / 1000f)
            canvas.drawText(distStr, targetX, targetY - 22f, distanceTextPaint)
        }

        postInvalidateOnAnimation()
    }
}
