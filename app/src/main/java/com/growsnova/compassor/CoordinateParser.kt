package com.growsnova.compassor

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.amap.api.maps.model.LatLng
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
        val action = intent.action
        val dataStr = intent.dataString
        val textExtra = intent.getStringExtra(Intent.EXTRA_TEXT)

        if (Intent.ACTION_VIEW == action && !dataStr.isNullOrEmpty()) {
            return parseUriString(dataStr)
        } else if (Intent.ACTION_SEND == action && !textExtra.isNullOrEmpty()) {
            return parseText(textExtra)
        } else if (!dataStr.isNullOrEmpty()) {
            return parseUriString(dataStr)
        } else if (!textExtra.isNullOrEmpty()) {
            return parseText(textExtra)
        }
        return null
    }

    fun parseUriString(uriString: String): ParsedLocation? {
        val uri = try { Uri.parse(uriString) } catch (e: Exception) { return null }
        val lower = uriString.lowercase()

        // 1. geo: scheme (WGS-84 standard)
        if (lower.startsWith("geo:")) {
            val coords = extractGeoCoordinates(uri, uriString)
            if (coords != null) {
                val (gcjLat, gcjLng) = CoordTransform.wgs84ToGcj02(coords.first, coords.second)
                val qParam = uri.getQueryParameter("q")
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
        if (lower.contains("androidamap://") || lower.contains("amapuri://") ||
            uri.host?.contains("amap.com") == true || uri.host?.contains("uri.amap.com") == true) {
            val coords = extractAmapCoordinates(uri)
            if (coords != null) {
                val name = uri.getQueryParameter("name")
                    ?: uri.getQueryParameter("title")
                    ?: uri.getQueryParameter("poiname")
                    ?: "高德位置"
                return ParsedLocation(LatLng(coords.first, coords.second), name)
            }
        }

        // 3. Baidu Map (BD-09)
        if (lower.contains("baidumap://") || lower.contains("bdapp://") ||
            uri.host?.contains("map.baidu.com") == true) {
            val coords = extractBaiduCoordinates(uri)
            if (coords != null) {
                val (gcjLat, gcjLng) = bd09ToGcj02(coords.first, coords.second)
                val name = uri.getQueryParameter("title") ?: uri.getQueryParameter("name") ?: "百度位置"
                return ParsedLocation(LatLng(gcjLat, gcjLng), name)
            }
            // Shortlinks (j.map.baidu.com) return null, allowing caller/browser handling
        }

        // 4. Tencent Map (GCJ-02)
        if (lower.contains("qqmap://") || uri.host?.contains("map.qq.com") == true ||
            uri.host?.contains("apis.map.qq.com") == true) {
            val coords = extractTencentCoordinates(uri)
            if (coords != null) {
                val name = uri.getQueryParameter("title") ?: "腾讯位置"
                return ParsedLocation(LatLng(coords.first, coords.second), name)
            }
        }

        // 5. Google Maps (WGS-84)
        if (lower.contains("google.navigation:") || uri.host?.contains("maps.google.com") == true ||
            uri.host?.contains("google.com/maps") == true || uri.host?.contains("maps.app.goo.gl") == true) {
            val coords = extractGoogleCoordinates(uri, uriString)
            if (coords != null) {
                val (gcjLat, gcjLng) = CoordTransform.wgs84ToGcj02(coords.first, coords.second)
                return ParsedLocation(LatLng(gcjLat, gcjLng), "谷歌位置")
            }
        }

        // 6. Fallback to parseText
        return parseText(uriString)
    }

    private fun extractGeoCoordinates(uri: Uri, uriString: String): Pair<Double, Double>? {
        // First try q= query parameter (geo:0,0?q=lat,lng(label) or geo:?q=lat,lng)
        val q = uri.getQueryParameter("q")
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
        val pathPart = uri.schemeSpecificPart.substringBefore("?")
        val parts = pathPart.split(",")
        if (parts.size >= 2) {
            val lat = parts[0].toDoubleOrNull()
            val lng = parts[1].toDoubleOrNull()
            if (lat != null && lng != null && lat in -90.0..90.0 && lng in -180.0..180.0) {
                return Pair(lat, lng)
            }
        }

        return null
    }

    private fun extractAmapCoordinates(uri: Uri): Pair<Double, Double>? {
        // Priority 1: position=lng,lat (AMap standard sequence)
        uri.getQueryParameter("position")?.let { pos ->
            val parts = pos.split(",")
            if (parts.size >= 2) {
                val lng = parts[0].toDoubleOrNull()
                val lat = parts[1].toDoubleOrNull()
                if (lat != null && lng != null) return Pair(lat, lng)
            }
        }

        // Priority 2: lat & lon/lng
        val lat = uri.getQueryParameter("lat")?.toDoubleOrNull()
        val lng = uri.getQueryParameter("lon")?.toDoubleOrNull() ?: uri.getQueryParameter("lng")?.toDoubleOrNull()
        if (lat != null && lng != null) return Pair(lat, lng)

        return null
    }

    private fun extractBaiduCoordinates(uri: Uri): Pair<Double, Double>? {
        // Priority 1: location=lat,lng or center=lat,lng
        val loc = uri.getQueryParameter("location") ?: uri.getQueryParameter("center")
        if (!loc.isNullOrEmpty()) {
            val parts = loc.split(",")
            if (parts.size >= 2) {
                val lat = parts[0].toDoubleOrNull()
                val lng = parts[1].toDoubleOrNull()
                if (lat != null && lng != null) return Pair(lat, lng)
            }
        }

        // Priority 2: lat & lng/lon
        val lat = uri.getQueryParameter("lat")?.toDoubleOrNull()
        val lng = uri.getQueryParameter("lng")?.toDoubleOrNull() ?: uri.getQueryParameter("lon")?.toDoubleOrNull()
        if (lat != null && lng != null) return Pair(lat, lng)

        return null
    }

    private fun extractTencentCoordinates(uri: Uri): Pair<Double, Double>? {
        // Priority 1: marker=coord:lat,lng;title:Name
        uri.getQueryParameter("marker")?.let { marker ->
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
        val center = uri.getQueryParameter("center") ?: uri.getQueryParameter("location")
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

    private fun extractGoogleCoordinates(uri: Uri, uriString: String): Pair<Double, Double>? {
        // Priority 1: query params q, ll, center
        val q = uri.getQueryParameter("q") ?: uri.getQueryParameter("ll") ?: uri.getQueryParameter("center")
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
        return null
    }

    fun shareLocation(context: Context, gcj02LatLng: LatLng, name: String) {
        val (wgsLat, wgsLng) = CoordTransform.gcj02ToWgs84(gcj02LatLng.latitude, gcj02LatLng.longitude)
        val encodedName = try { URLEncoder.encode(name, "UTF-8") } catch (e: Exception) { name }

        val geoUri = "geo:$wgsLat,$wgsLng?q=$wgsLat,$wgsLng($encodedName)"
        val amapLink = "https://uri.amap.com/marker?position=${gcj02LatLng.longitude},${gcj02LatLng.latitude}&name=$encodedName"
        val googleLink = "https://www.google.com/maps/search/?api=1&query=$wgsLat,$wgsLng"

        val shareText = """
            $name
            ${String.format("%.6f, %.6f", wgsLat, wgsLng)}
            $geoUri
            $amapLink
            $googleLink
        """.trimIndent()

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, name)
            putExtra(Intent.EXTRA_TEXT, shareText)
        }

        context.startActivity(Intent.createChooser(sendIntent, context.getString(R.string.share_location)))
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
