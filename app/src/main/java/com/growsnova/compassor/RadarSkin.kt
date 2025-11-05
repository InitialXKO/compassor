package com.growsnova.compassor

import android.graphics.Color

data class RadarSkin(
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
)
