package com.growsnova.compassor

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.amap.api.maps.model.LatLng
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.regex.Pattern

data class ParsedLocation(
    val gcj02LatLng: LatLng,
    val name: String
)

object CoordinateParser {

    private val LAT_LNG_REGEX = Pattern.compile(
        """(-?\d{1,2}\.\d+)\s*,\s*(-?\d{1,3}\.\d+)"""
    )

    fun parseIntent(intent: Intent?): ParsedLocation? {
        if (intent == null) return null
        val dataStr = intent.dataString
        val textExtra = intent.getStringExtra(Intent.EXTRA_TEXT)

        // 1. Check intent data URI
        if (!dataStr.isNullOrEmpty()) {
            val parsed = parseUriString(dataStr)
            if (parsed != null) return parsed
        }

        // 2. Check EXTRA_TEXT - if it contains a URL/geo URI, parseUriString first
        if (!textExtra.isNullOrEmpty()) {
            if (textExtra.contains("://") || textExtra.lowercase().contains("geo:")) {
                val parsed = parseUriString(textExtra)
                if (parsed != null) return parsed
            }
            return parseText(textExtra)
        }

        return null
    }

    private fun decodeUrl(str: String): String {
        return try {
            URLDecoder.decode(str, "UTF-8")
        } catch (e: Exception) {
            str
        }
    }

    private fun getQueryParam(uriStr: String, paramName: String): String? {
        val queryIndex = uriStr.indexOf("?")
        if (queryIndex == -1) return null
        val queryString = uriStr.substring(queryIndex + 1)
        for (param in queryString.split("&")) {
            val kv = param.split("=")
            if (kv.size == 2 && kv[0].equals(paramName, ignoreCase = true)) {
                return decodeUrl(kv[1])
            }
        }
        return null
    }

    fun parseUriString(uriString: String): ParsedLocation? {
        val lower = uriString.lowercase()

        // 1. geo: scheme (WGS-84 standard)
        val geoIndex = lower.indexOf("geo:")
        if (geoIndex != -1) {
            val geoSubStr = uriString.substring(geoIndex)
            val coords = extractGeoCoordinates(geoSubStr)
            if (coords != null) {
                val (gcjLat, gcjLng) = CoordTransform.wgs84ToGcj02(coords.first, coords.second)
                val qParam = getQueryParam(geoSubStr, "q")
                val label = if (!qParam.isNullOrEmpty()) {
                    val match = Regex("""\(([^)]+)\)""").find(qParam)
                    match?.groupValues?.get(1)?.trim() ?: "共享位置"
                } else {
                    "共享位置"
                }
                return ParsedLocation(LatLng(gcjLat, gcjLng), label)
            }
        }

        // 2. AMap (GCJ-02)
        if (lower.contains("androidamap://") || lower.contains("amapuri://") || lower.contains("amap.com")) {
            val coords = extractAmapCoordinates(uriString)
            if (coords != null) {
                val name = getQueryParam(uriString, "name")
                    ?: getQueryParam(uriString, "title")
                    ?: getQueryParam(uriString, "poiname")
                    ?: "高德位置"
                return ParsedLocation(LatLng(coords.first, coords.second), name)
            }
        }

        // 3. Baidu Map (BD-09)
        if (lower.contains("baidumap://") || lower.contains("bdapp://") || lower.contains("map.baidu.com")) {
            val coords = extractBaiduCoordinates(uriString)
            if (coords != null) {
                val (gcjLat, gcjLng) = bd09ToGcj02(coords.first, coords.second)
                val name = getQueryParam(uriString, "title") ?: getQueryParam(uriString, "name") ?: "百度位置"
                return ParsedLocation(LatLng(gcjLat, gcjLng), name)
            }
        }

        // 4. Tencent Map (GCJ-02)
        if (lower.contains("qqmap://") || lower.contains("map.qq.com")) {
            val coords = extractTencentCoordinates(uriString)
            if (coords != null) {
                val name = getQueryParam(uriString, "title") ?: "腾讯位置"
                return ParsedLocation(LatLng(coords.first, coords.second), name)
            }
        }

        // 5. Google Maps (WGS-84)
        if (lower.contains("google.navigation:") || lower.contains("google.com/maps") || lower.contains("maps.app.goo.gl")) {
            val coords = extractGoogleCoordinates(uriString)
            if (coords != null) {
                val (gcjLat, gcjLng) = CoordTransform.wgs84ToGcj02(coords.first, coords.second)
                return ParsedLocation(LatLng(gcjLat, gcjLng), "谷歌位置")
            }
        }

        // 6. Fallback to parseText
        return parseText(uriString)
    }

    private fun extractGeoCoordinates(uriString: String): Pair<Double, Double>? {
        // First try q= query parameter (geo:0,0?q=lat,lng(label) or geo:?q=lat,lng)
        val q = getQueryParam(uriString, "q")
        if (!q.isNullOrEmpty()) {
            val coordPart = if (q.contains("(")) q.substringBefore("(").trim() else q.trim()
            val parts = coordPart.split(",")
            if (parts.size >= 2) {
                val lat = parts[0].toDoubleOrNull()
                val lng = parts[1].toDoubleOrNull()
                if (lat != null && lng != null && lat in -90.0..90.0 && lng in -180.0..180.0) {
                    return Pair(lat, lng)
                }
            }
        }

        // Second try path scheme specific part geo:lat,lng
        val geoPath = uriString.substringAfter("geo:").substringBefore("?")
        val parts = geoPath.split(",")
        if (parts.size >= 2) {
            val lat = parts[0].trim().toDoubleOrNull()
            val lng = parts[1].trim().toDoubleOrNull()
            if (lat != null && lng != null && lat in -90.0..90.0 && lng in -180.0..180.0) {
                return Pair(lat, lng)
            }
        }

        return null
    }

    private fun extractAmapCoordinates(uriString: String): Pair<Double, Double>? {
        // Priority 1: position=lng,lat (AMap standard sequence)
        getQueryParam(uriString, "position")?.let { pos ->
            val parts = pos.split(",")
            if (parts.size >= 2) {
                val lng = parts[0].toDoubleOrNull()
                val lat = parts[1].toDoubleOrNull()
                if (lat != null && lng != null) return Pair(lat, lng)
            }
        }

        // Priority 2: lat & lon/lng
        val lat = getQueryParam(uriString, "lat")?.toDoubleOrNull()
        val lng = getQueryParam(uriString, "lon")?.toDoubleOrNull() ?: getQueryParam(uriString, "lng")?.toDoubleOrNull()
        if (lat != null && lng != null) return Pair(lat, lng)

        return null
    }

    private fun extractBaiduCoordinates(uriString: String): Pair<Double, Double>? {
        // Priority 1: location=lat,lng or center=lat,lng
        val loc = getQueryParam(uriString, "location") ?: getQueryParam(uriString, "center")
        if (!loc.isNullOrEmpty()) {
            val parts = loc.split(",")
            if (parts.size >= 2) {
                val lat = parts[0].toDoubleOrNull()
                val lng = parts[1].toDoubleOrNull()
                if (lat != null && lng != null) return Pair(lat, lng)
            }
        }

        // Priority 2: lat & lng/lon
        val lat = getQueryParam(uriString, "lat")?.toDoubleOrNull()
        val lng = getQueryParam(uriString, "lng")?.toDoubleOrNull() ?: getQueryParam(uriString, "lon")?.toDoubleOrNull()
        if (lat != null && lng != null) return Pair(lat, lng)

        return null
    }

    private fun extractTencentCoordinates(uriString: String): Pair<Double, Double>? {
        // Priority 1: marker=coord:lat,lng;title:Name
        getQueryParam(uriString, "marker")?.let { marker ->
            for (part in marker.split(";")) {
                if (part.startsWith("coord:")) {
                    val coords = part.substring(6).split(",")
                    if (coords.size >= 2) {
                        val lat = coords[0].toDoubleOrNull()
                        val lng = coords[1].toDoubleOrNull()
                        if (lat != null && lng != null) return Pair(lat, lng)
                    }
                }
            }
        }

        // Priority 2: center=lat,lng or location=lat,lng
        val center = getQueryParam(uriString, "center") ?: getQueryParam(uriString, "location")
        if (!center.isNullOrEmpty()) {
            val parts = center.split(",")
            if (parts.size >= 2) {
                val lat = parts[0].toDoubleOrNull()
                val lng = parts[1].toDoubleOrNull()
                if (lat != null && lng != null) return Pair(lat, lng)
            }
        }

        return null
    }

    private fun extractGoogleCoordinates(uriString: String): Pair<Double, Double>? {
        // Priority 1: query params q, ll, center
        val q = getQueryParam(uriString, "q") ?: getQueryParam(uriString, "ll") ?: getQueryParam(uriString, "center")
        if (!q.isNullOrEmpty()) {
            val matcher = LAT_LNG_REGEX.matcher(q)
            if (matcher.find()) {
                val lat = matcher.group(1)?.toDoubleOrNull()
                val lng = matcher.group(2)?.toDoubleOrNull()
                if (lat != null && lng != null) return Pair(lat, lng)
            }
        }

        // Priority 2: regex match on URI string
        val matcher = LAT_LNG_REGEX.matcher(uriString)
        if (matcher.find()) {
            val lat = matcher.group(1)?.toDoubleOrNull()
            val lng = matcher.group(2)?.toDoubleOrNull()
            if (lat != null && lng != null && lat in -90.0..90.0 && lng in -180.0..180.0) {
                return Pair(lat, lng)
            }
        }

        return null
    }

    fun parseText(text: String): ParsedLocation? {
        val matcher = LAT_LNG_REGEX.matcher(text)
        if (matcher.find()) {
            val lat = matcher.group(1)?.toDoubleOrNull()
            val lng = matcher.group(2)?.toDoubleOrNull()
            if (lat != null && lng != null && lat in -90.0..90.0 && lng in -180.0..180.0) {
                val nameStart = matcher.end()
                var name = text.substring(nameStart).trim()
                name = name.replace(Regex("""^[,\s\(\)\:：\-\|\n\r]+"""), "")
                if (name.isEmpty()) {
                    val prefix = text.substring(0, matcher.start()).trim()
                        .replace(Regex("""[,\s\(\)\:：\-\|\n\r]+$"""), "")
                    name = if (prefix.isNotEmpty()) prefix else "共享位置"
                }

                val (gcjLat, gcjLng) = CoordTransform.wgs84ToGcj02(lat, lng)
                return ParsedLocation(LatLng(gcjLat, gcjLng), name)
            }
        }

        // When no lat/lng numbers are found, extract clean address text (strip URLs and hashtags)
        val cleanText = text.replace(Regex("""https?://\S+"""), "")
            .replace(Regex("""#[^#\s]+#"""), "")
            .trim()
            .replace(Regex("""^[,\s\(\)\:：\-\|\n\r]+|[,\s\(\)\:：\-\|\n\r]+$"""), "")

        if (cleanText.isNotEmpty()) {
            return ParsedLocation(LatLng(0.0, 0.0), cleanText)
        }
        return null
    }

    fun shareLocation(context: Context, gcj02LatLng: LatLng, name: String) {
        val (wgsLat, wgsLng) = CoordTransform.gcj02ToWgs84(gcj02LatLng.latitude, gcj02LatLng.longitude)
        val encodedName = try { URLEncoder.encode(name, "UTF-8") } catch (e: Exception) { name }

        val geoUri = "geo:$wgsLat,$wgsLng?q=$wgsLat,$wgsLng($encodedName)"
        val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(geoUri))

        context.startActivity(Intent.createChooser(viewIntent, context.getString(R.string.share_location)))
    }

    private fun bd09ToGcj02(bdLat: Double, bdLng: Double): Pair<Double, Double> {
        val xPi = Math.PI * 3000.0 / 180.0
        val x = bdLng - 0.0065
        val y = bdLat - 0.006
        val z = Math.sqrt(x * x + y * y) - 0.00002 * Math.sin(y * xPi)
        val theta = Math.atan2(y, x) - 0.000003 * Math.cos(x * xPi)
        val gcjLng = z * Math.cos(theta)
        val gcjLat = z * Math.sin(theta)
        return Pair(gcjLat, gcjLng)
    }
}
