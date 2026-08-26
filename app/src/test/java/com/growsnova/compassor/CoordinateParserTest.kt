package com.growsnova.compassor

import org.junit.Assert.*
import org.junit.Test

class CoordinateParserTest {

    @Test
    fun testParseGeoUri() {
        val uri = "geo:39.9042,116.4074?q=39.9042,116.4074(Tiananmen Square)"
        val parsed = CoordinateParser.parseUriString(uri)
        assertNotNull(parsed)
        assertEquals("Tiananmen Square", parsed?.name)
        // Check that coordinates were converted from WGS-84 to GCJ-02 (non-zero shift in China)
        assertTrue(parsed!!.gcj02LatLng.latitude > 39.90)
        assertTrue(parsed.gcj02LatLng.longitude > 116.40)
    }

    @Test
    fun testParseRawText() {
        val text = "Meet me at 31.2304, 121.4737 (Shanghai)"
        val parsed = CoordinateParser.parseText(text)
        assertNotNull(parsed)
        assertTrue(parsed!!.gcj02LatLng.latitude > 31.20)
        assertTrue(parsed.gcj02LatLng.longitude > 121.40)
    }

    @Test
    fun testParseAmapUrl() {
        val url = "https://uri.amap.com/marker?position=121.4737,31.2304&name=TheBund"
        val parsed = CoordinateParser.parseUriString(url)
        assertNotNull(parsed)
        assertEquals("TheBund", parsed?.name)
        assertEquals(31.2304, parsed!!.gcj02LatLng.latitude, 0.0001)
        assertEquals(121.4737, parsed.gcj02LatLng.longitude, 0.0001)
    }
}
