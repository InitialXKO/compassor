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
        val lowerUri = uriString.lowercase()

        if (lowerUri.startsWith("geo:")) {
            val schemeSpecific = uriString.substring(4)
            var lat: Double? = null
            var lng: Double? = null
            var label: String? = null

            // First check q parameter if present
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
                // geo: coordinates are WGS-84 standard, convert to GCJ-02
                val (gcjLat, gcjLng) = CoordTransform.wgs84ToGcj02(lat, lng)
                val finalName = if (!label.isNullOrEmpty()) label else "共享位置"
                return ParsedLocation(LatLng(gcjLat, gcjLng), finalName)
            }
        }

        // Check AMap or Google URLs via query params / regex
        if (uriString.contains("position=") || uriString.contains("name=")) {
            var lat: Double? = null
            var lng: Double? = null
            var name: String? = null

            val params = uriString.substringAfter("?", "").split("&")
            for (p in params) {
                val kv = p.split("=")
                if (kv.size == 2) {
                    if (kv[0] == "position") {
                        val pos = decodeUrl(kv[1]).split(",")
                        if (pos.size >= 2) {
                            lng = pos[0].toDoubleOrNull()
                            lat = pos[1].toDoubleOrNull()
                        }
                    } else if (kv[0] == "name") {
                        name = decodeUrl(kv[1])
                    }
                }
            }
            if (lat != null && lng != null) {
                // AMap position is already GCJ-02
                return ParsedLocation(LatLng(lat, lng), name ?: "共享位置")
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
                // Extract possible name before or after coordinates
                var name = text.replace(matcher.group(0) ?: "", "").trim()
                    .replace(Regex("""^[,\s\(\)\:：]+|[,\s\(\)\:：]+$"""), "")
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

        val geoUriString = "geo:$wgsLat,$wgsLng?q=$wgsLat,$wgsLng($encodedName)"
        val amapWebUrl = "https://uri.amap.com/marker?position=${gcj02LatLng.longitude},${gcj02LatLng.latitude}&name=$encodedName"

        val shareText = "$name\n${String.format("%.6f, %.6f", wgsLat, wgsLng)}\n$geoUriString\n$amapWebUrl"

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, name)
            putExtra(Intent.EXTRA_TEXT, shareText)
        }

        val chooser = Intent.createChooser(sendIntent, context.getString(R.string.share_location))
        context.startActivity(chooser)
    }
}
