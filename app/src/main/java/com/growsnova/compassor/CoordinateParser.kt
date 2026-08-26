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

    private fun decodeUrl(str: String): String {
        return try {
            URLDecoder.decode(str, "UTF-8")
        } catch (e: Exception) {
            str
        }
    }

    fun parseUriString(uriString: String): ParsedLocation? {
        val uri = try { Uri.parse(uriString) } catch (e: Exception) { null }
        val lowerUri = uriString.lowercase()

        // 1. Handle geo: scheme (WGS-84)
        if (lowerUri.startsWith("geo:")) {
            val schemeSpecific = uriString.substring(4)
            var lat: Double? = null
            var lng: Double? = null
            var label: String? = null

            val queryIndex = uriString.indexOf("?")
            if (queryIndex != -1) {
                val queryString = uriString.substring(queryIndex + 1)
                for (param in queryString.split("&")) {
                    val kv = param.split("=")
                    if (kv.size == 2 && kv[0] == "q") {
                        val qVal = decodeUrl(kv[1])
                        val labelIndex = qVal.indexOf('(')
                        val coordPart = if (labelIndex != -1 && qVal.endsWith(")")) {
                            label = qVal.substring(labelIndex + 1, qVal.length - 1).trim()
                            qVal.substring(0, labelIndex)
                        } else {
                            qVal
                        }
                        val parts = coordPart.split(",")
                        if (parts.size >= 2) {
                            lat = parts[0].trim().toDoubleOrNull()
                            lng = parts[1].trim().toDoubleOrNull()
                        } else if (lat == null && lng == null) {
                            label = qVal
                        }
                    }
                }
            }

            if (lat == null || lng == null) {
                val pathPart = schemeSpecific.split("?")[0]
                val parts = pathPart.split(",")
                if (parts.size >= 2) {
                    lat = parts[0].trim().toDoubleOrNull()
                    lng = parts[1].trim().toDoubleOrNull()
                }
            }

            if (lat != null && lng != null && lat in -90.0..90.0 && lng in -180.0..180.0) {
                val (gcjLat, gcjLng) = CoordTransform.wgs84ToGcj02(lat, lng)
                val finalName = if (!label.isNullOrEmpty()) label else "共享位置"
                return ParsedLocation(LatLng(gcjLat, gcjLng), finalName)
            }
        }

        // 2. Handle AMap scheme (androidamap://) or AMap web URLs (GCJ-02)
        if (lowerUri.contains("androidamap://") || lowerUri.contains("amapuri://") || uriString.contains("uri.amap.com") || uriString.contains("ditu.amap.com")) {
            var lat: Double? = null
            var lng: Double? = null
            var name: String? = null

            val params = uriString.substringAfter("?", "").split("&")
            for (p in params) {
                val kv = p.split("=")
                if (kv.size == 2) {
                    val key = kv[0].lowercase()
                    val value = decodeUrl(kv[1])
                    if (key == "lat") lat = value.toDoubleOrNull()
                    if (key == "lon" || key == "lng") lng = value.toDoubleOrNull()
                    if (key == "name" || key == "title" || key == "poiname") name = value
                    if (key == "position") {
                        val pos = value.split(",")
                        if (pos.size >= 2) {
                            lng = pos[0].toDoubleOrNull()
                            lat = pos[1].toDoubleOrNull()
                        }
                    }
                }
            }
            if (lat != null && lng != null) {
                return ParsedLocation(LatLng(lat, lng), name ?: "高德地图位置")
            }
        }

        // 3. Handle Baidu Map scheme (baidumap:// or bdapp://) or Baidu web URLs
        if (lowerUri.contains("baidumap://") || lowerUri.contains("bdapp://") || uriString.contains("map.baidu.com")) {
            var lat: Double? = null
            var lng: Double? = null
            var name: String? = null

            val params = uriString.substringAfter("?", "").split("&")
            for (p in params) {
                val kv = p.split("=")
                if (kv.size == 2) {
                    val key = kv[0].lowercase()
                    val value = decodeUrl(kv[1])
                    if (key == "title" || key == "name") name = value
                    if (key == "location" || key == "center") {
                        val pos = value.split(",")
                        if (pos.size >= 2) {
                            lat = pos[0].toDoubleOrNull()
                            lng = pos[1].toDoubleOrNull()
                        }
                    }
                }
            }
            if (lat != null && lng != null) {
                // Baidu coordinates standard conversion to GCJ-02 if needed, or fallback
                val (gcjLat, gcjLng) = bd09ToGcj02(lat, lng)
                return ParsedLocation(LatLng(gcjLat, gcjLng), name ?: "百度地图位置")
            }
        }

        // 4. Handle Tencent Map scheme (qqmap://) or Tencent web URLs (GCJ-02)
        if (lowerUri.contains("qqmap://") || uriString.contains("map.qq.com") || uriString.contains("apis.map.qq.com")) {
            var lat: Double? = null
            var lng: Double? = null
            var name: String? = null

            val params = uriString.substringAfter("?", "").split("&")
            for (p in params) {
                val kv = p.split("=")
                if (kv.size == 2) {
                    val key = kv[0].lowercase()
                    val value = decodeUrl(kv[1])
                    if (key == "title") name = value
                    if (key == "marker") {
                        // marker=coord:lat,lng;title:Name
                        val markerParts = value.split(";")
                        for (mp in markerParts) {
                            if (mp.startsWith("coord:")) {
                                val coords = mp.substring(6).split(",")
                                if (coords.size >= 2) {
                                    lat = coords[0].toDoubleOrNull()
                                    lng = coords[1].toDoubleOrNull()
                                }
                            } else if (mp.startsWith("title:")) {
                                name = mp.substring(6)
                            }
                        }
                    }
                }
            }
            if (lat != null && lng != null) {
                return ParsedLocation(LatLng(lat, lng), name ?: "腾讯地图位置")
            }
        }

        // 5. Handle Google Maps scheme (google.navigation:) or Google web URLs (WGS-84)
        if (lowerUri.contains("google.navigation:") || uriString.contains("maps.google.com") || uriString.contains("google.com/maps") || uriString.contains("maps.app.goo.gl")) {
            val matcher = LAT_LNG_REGEX.matcher(uriString)
            if (matcher.find()) {
                val lat = matcher.group(1)?.toDoubleOrNull()
                val lng = matcher.group(2)?.toDoubleOrNull()
                if (lat != null && lng != null && lat in -90.0..90.0 && lng in -180.0..180.0) {
                    val (gcjLat, gcjLng) = CoordTransform.wgs84ToGcj02(lat, lng)
                    return ParsedLocation(LatLng(gcjLat, gcjLng), "谷歌地图位置")
                }
            }
        }

        return parseText(uriString)
    }

    fun parseText(text: String): ParsedLocation? {
        val matcher = LAT_LNG_REGEX.matcher(text)
        if (matcher.find()) {
            val lat = matcher.group(1)?.toDoubleOrNull()
            val lng = matcher.group(2)?.toDoubleOrNull()
            if (lat != null && lng != null && lat in -90.0..90.0 && lng in -180.0..180.0) {
                var name = text.replace(matcher.group(0) ?: "", "").trim()
                    .replace(Regex("""^[,\s\(\)\:：\-\|\n\r]+|[,\s\(\)\:：\-\|\n\r]+$"""), "")
                if (name.length > 30 || name.isEmpty()) {
                    name = "共享位置"
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

        // Standard Android geo: URI (compatible with all map apps natively)
        val geoUriString = "geo:$wgsLat,$wgsLng?q=$wgsLat,$wgsLng($encodedName)"

        // Web map links for AMap and Google Maps
        val amapWebUrl = "https://uri.amap.com/marker?position=${gcj02LatLng.longitude},${gcj02LatLng.latitude}&name=$encodedName"
        val googleWebUrl = "https://www.google.com/maps/search/?api=1&query=$wgsLat,$wgsLng"

        val shareText = "$name\n${String.format("%.6f, %.6f", wgsLat, wgsLng)}\n$geoUriString\n$amapWebUrl\n$googleWebUrl"

        // 1. Try launching standard geo: view intent chooser so user can open in map apps directly
        val viewGeoIntent = Intent(Intent.ACTION_VIEW, Uri.parse(geoUriString))

        // 2. Also prepare text sharing intent
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, name)
            putExtra(Intent.EXTRA_TEXT, shareText)
        }

        val chooser = Intent.createChooser(sendIntent, context.getString(R.string.share_location)).apply {
            putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(viewGeoIntent))
        }
        context.startActivity(chooser)
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
