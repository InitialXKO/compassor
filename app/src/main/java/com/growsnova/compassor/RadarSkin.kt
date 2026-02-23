package com.growsnova.compassor

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.core.content.ContextCompat

data class RadarSkin(
    val name: String = "Default",
    val backgroundColor: Int = Color.parseColor("#0D1117"),
    val compassRingColor: Int = Color.parseColor("#58A6FF"),
    val innerRingColor: Int = Color.parseColor("#21262D"),
    val crosshairColor: Int = Color.parseColor("#F0F6FC"),
    val targetColor: Int = Color.parseColor("#FF9500"),
    val targetRingColor: Int = Color.parseColor("#FF9500"),
    val targetLineColor: Int = Color.parseColor("#FF9500"),
    val distanceTextColor: Int = Color.parseColor("#F0F6FC"),
    val infoTextColor: Int = Color.parseColor("#8B949E"),
    val directionTextColor: Int = Color.parseColor("#58A6FF"),
    val tickColor: Int = Color.parseColor("#30363D")
) {
    companion object {
        fun createFromTheme(context: Context): RadarSkin {
            val name = "Dynamic"
            val primary = getThemeColor(context, com.google.android.material.R.attr.colorPrimary)
            val surface = getThemeColor(context, com.google.android.material.R.attr.colorSurface)
            val onSurface = getThemeColor(context, com.google.android.material.R.attr.colorOnSurface)
            val surfaceVariant = getThemeColor(context, com.google.android.material.R.attr.colorSurfaceVariant)
            val onSurfaceVariant = getThemeColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant)
            val tertiary = getThemeColor(context, com.google.android.material.R.attr.colorTertiary)
            val outline = getThemeColor(context, com.google.android.material.R.attr.colorOutline)

            return RadarSkin(
                name = name,
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
            context.theme.resolveAttribute(attrRes, typedValue, true)
            return typedValue.data
        }
    }
}
