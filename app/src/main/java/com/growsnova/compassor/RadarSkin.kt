package com.growsnova.compassor

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.core.content.ContextCompat

fun parseHexColor(colorString: String): Int {
    return try {
        val cleanHex = colorString.removePrefix("#")
        if (cleanHex.length == 6) {
            (0xFF000000.toLong() or cleanHex.toLong(16)).toInt()
        } else if (cleanHex.length == 8) {
            cleanHex.toLong(16).toInt()
        } else {
            0
        }
    } catch (e: Exception) {
        0
    }
}

data class RadarSkin(
    val backgroundColor: Int = parseHexColor("#0D1117"),
    val compassRingColor: Int = parseHexColor("#58A6FF"),
    val innerRingColor: Int = parseHexColor("#21262D"),
    val crosshairColor: Int = parseHexColor("#F0F6FC"),
    val targetColor: Int = parseHexColor("#FF9500"),
    val targetRingColor: Int = parseHexColor("#FF9500"),
    val targetLineColor: Int = parseHexColor("#FF9500"),
    val distanceTextColor: Int = parseHexColor("#F0F6FC"),
    val infoTextColor: Int = parseHexColor("#8B949E"),
    val directionTextColor: Int = parseHexColor("#58A6FF"),
    val tickColor: Int = parseHexColor("#30363D")
) {
    fun toDarkMode(): RadarSkin {
        val r = (backgroundColor shr 16) and 0xFF
        val g = (backgroundColor shr 8) and 0xFF
        val b = backgroundColor and 0xFF
        val lum = 0.299 * r + 0.587 * g + 0.114 * b

        if (lum <= 128) {
            return this
        }

        val ringR = (compassRingColor shr 16) and 0xFF
        val ringG = (compassRingColor shr 8) and 0xFF
        val ringB = compassRingColor and 0xFF
        val darkBgR = (14 + ringR * 0.08).toInt().coerceIn(10, 30)
        val darkBgG = (16 + ringG * 0.08).toInt().coerceIn(10, 30)
        val darkBgB = (22 + ringB * 0.08).toInt().coerceIn(10, 35)
        val darkBg = (0xFF000000.toLong() or (darkBgR.toLong() shl 16) or (darkBgG.toLong() shl 8) or darkBgB.toLong()).toInt()

        return this.copy(
            backgroundColor = darkBg,
            crosshairColor = parseHexColor("#F0F6FC"),
            distanceTextColor = parseHexColor("#F0F6FC"),
            infoTextColor = parseHexColor("#8B949E"),
            innerRingColor = parseHexColor("#21262D"),
            tickColor = parseHexColor("#30363D")
        )
    }

    companion object {
        fun createFromTheme(context: Context): RadarSkin {
            val primary = getThemeColor(context, com.google.android.material.R.attr.colorPrimary)
            val surface = getThemeColor(context, com.google.android.material.R.attr.colorSurface)
            val onSurface = getThemeColor(context, com.google.android.material.R.attr.colorOnSurface)
            val surfaceVariant = getThemeColor(context, com.google.android.material.R.attr.colorSurfaceVariant)
            val onSurfaceVariant = getThemeColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant)
            val tertiary = getThemeColor(context, com.google.android.material.R.attr.colorTertiary)
            val outline = getThemeColor(context, com.google.android.material.R.attr.colorOutline)

            return RadarSkin(
                backgroundColor = surface,
                compassRingColor = primary,
                innerRingColor = surfaceVariant,
                crosshairColor = onSurface,
                targetColor = tertiary,
                targetRingColor = tertiary,
                targetLineColor = tertiary,
                distanceTextColor = onSurface,
                infoTextColor = onSurfaceVariant,
                directionTextColor = primary,
                tickColor = outline
            )
        }

        private fun getThemeColor(context: Context, @AttrRes attrRes: Int): Int {
            val typedValue = TypedValue()
            if (context.theme.resolveAttribute(attrRes, typedValue, true)) {
                return if (typedValue.resourceId != 0) {
                    ContextCompat.getColor(context, typedValue.resourceId)
                } else {
                    typedValue.data
                }
            }
            return Color.BLACK
        }
    }
}
