package com.growsnova.compassor.wear

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class WearArrowCompassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var distance: Float = 0.0f
    private var bearing: Float = 0.0f
    private var deviceAzimuth: Float = 0.0f
    private var hasTarget: Boolean = false

    private val bgColor = Color.parseColor("#0F172A")
    private val arrowColor = Color.parseColor("#F59E0B")
    private val textColor = Color.parseColor("#FFFFFF")
    private val secondaryTextColor = Color.parseColor("#94A3B8")
    private val ringColor = Color.parseColor("#3B82F6")

    private val backgroundPaint = Paint().apply {
        style = Paint.Style.FILL
        color = bgColor
    }

    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = arrowColor
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val secondaryTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = secondaryTextColor
        textAlign = Paint.Align.CENTER
    }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = ringColor
    }

    private val arrowPath = Path()

    init {
        arrowPath.moveTo(0f, -60f)
        arrowPath.lineTo(25f, 15f)
        arrowPath.lineTo(0f, 0f)
        arrowPath.lineTo(-25f, 15f)
        arrowPath.close()
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
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = minOf(centerX, centerY) * 0.75f

        canvas.drawCircle(centerX, centerY - height * 0.06f, radius * 0.85f, ringPaint)

        if (hasTarget && distance >= 0) {
            textPaint.textSize = height * 0.13f
            val distStr = if (distance < 1000) "${distance.toInt()}m" else "%.1fkm".format(distance / 1000f)
            canvas.drawText(distStr, centerX, centerY + height * 0.32f, textPaint)

            var relativeBearing = bearing - deviceAzimuth
            canvas.save()
            canvas.translate(centerX, centerY - height * 0.06f)
            canvas.rotate(relativeBearing)
            val scale = height * 0.0035f
            canvas.scale(scale, scale)
            canvas.drawPath(arrowPath, arrowPaint)
            canvas.restore()

            secondaryTextPaint.textSize = height * 0.075f
            val hint = getRelativeDirection(relativeBearing)
            canvas.drawText(hint, centerX, centerY + height * 0.42f, secondaryTextPaint)
        } else {
            textPaint.textSize = height * 0.13f
            val azimuthInt = ((deviceAzimuth % 360f + 360f) % 360f).toInt()
            val headingText = "${azimuthInt}°"
            canvas.drawText(headingText, centerX, centerY + height * 0.32f, textPaint)

            canvas.save()
            canvas.translate(centerX, centerY - height * 0.06f)
            canvas.rotate(-deviceAzimuth)
            val scale = height * 0.0035f
            canvas.scale(scale, scale)
            canvas.drawPath(arrowPath, arrowPaint)
            canvas.restore()

            secondaryTextPaint.textSize = height * 0.075f
            val cardinalText = getCardinalDirection(deviceAzimuth)
            canvas.drawText(cardinalText, centerX, centerY + height * 0.42f, secondaryTextPaint)
        }
    }

    private fun getCardinalDirection(azimuth: Float): String {
        var a = (azimuth % 360f + 360f) % 360f
        return when {
            a < 22.5f || a >= 337.5f -> "真北"
            a < 67.5f -> "东北"
            a < 112.5f -> "正东"
            a < 157.5f -> "东南"
            a < 202.5f -> "正南"
            a < 247.5f -> "西南"
            a < 292.5f -> "正西"
            a < 337.5f -> "西北"
            else -> "真北"
        }
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
}
