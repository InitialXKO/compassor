package com.growsnova.compassor

import android.graphics.Color

object DefaultSkins {
    val default = RadarSkin("默认")

    val forest = RadarSkin(
        name = "森林",
        backgroundColor = Color.parseColor("#1B2631"),
        compassRingColor = Color.parseColor("#7D8C8C"),
        innerRingColor = Color.parseColor("#5A6B6B"),
        crosshairColor = Color.parseColor("#F4F6F6"),
        targetColor = Color.parseColor("#D4AC0D"),
        targetRingColor = Color.parseColor("#D4AC0D"),
        targetLineColor = Color.parseColor("#D4AC0D"),
        distanceTextColor = Color.parseColor("#F4F6F6"),
        infoTextColor = Color.parseColor("#AAB7B7"),
        directionTextColor = Color.parseColor("#AAB7B7"),
        tickColor = Color.parseColor("#495A5A")
    )

    val ocean = RadarSkin(
        name = "海洋",
        backgroundColor = Color.parseColor("#0E2F44"),
        compassRingColor = Color.parseColor("#85C1E9"),
        innerRingColor = Color.parseColor("#5DADE2"),
        crosshairColor = Color.parseColor("#EBF5FB"),
        targetColor = Color.parseColor("#F1C40F"),
        targetRingColor = Color.parseColor("#F1C40F"),
        targetLineColor = Color.parseColor("#F1C40F"),
        distanceTextColor = Color.parseColor("#EBF5FB"),
        infoTextColor = Color.parseColor("#AED6F1"),
        directionTextColor = Color.parseColor("#AED6F1"),
        tickColor = Color.parseColor("#34495E")
    )

    val skins = listOf(default, forest, ocean)
}
