package com.growsnova.compassor

import android.net.Uri
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
import java.net.URLDecoder

class CoordinateParserTest {

    private fun decodeUrl(str: String): String {
        return try { URLDecoder.decode(str, "UTF-8") } catch (e: Exception) { str }
    }

    private fun parseQueryParam(uriStr: String, key: String): String? {
        val queryIndex = uriStr.indexOf("?")
        if (queryIndex == -1) return null
        val queryString = uriStr.substring(queryIndex + 1)
        for (param in queryString.split("&")) {
            val kv = param.split("=")
            if (kv.size == 2 && kv[0].lowercase() == key.lowercase()) {
                return decodeUrl(kv[1])
            }
        }
        return null
    }

    @Test
    fun testParseGeoUriStandard() {
        val uriStr = "geo:39.9042,116.4074?q=39.9042,116.4074(Tiananmen Square)"
        val q = parseQueryParam(uriStr, "q")
        assertNotNull(q)
        val nameMatch = Regex("""\(([^)]+)\)""").find(q!!)
        val name = nameMatch?.groupValues?.get(1)
        assertEquals("Tiananmen Square", name)

        val coordPart = q.substringBefore("(").trim()
        val parts = coordPart.split(",")
        val lat = parts[0].toDouble()
        val lng = parts[1].toDouble()

        val (gcjLat, gcjLng) = CoordTransform.wgs84ToGcj02(lat, lng)
        assertTrue(gcjLat > 39.90)
        assertTrue(gcjLng > 116.40)
    }

    @Test
    fun testParseGeoUriZeroPath() {
        val uriStr = "geo:0,0?q=39.9042,116.4074(Beijing Landmark)"
        val q = parseQueryParam(uriStr, "q")
        assertNotNull(q)
        val nameMatch = Regex("""\(([^)]+)\)""").find(q!!)
        assertEquals("Beijing Landmark", nameMatch?.groupValues?.get(1))
    }

    @Test
    fun testParseAmapUrl() {
        val url = "https://uri.amap.com/marker?position=121.4737,31.2304&name=TheBund"
        val pos = parseQueryParam(url, "position")
        val name = parseQueryParam(url, "name")
        assertEquals("TheBund", name)
        assertNotNull(pos)
        val parts = pos!!.split(",")
        val lng = parts[0].toDouble()
        val lat = parts[1].toDouble()
        assertEquals(31.2304, lat, 0.0001)
        assertEquals(121.4737, lng, 0.0001)
    }

    @Test
    fun testParseAmapScheme() {
        val uriStr = "androidamap://viewReGeo?sourceApplication=Compassor&lat=39.9042&lon=116.4074&title=BeijingStation"
        val lat = parseQueryParam(uriStr, "lat")?.toDouble()
        val lon = parseQueryParam(uriStr, "lon")?.toDouble()
        val title = parseQueryParam(uriStr, "title")
        assertEquals("BeijingStation", title)
        assertEquals(39.9042, lat!!, 0.0001)
        assertEquals(116.4074, lon!!, 0.0001)
    }

    @Test
    fun testParseBaiduMapScheme() {
        val uriStr = "baidumap://map/marker?location=39.915,116.404&title=ForbiddenCity"
        val loc = parseQueryParam(uriStr, "location")
        val title = parseQueryParam(uriStr, "title")
        assertEquals("ForbiddenCity", title)
        assertNotNull(loc)
        val parts = loc!!.split(",")
        val lat = parts[0].toDouble()
        val lng = parts[1].toDouble()

        val xPi = Math.PI * 3000.0 / 180.0
        val x = lng - 0.0065
        val y = lat - 0.006
        val z = Math.sqrt(x * x + y * y) - 0.00002 * Math.sin(y * xPi)
        val theta = Math.atan2(y, x) - 0.000003 * Math.cos(x * xPi)
        val gcjLat = z * Math.sin(theta)
        assertTrue(gcjLat in 39.90..39.93)
    }

    @Test
    fun testParseTencentMapScheme() {
        val uriStr = "qqmap://map/marker?marker=coord:39.9042,116.4074;title:TencentHQ"
        val marker = parseQueryParam(uriStr, "marker")
        assertNotNull(marker)
        assertTrue(marker!!.contains("coord:39.9042,116.4074"))
        assertTrue(marker.contains("title:TencentHQ"))
    }

    @Test
    fun testSelfShareAndParseGeoUri() {
        val targetName = "Shanghai Tower"
        val originalGcjLat = 31.2335
        val originalGcjLng = 121.5056

        val (wgsLat, wgsLng) = CoordTransform.gcj02ToWgs84(originalGcjLat, originalGcjLng)
        val encodedName = java.net.URLEncoder.encode(targetName, "UTF-8")
        val generatedGeoUri = "geo:$wgsLat,$wgsLng?q=$wgsLat,$wgsLng($encodedName)"

        val parsed = CoordinateParser.parseUriString(generatedGeoUri)
        assertNotNull(parsed)
        println("DEBUG parsed.name = '${parsed?.name}'")
        assertEquals(targetName, parsed?.name)
        assertEquals(originalGcjLat, parsed!!.gcj02LatLng.latitude, 0.001)
        assertEquals(originalGcjLng, parsed.gcj02LatLng.longitude, 0.001)
    }

    @Test
    fun testSelfShareAndParseShareText() {
        val targetName = "West Lake"
        val originalGcjLat = 30.2435
        val originalGcjLng = 120.1421

        val (wgsLat, wgsLng) = CoordTransform.gcj02ToWgs84(originalGcjLat, originalGcjLng)
        val encodedName = java.net.URLEncoder.encode(targetName, "UTF-8")
        val shareText = """
            $targetName
            ${String.format(java.util.Locale.US, "%.6f, %.6f", wgsLat, wgsLng)} (WGS-84)
            geo:%.6f,%.6f?q=%.6f,%.6f(%s)
            https://uri.amap.com/marker?position=%.6f,%.6f&name=%s
        """.trimIndent().format(java.util.Locale.US, wgsLat, wgsLng, wgsLat, wgsLng, encodedName, originalGcjLng, originalGcjLat, encodedName)

        val parsed = CoordinateParser.parseUriString(shareText)
        assertNotNull(parsed)
        assertEquals(originalGcjLat, parsed!!.gcj02LatLng.latitude, 0.001)
        assertEquals(originalGcjLng, parsed.gcj02LatLng.longitude, 0.001)
    }

    @Test
    fun testParseGeoUriWithElevationAndLabel() {
        val uriStr = "geo:39.9042,116.4074,120.5?label=Custom%20Beacon"
        val parsed = CoordinateParser.parseUriString(uriStr)
        assertNotNull(parsed)
        assertEquals("Custom Beacon", parsed?.name)
        assertTrue(parsed!!.gcj02LatLng.latitude > 39.90)
    }

    @Test
    fun testParseGeoUriQueryAddressFallback() {
        val uriStr = "geo:0,0?q=Forbidden%20City"
        val parsed = CoordinateParser.parseUriString(uriStr)
        assertNotNull(parsed)
        assertEquals("Forbidden City", parsed?.name)
    }

    @Test
    fun testIncomingLocationDialogActionParsing() {
        val uriStr = "geo:31.2304,121.4737?q=31.2304,121.4737(The%20Bund)"
        val parsed = CoordinateParser.parseUriString(uriStr)
        assertNotNull(parsed)
        assertEquals("The Bund", parsed?.name)
        assertTrue(parsed!!.gcj02LatLng.latitude > 0.0)
        assertTrue(parsed.gcj02LatLng.longitude > 0.0)
    }
}
