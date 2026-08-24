package com.growsnova.compassor

import android.content.Context
import android.graphics.Color

data class SkinTheme(
    val key: String,
    val nameResId: Int,
    val descResId: Int,
    val skin: RadarSkin
)

object DefaultSkins {

    val default = RadarSkin()

    val morandi = RadarSkin(
        backgroundColor = parseHexColor("#F0EDE9"),
        compassRingColor = parseHexColor("#6B7B8C"),
        innerRingColor = parseHexColor("#D5DBE3"),
        crosshairColor = parseHexColor("#3D3A38"),
        targetColor = parseHexColor("#C9ADA7"),
        targetRingColor = parseHexColor("#C9ADA7"),
        targetLineColor = parseHexColor("#C9ADA7"),
        distanceTextColor = parseHexColor("#3D3A38"),
        infoTextColor = parseHexColor("#6B6561"),
        directionTextColor = parseHexColor("#6B7B8C"),
        tickColor = parseHexColor("#A69C94")
    )

    val mondrian = RadarSkin(
        backgroundColor = parseHexColor("#F8F9FA"),
        compassRingColor = parseHexColor("#1D3557"),
        innerRingColor = parseHexColor("#E9ECEF"),
        crosshairColor = parseHexColor("#111111"),
        targetColor = parseHexColor("#E63946"),
        targetRingColor = parseHexColor("#FFB703"),
        targetLineColor = parseHexColor("#E63946"),
        distanceTextColor = parseHexColor("#111111"),
        infoTextColor = parseHexColor("#495057"),
        directionTextColor = parseHexColor("#1D3557"),
        tickColor = parseHexColor("#212529")
    )

    val cyberpunk = RadarSkin(
        backgroundColor = parseHexColor("#0A0A12"),
        compassRingColor = parseHexColor("#00F0FF"),
        innerRingColor = parseHexColor("#1A1A2E"),
        crosshairColor = parseHexColor("#FFFFFF"),
        targetColor = parseHexColor("#FF007F"),
        targetRingColor = parseHexColor("#FF007F"),
        targetLineColor = parseHexColor("#FF007F"),
        distanceTextColor = parseHexColor("#00F0FF"),
        infoTextColor = parseHexColor("#8A8AA3"),
        directionTextColor = parseHexColor("#FF007F"),
        tickColor = parseHexColor("#2A2A4A")
    )

    val bauhaus = RadarSkin(
        backgroundColor = parseHexColor("#EAEAEA"),
        compassRingColor = parseHexColor("#2C3E50"),
        innerRingColor = parseHexColor("#D6DBDF"),
        crosshairColor = parseHexColor("#1A1A1A"),
        targetColor = parseHexColor("#D32F2F"),
        targetRingColor = parseHexColor("#FBC02D"),
        targetLineColor = parseHexColor("#D32F2F"),
        distanceTextColor = parseHexColor("#1A1A1A"),
        infoTextColor = parseHexColor("#5D6D7E"),
        directionTextColor = parseHexColor("#2C3E50"),
        tickColor = parseHexColor("#85929E")
    )

    val memphis = RadarSkin(
        backgroundColor = parseHexColor("#FFF8F0"),
        compassRingColor = parseHexColor("#00B4D8"),
        innerRingColor = parseHexColor("#FFE5EC"),
        crosshairColor = parseHexColor("#2B2D42"),
        targetColor = parseHexColor("#FF4D6D"),
        targetRingColor = parseHexColor("#FFB703"),
        targetLineColor = parseHexColor("#FF4D6D"),
        distanceTextColor = parseHexColor("#2B2D42"),
        infoTextColor = parseHexColor("#6C757D"),
        directionTextColor = parseHexColor("#00B4D8"),
        tickColor = parseHexColor("#ADB5BD")
    )

    val wabisabi = RadarSkin(
        backgroundColor = parseHexColor("#EFECE6"),
        compassRingColor = parseHexColor("#6E756B"),
        innerRingColor = parseHexColor("#E0DDD5"),
        crosshairColor = parseHexColor("#36332E"),
        targetColor = parseHexColor("#C06C84"),
        targetRingColor = parseHexColor("#C06C84"),
        targetLineColor = parseHexColor("#C06C84"),
        distanceTextColor = parseHexColor("#36332E"),
        infoTextColor = parseHexColor("#7D776D"),
        directionTextColor = parseHexColor("#6E756B"),
        tickColor = parseHexColor("#A8A297")
    )

    val sunsetImpression = RadarSkin(
        backgroundColor = parseHexColor("#1F1A24"),
        compassRingColor = parseHexColor("#9B5DE5"),
        innerRingColor = parseHexColor("#2D2336"),
        crosshairColor = parseHexColor("#F1FAEE"),
        targetColor = parseHexColor("#F15BB5"),
        targetRingColor = parseHexColor("#FEE440"),
        targetLineColor = parseHexColor("#F15BB5"),
        distanceTextColor = parseHexColor("#F1FAEE"),
        infoTextColor = parseHexColor("#A09ABC"),
        directionTextColor = parseHexColor("#FEE440"),
        tickColor = parseHexColor("#433852")
    )

    val nordicWarmth = RadarSkin(
        backgroundColor = parseHexColor("#222831"),
        compassRingColor = parseHexColor("#00ADB5"),
        innerRingColor = parseHexColor("#393E46"),
        crosshairColor = parseHexColor("#EEEEEE"),
        targetColor = parseHexColor("#FF5722"),
        targetRingColor = parseHexColor("#FF5722"),
        targetLineColor = parseHexColor("#FF5722"),
        distanceTextColor = parseHexColor("#EEEEEE"),
        infoTextColor = parseHexColor("#929AAB"),
        directionTextColor = parseHexColor("#00ADB5"),
        tickColor = parseHexColor("#4F5866")
    )

    val inkMonochrome = RadarSkin(
        backgroundColor = parseHexColor("#121212"),
        compassRingColor = parseHexColor("#E0E0E0"),
        innerRingColor = parseHexColor("#242424"),
        crosshairColor = parseHexColor("#FFFFFF"),
        targetColor = parseHexColor("#D32F2F"),
        targetRingColor = parseHexColor("#D32F2F"),
        targetLineColor = parseHexColor("#D32F2F"),
        distanceTextColor = parseHexColor("#FFFFFF"),
        infoTextColor = parseHexColor("#9E9E9E"),
        directionTextColor = parseHexColor("#E0E0E0"),
        tickColor = parseHexColor("#424242")
    )

    val forest = RadarSkin(
        backgroundColor = parseHexColor("#0B1914"),
        compassRingColor = parseHexColor("#2A9D8F"),
        innerRingColor = parseHexColor("#132E25"),
        crosshairColor = parseHexColor("#E8F1EE"),
        targetColor = parseHexColor("#E9C46A"),
        targetRingColor = parseHexColor("#E9C46A"),
        targetLineColor = parseHexColor("#E9C46A"),
        distanceTextColor = parseHexColor("#E8F1EE"),
        infoTextColor = parseHexColor("#7CA99A"),
        directionTextColor = parseHexColor("#2A9D8F"),
        tickColor = parseHexColor("#214A3C")
    )

    val ocean = RadarSkin(
        backgroundColor = parseHexColor("#08121E"),
        compassRingColor = parseHexColor("#00B4D8"),
        innerRingColor = parseHexColor("#0F2238"),
        crosshairColor = parseHexColor("#E0F7FA"),
        targetColor = parseHexColor("#48CAE4"),
        targetRingColor = parseHexColor("#90E0EF"),
        targetLineColor = parseHexColor("#48CAE4"),
        distanceTextColor = parseHexColor("#E0F7FA"),
        infoTextColor = parseHexColor("#648CA6"),
        directionTextColor = parseHexColor("#00B4D8"),
        tickColor = parseHexColor("#18385A")
    )

    val emeraldForest = forest

    val sunsetGlow = RadarSkin(
        backgroundColor = parseHexColor("#181124"),
        compassRingColor = parseHexColor("#E63946"),
        innerRingColor = parseHexColor("#281B3D"),
        crosshairColor = parseHexColor("#FFF3E0"),
        targetColor = parseHexColor("#FA8231"),
        targetRingColor = parseHexColor("#F7B731"),
        targetLineColor = parseHexColor("#FA8231"),
        distanceTextColor = parseHexColor("#FFF3E0"),
        infoTextColor = parseHexColor("#9E8BB3"),
        directionTextColor = parseHexColor("#FA8231"),
        tickColor = parseHexColor("#453160")
    )

    val deepSeaAbyss = ocean

    val vintageMacaron = RadarSkin(
        backgroundColor = parseHexColor("#FAF4F6"),
        compassRingColor = parseHexColor("#98D8AA"),
        innerRingColor = parseHexColor("#F3E8EE"),
        crosshairColor = parseHexColor("#4A3E4D"),
        targetColor = parseHexColor("#F7A4A4"),
        targetRingColor = parseHexColor("#FFD97D"),
        targetLineColor = parseHexColor("#F7A4A4"),
        distanceTextColor = parseHexColor("#4A3E4D"),
        infoTextColor = parseHexColor("#8E7C93"),
        directionTextColor = parseHexColor("#98D8AA"),
        tickColor = parseHexColor("#D8C4D0")
    )

    val themes = listOf(
        SkinTheme("Default", R.string.skin_default, R.string.skin_default_desc, default),
        SkinTheme("Morandi", R.string.skin_morandi, R.string.skin_morandi_desc, morandi),
        SkinTheme("Mondrian", R.string.skin_mondrian, R.string.skin_mondrian_desc, mondrian),
        SkinTheme("Cyberpunk", R.string.skin_cyberpunk, R.string.skin_cyberpunk_desc, cyberpunk),
        SkinTheme("Bauhaus", R.string.skin_bauhaus, R.string.skin_bauhaus_desc, bauhaus),
        SkinTheme("Memphis", R.string.skin_memphis, R.string.skin_memphis_desc, memphis),
        SkinTheme("WabiSabi", R.string.skin_wabisabi, R.string.skin_wabisabi_desc, wabisabi),
        SkinTheme("SunsetImpression", R.string.skin_sunset_impression, R.string.skin_sunset_impression_desc, sunsetImpression),
        SkinTheme("NordicWarmth", R.string.skin_nordic_warmth, R.string.skin_nordic_warmth_desc, nordicWarmth),
        SkinTheme("InkMonochrome", R.string.skin_ink_monochrome, R.string.skin_ink_monochrome_desc, inkMonochrome),
        SkinTheme("EmeraldForest", R.string.skin_emerald_forest, R.string.skin_emerald_forest_desc, emeraldForest),
        SkinTheme("SunsetGlow", R.string.skin_sunset_glow, R.string.skin_sunset_glow_desc, sunsetGlow),
        SkinTheme("DeepSeaAbyss", R.string.skin_deep_sea, R.string.skin_deep_sea_desc, deepSeaAbyss),
        SkinTheme("VintageMacaron", R.string.skin_vintage_macaron, R.string.skin_vintage_macaron_desc, vintageMacaron)
    )

    val skins = themes.map { it.skin }

    fun getSkinByName(name: String, context: Context? = null): RadarSkin {
        return when (name) {
            "Morandi" -> morandi
            "Mondrian" -> mondrian
            "Cyberpunk" -> cyberpunk
            "Bauhaus" -> bauhaus
            "Memphis" -> memphis
            "WabiSabi" -> wabisabi
            "SunsetImpression" -> sunsetImpression
            "NordicWarmth" -> nordicWarmth
            "InkMonochrome" -> inkMonochrome
            "EmeraldForest", "Forest" -> emeraldForest
            "SunsetGlow" -> sunsetGlow
            "DeepSeaAbyss", "Ocean" -> deepSeaAbyss
            "VintageMacaron" -> vintageMacaron
            "Default" -> context?.let { RadarSkin.createFromTheme(it) } ?: default
            else -> context?.let { RadarSkin.createFromTheme(it) } ?: default
        }
    }
}
