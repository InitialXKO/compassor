package com.growsnova.compassor

import android.content.Context
import android.graphics.*
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
) : View(context, attrs, defStyleAttr) {

    private var myLocation: LatLng = LatLng(0.0, 0.0)
    private var targetLocation: LatLng = LatLng(0.0, 0.0)
    private var distance: Float = 0.0f
    private var bearing: Float = 0.0f
    private var deviceAzimuth: Float = 0.0f

    private var skin: RadarSkin = RadarSkin()

    private val backgroundPaint = Paint().apply {
        style = Paint.Style.FILL
    }

    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val secondaryTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
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

    fun setAzimuth(azimuth: Float) {
        this.deviceAzimuth = azimuth
        invalidate()
    }

    fun setSkin(skin: RadarSkin) {
        this.skin = skin
        backgroundPaint.color = skin.backgroundColor
        arrowPaint.color = skin.targetColor
        textPaint.color = skin.distanceTextColor
        secondaryTextPaint.color = skin.infoTextColor
        ringPaint.color = skin.compassRingColor
        invalidate()
    }


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
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = minOf(centerX, centerY) * 0.7f

        // Outer Fluent Accent Ring
        canvas.drawCircle(centerX, centerY - height * 0.05f, radius * 0.85f, ringPaint)

        // Draw distance
        textPaint.textSize = height * 0.12f
        val distStr = if (distance < 1000) "${distance.toInt()}m" else "%.1fkm".format(distance / 1000)
        canvas.drawText(distStr, centerX, centerY + height * 0.32f, textPaint)

        // Draw arrow
        var relativeBearing = bearing - deviceAzimuth
        canvas.save()
        canvas.translate(centerX, centerY - height * 0.05f)
        canvas.rotate(relativeBearing)
        val scale = height * 0.0035f
        canvas.scale(scale, scale)
        canvas.drawPath(arrowPath, arrowPaint)
        canvas.restore()
        
        // Draw relative direction hint
        secondaryTextPaint.textSize = height * 0.065f
        val hint = getRelativeDirection(relativeBearing)
        canvas.drawText(hint, centerX, centerY + height * 0.42f, secondaryTextPaint)
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
