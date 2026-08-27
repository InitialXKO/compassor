package com.growsnova.compassor

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.amap.api.maps.model.LatLng
import java.net.URLEncoder

object ShareUtils {

    fun shareWaypointText(context: Context, name: String, gcjLat: Double, gcjLng: Double) {
        val (wgsLat, wgsLng) = CoordTransform.gcj02ToWgs84(gcjLat, gcjLng)
        val encodedName = try { URLEncoder.encode(name, "UTF-8") } catch (e: Exception) { name }
        val geoUriStr = "geo:$wgsLat,$wgsLng?q=$wgsLat,$wgsLng($encodedName)"
        val shareText = buildString {
            appendLine(name)
            appendLine("${String.format("%.6f, %.6f", wgsLat, wgsLng)} (WGS-84)")
            appendLine(geoUriStr)
            appendLine("https://www.amap.com/place/$gcjLat,$gcjLng")
        }

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_SUBJECT, name)
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }
        context.startActivity(Intent.createChooser(sendIntent, context.getString(R.string.share_location)))
    }

    fun openInMaps(context: Context, name: String, gcjLat: Double, gcjLng: Double) {
        val (wgsLat, wgsLng) = CoordTransform.gcj02ToWgs84(gcjLat, gcjLng)
        val encodedName = try { URLEncoder.encode(name, "UTF-8") } catch (e: Exception) { name }
        val geoUri = "geo:$wgsLat,$wgsLng?q=$wgsLat,$wgsLng($encodedName)"

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(geoUri))
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_location)))
    }
}
