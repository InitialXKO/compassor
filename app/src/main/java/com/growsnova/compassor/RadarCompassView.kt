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

    // 画笔 - FPS游戏风格
    private val backgroundPaint = Paint().apply {
        style = Paint.Style.FILL
    }

    // 扫描特效
    private val scanPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // 发光效果
    private val glowPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // 指南针外环
    private val compassRingPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        alpha = 180
        isAntiAlias = true
    }

    // 内环（更细的辅助环）
    private val innerRingPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        alpha = 120
        isAntiAlias = true
    }

    // 中心十字准星
    private val crosshairPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }

    // 目标点主色（橙色警告色）
    private val targetPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // 目标点外圈
    private val targetRingPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        alpha = 150
        isAntiAlias = true
    }

    // 目标指示线
    private val targetLinePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        alpha = 200
        isAntiAlias = true
        pathEffect = DashPathEffect(floatArrayOf(10f, 5f), 0f)
    }

    // 距离文本
    private val distanceTextPaint = Paint().apply {
        textSize = 48f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    // 辅助信息文本
    private val infoTextPaint = Paint().apply {
        textSize = 28f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    // 方向标记文本（N/E/S/W）
    private val directionTextPaint = Paint().apply {
        textSize = 36f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    // 刻度线
    private val tickPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    init {
        setSkin(this.skin)
    }

    fun setAzimuth(azimuth: Float) {
        this.deviceAzimuth = azimuth
        invalidate()
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


    fun updateTarget(myLoc: LatLng, targetLoc: LatLng) {
        this.myLocation = myLoc
        this.targetLocation = targetLoc
        this.distance = calculateDistance(myLoc, targetLoc)
        this.bearing = calculateBearing(myLoc, targetLoc)
        invalidate() // 触发重绘
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val currentTime = System.currentTimeMillis()
        if (lastDrawTime > 0) {
            val elapsed = currentTime - lastDrawTime
            scanAngle = (scanAngle + elapsed * 0.1f) % 360f // 扫描速度
        }
        lastDrawTime = currentTime

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = minOf(centerX, centerY) * 0.7f

        // 1. 绘制背景
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        // 1.5 绘制扫描发光特效
        drawScanGlow(canvas, centerX, centerY, radius)

        // 2. 绘制指南针环和刻度
        drawCompassRing(canvas, centerX, centerY, radius)

        // 3. 绘制中心十字准星
        drawCrosshair(canvas, centerX, centerY)

        // 4. 绘制方向标识（N, S, E, W）
        drawDirectionMarkers(canvas, centerX, centerY, radius)

        // 5. 计算相对方位角（目标方位 - 设备朝向）
        var relativeBearing = bearing - deviceAzimuth
        if (relativeBearing < 0) relativeBearing += 360f
        if (relativeBearing >= 360) relativeBearing -= 360f

        // 6. 绘制目标路点指示器
        if (distance > 0) {
            drawTargetWaypoint(canvas, centerX, centerY, radius, relativeBearing)
        }

        // 7. 显示距离和方位信息
        drawInfoPanel(canvas, centerX, centerY, radius, relativeBearing)

        postInvalidateOnAnimation()
    }

    private fun drawScanGlow(canvas: Canvas, centerX: Float, centerY: Float, radius: Float) {
        val sweepGradient = SweepGradient(centerX, centerY, 
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.parseColor("#2058A6FF"), Color.parseColor("#4058A6FF")),
            floatArrayOf(0f, (scanAngle - 45f) / 360f, (scanAngle - 10f) / 360f, scanAngle / 360f)
        )
        
        // Handle wrap around for SweepGradient if necessary, or just rotate the canvas
        canvas.save()
        canvas.rotate(scanAngle, centerX, centerY)
        val colors = intArrayOf(
            Color.TRANSPARENT, 
            Color.TRANSPARENT, 
            skin.compassRingColor and 0x00FFFFFF or 0x10000000, 
            skin.compassRingColor and 0x00FFFFFF or 0x30000000
        )
        val positions = floatArrayOf(0f, 0.7f, 0.9f, 1f)
        scanPaint.shader = SweepGradient(centerX, centerY, colors, positions)
        canvas.drawCircle(centerX, centerY, radius, scanPaint)
        canvas.restore()
    }

    private fun drawCompassRing(canvas: Canvas, centerX: Float, centerY: Float, radius: Float) {
        // 绘制外环
        canvas.drawCircle(centerX, centerY, radius, compassRingPaint)

        // 绘制内环
        canvas.drawCircle(centerX, centerY, radius * 0.85f, innerRingPaint)

        // 绘制刻度线（每10度一个小刻度，每90度一个大刻度）
        for (angle in 0 until 360 step 10) {
            val adjustedAngle = angle - deviceAzimuth
            val radian = Math.toRadians(adjustedAngle.toDouble())

            val startRadius: Float
            val endRadius: Float
            val paint: Paint

            if (angle % 90 == 0) {
                // 主方向刻度（N/E/S/W）更长更粗
                startRadius = radius * 0.92f
                endRadius = radius * 1.0f
                paint = compassRingPaint
            } else {
                // 普通刻度
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

        // 上
        canvas.drawLine(centerX, centerY - gap, centerX, centerY - gap - crossSize, crosshairPaint)
        // 下
        canvas.drawLine(centerX, centerY + gap, centerX, centerY + gap + crossSize, crosshairPaint)
        // 左
        canvas.drawLine(centerX - gap, centerY, centerX - gap - crossSize, centerY, crosshairPaint)
        // 右
        canvas.drawLine(centerX + gap, centerY, centerX + gap + crossSize, centerY, crosshairPaint)

        // 中心点
        canvas.drawCircle(centerX, centerY, 3f, crosshairPaint)
    }

    private fun drawTargetWaypoint(canvas: Canvas, centerX: Float, centerY: Float, radius: Float, relativeBearing: Float) {
        val radian = Math.toRadians(relativeBearing.toDouble())
        val targetRadius = radius * 0.75f
        val targetX = centerX + targetRadius * sin(radian).toFloat()
        val targetY = centerY - targetRadius * cos(radian).toFloat()

        // 绘制指向目标的虚线 - 从中心到目标点
        canvas.drawLine(centerX, centerY, targetX, targetY, targetLinePaint)

        // 绘制目标发光效果
        val pulse = (sin(System.currentTimeMillis() / 200.0) * 0.5 + 0.5).toFloat()
        val glowSize = 40f + 20f * pulse
        val colors = intArrayOf(skin.targetColor and 0x00FFFFFF or 0x60000000, Color.TRANSPARENT)
        glowPaint.shader = RadialGradient(targetX, targetY, glowSize, colors, null, Shader.TileMode.CLAMP)
        canvas.drawCircle(targetX, targetY, glowSize, glowPaint)

        // 绘制目标点外圈（脉冲效果）
        canvas.drawCircle(targetX, targetY, 25f + 5f * pulse, targetRingPaint)
        canvas.drawCircle(targetX, targetY, 20f, targetRingPaint)

        // 绘制目标点中心
        canvas.drawCircle(targetX, targetY, 12f, targetPaint)

        // 在目标点上方显示距离
        val distanceText = if (distance < 1000) {
            "${distance.toInt()}m"
        } else {
            "%.1fkm".format(distance / 1000)
        }

        val textY = targetY - 45f - 5f * pulse
        canvas.drawText(distanceText, targetX, textY, distanceTextPaint)
    }

    private fun drawInfoPanel(canvas: Canvas, centerX: Float, centerY: Float, radius: Float, relativeBearing: Float) {
        val infoY = centerY + radius + 80

        // 主要距离信息
        val distanceText = formatDistance(distance)
        canvas.drawText(distanceText, centerX, infoY, distanceTextPaint)

        // 方位角信息
        val bearingText = "${bearing.toInt()}°"
        canvas.drawText(bearingText, centerX, infoY + 50, infoTextPaint)

        // 相对方位信息（小字）
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

        // 定义方向及其角度
        val directions = listOf(
            Pair("N", 0f),
            Pair("E", 90f),
            Pair("S", 180f),
            Pair("W", 270f)
        )

        for ((direction, baseAngle) in directions) {
            val adjustedAngle = baseAngle - deviceAzimuth
            val radian = Math.toRadians(adjustedAngle.toDouble())

            val x = centerX + textRadius * sin(radian).toFloat()
            val y = centerY - textRadius * cos(radian).toFloat() + 12f // 微调文字垂直位置

            // 北方向使用高亮色
            val paint = if (direction == "N") {
                directionTextPaint.apply {
                    color = Color.parseColor("#58A6FF")
                    alpha = 255
                }
            } else {
                directionTextPaint.apply {
                    color = Color.parseColor("#8B949E")
                    alpha = 180
                }
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

}
