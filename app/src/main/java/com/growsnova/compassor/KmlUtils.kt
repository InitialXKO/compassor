package com.growsnova.compassor

import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

object KmlUtils {

    fun exportWaypointsToKml(waypoints: List<Waypoint>): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n")
        sb.append("  <Document>\n")
        sb.append("    <name>Compassor Waypoints</name>\n")

        for (wp in waypoints) {
            val (wgsLat, wgsLng) = CoordTransform.gcj02ToWgs84(wp.latitude, wp.longitude)
            sb.append("    <Placemark>\n")
            sb.append("      <name>").append(escapeXml(wp.name)).append("</name>\n")
            if (!wp.remarks.isNullOrEmpty()) {
                sb.append("      <description>").append(escapeXml(wp.remarks!!)).append("</description>\n")
            }
            sb.append("      <Point>\n")
            sb.append("        <coordinates>").append(wgsLng).append(",").append(wgsLat).append("</coordinates>\n")
            sb.append("      </Point>\n")
            sb.append("    </Placemark>\n")
        }

        sb.append("  </Document>\n")
        sb.append("</kml>")
        return sb.toString()
    }

    fun exportRouteToKml(route: Route): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n")
        sb.append("  <Document>\n")
        sb.append("    <name>").append(escapeXml(route.name)).append("</name>\n")

        // 1. LineString Placemark
        sb.append("    <Placemark>\n")
        sb.append("      <name>").append(escapeXml(route.name)).append("</name>\n")
        sb.append("      <LineString>\n")
        sb.append("        <coordinates>\n")
        for (wp in route.waypoints) {
            val (wgsLat, wgsLng) = CoordTransform.gcj02ToWgs84(wp.latitude, wp.longitude)
            sb.append("          ").append(wgsLng).append(",").append(wgsLat).append("\n")
        }
        sb.append("        </coordinates>\n")
        sb.append("      </LineString>\n")
        sb.append("    </Placemark>\n")

        // 2. Waypoint Placemarks
        for (wp in route.waypoints) {
            val (wgsLat, wgsLng) = CoordTransform.gcj02ToWgs84(wp.latitude, wp.longitude)
            sb.append("    <Placemark>\n")
            sb.append("      <name>").append(escapeXml(wp.name)).append("</name>\n")
            if (!wp.remarks.isNullOrEmpty()) {
                sb.append("      <description>").append(escapeXml(wp.remarks!!)).append("</description>\n")
            }
            sb.append("      <Point>\n")
            sb.append("        <coordinates>").append(wgsLng).append(",").append(wgsLat).append("</coordinates>\n")
            sb.append("      </Point>\n")
            sb.append("    </Placemark>\n")
        }

        sb.append("  </Document>\n")
        sb.append("</kml>")
        return sb.toString()
    }

    fun importKmlToWaypoints(kmlStr: String): List<Waypoint> {
        val waypoints = mutableListOf<Waypoint>()
        try {
            val dbFactory = DocumentBuilderFactory.newInstance()
            dbFactory.isNamespaceAware = true
            val dBuilder = dbFactory.newDocumentBuilder()
            val doc = dBuilder.parse(ByteArrayInputStream(kmlStr.toByteArray(Charsets.UTF_8)))
            doc.documentElement.normalize()

            val placemarks = doc.getElementsByTagName("Placemark")
            for (i in 0 until placemarks.length) {
                val node = placemarks.item(i)
                if (node.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
                    val elem = node as org.w3c.dom.Element

                    val pointNodes = elem.getElementsByTagName("Point")
                    if (pointNodes.length > 0) {
                        val nameNode = elem.getElementsByTagName("name").item(0)
                        val name = nameNode?.textContent?.trim() ?: "KML地点"

                        val descNode = elem.getElementsByTagName("description").item(0)
                        val remarks = descNode?.textContent?.trim()

                        val pointElem = pointNodes.item(0) as org.w3c.dom.Element
                        val coordNode = pointElem.getElementsByTagName("coordinates").item(0)
                        val coordText = coordNode?.textContent?.trim() ?: ""

                        val parts = coordText.split(",")
                        if (parts.size >= 2) {
                            val wgsLng = parts[0].trim().toDoubleOrNull()
                            val wgsLat = parts[1].trim().toDoubleOrNull()
                            if (wgsLat != null && wgsLng != null) {
                                val (gcjLat, gcjLng) = CoordTransform.wgs84ToGcj02(wgsLat, wgsLng)
                                waypoints.add(
                                    Waypoint(
                                        id = 0L,
                                        name = name,
                                        latitude = gcjLat,
                                        longitude = gcjLng,
                                        remarks = remarks
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // XML parsing error
        }
        return waypoints
    }

    fun importKmlToRoute(kmlStr: String): Pair<Route, List<Waypoint>>? {
        val waypoints = importKmlToWaypoints(kmlStr)
        if (waypoints.isEmpty()) return null

        var routeName = "KML Route"
        try {
            val dbFactory = DocumentBuilderFactory.newInstance()
            dbFactory.isNamespaceAware = true
            val dBuilder = dbFactory.newDocumentBuilder()
            val doc = dBuilder.parse(ByteArrayInputStream(kmlStr.toByteArray(Charsets.UTF_8)))
            val docNameNode = doc.getElementsByTagName("Document").item(0) as? org.w3c.dom.Element
            val nameNode = docNameNode?.getElementsByTagName("name")?.item(0)
            if (nameNode != null && !nameNode.textContent.isNullOrEmpty()) {
                routeName = nameNode.textContent.trim()
            }
        } catch (e: Exception) {
            // Ignore
        }

        val route = Route(
            id = 0L,
            name = routeName,
            isLooping = false,
            waypoints = waypoints.toMutableList()
        )
        return Pair(route, waypoints)
    }

    private fun escapeXml(str: String): String {
        return str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
