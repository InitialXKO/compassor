package com.growsnova.compassor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkinTest {

    @Test
    fun testAllThemesRegistered() {
        val themes = DefaultSkins.themes
        assertTrue("Expected at least 13 themes", themes.size >= 13)

        val keys = themes.map { it.key }
        val expectedKeys = listOf(
            "Default", "Morandi", "Mondrian", "Cyberpunk",
            "Bauhaus", "Memphis", "WabiSabi", "SunsetImpression",
            "NordicWarmth", "InkMonochrome", "EmeraldForest",
            "SunsetGlow", "DeepSeaAbyss", "VintageMacaron"
        )

        for (expectedKey in expectedKeys) {
            assertTrue("Missing theme key: $expectedKey", keys.contains(expectedKey))
        }
    }

    @Test
    fun testGetSkinByNameReturnsValidSkin() {
        val keys = listOf(
            "Default", "Morandi", "Mondrian", "Cyberpunk",
            "Bauhaus", "Memphis", "WabiSabi", "SunsetImpression",
            "NordicWarmth", "InkMonochrome", "EmeraldForest", "Forest",
            "SunsetGlow", "DeepSeaAbyss", "Ocean", "VintageMacaron"
        )

        for (key in keys) {
            val skin = DefaultSkins.getSkinByName(key)
            assertNotNull("Skin for key '$key' should not be null", skin)
        }
    }

    @Test
    fun testFallbackForUnknownSkinName() {
        val unknownSkin = DefaultSkins.getSkinByName("UnknownThemeKey123")
        assertNotNull(unknownSkin)
        assertEquals(DefaultSkins.default.backgroundColor, unknownSkin.backgroundColor)
    }

    @Test
    fun testSpecificThemeColors() {
        val mondrian = DefaultSkins.getSkinByName("Mondrian")
        val cyberpunk = DefaultSkins.getSkinByName("Cyberpunk")
        val morandi = DefaultSkins.getSkinByName("Morandi")

        assertEquals(parseHexColor("#E63946"), mondrian.targetColor)
        assertEquals(parseHexColor("#00F0FF"), cyberpunk.compassRingColor)
        assertEquals(parseHexColor("#C9ADA7"), morandi.targetColor)
    }
}
