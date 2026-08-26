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

    @Test
    fun testParseAmapScheme() {
        val uri = "androidamap://viewReGeo?sourceApplication=Compassor&lat=39.9042&lon=116.4074&title=BeijingStation"
        val parsed = CoordinateParser.parseUriString(uri)
        assertNotNull(parsed)
        assertEquals("BeijingStation", parsed?.name)
        assertEquals(39.9042, parsed!!.gcj02LatLng.latitude, 0.0001)
        assertEquals(116.4074, parsed.gcj02LatLng.longitude, 0.0001)
    }

    @Test
    fun testParseBaiduMapScheme() {
        val uri = "baidumap://map/marker?location=39.915,116.404&title=ForbiddenCity"
        val parsed = CoordinateParser.parseUriString(uri)
        assertNotNull(parsed)
        assertEquals("ForbiddenCity", parsed?.name)
        assertTrue(parsed!!.gcj02LatLng.latitude in 39.90..39.93)
    }

    @Test
    fun testParseTencentMapScheme() {
        val uri = "qqmap://map/marker?marker=coord:39.9042,116.4074;title:TencentHQ"
        val parsed = CoordinateParser.parseUriString(uri)
        assertNotNull(parsed)
        assertEquals("TencentHQ", parsed?.name)
        assertEquals(39.9042, parsed!!.gcj02LatLng.latitude, 0.0001)
    }
}
