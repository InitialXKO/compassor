package com.growsnova.compassor

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject

object GeoJsonUtils {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    fun exportWaypointsToGeoJson(waypoints: List<Waypoint>): String {
        val featureCollection = JsonObject()
        featureCollection.addProperty("type", "FeatureCollection")

        val features = JsonArray()
        for (wp in waypoints) {
            val feature = JsonObject()
            feature.addProperty("type", "Feature")

            val geometry = JsonObject()
            geometry.addProperty("type", "Point")
            val coordinates = JsonArray()
            val (wgsLat, wgsLng) = CoordTransform.gcj02ToWgs84(wp.latitude, wp.longitude)
            coordinates.add(wgsLng)
            coordinates.add(wgsLat)
            geometry.add("coordinates", coordinates)
            feature.add("geometry", geometry)

            val properties = JsonObject()
            properties.addProperty("name", wp.name)
            wp.remarks?.let { properties.addProperty("remarks", it) }
            wp.floor?.let { properties.addProperty("floor", it) }
            feature.add("properties", properties)

            features.add(feature)
        }
        featureCollection.add("features", features)
        return gson.toJson(featureCollection)
    }

    fun exportRouteToGeoJson(route: Route): String {
        val featureCollection = JsonObject()
        featureCollection.addProperty("type", "FeatureCollection")

        val features = JsonArray()

        // 1. LineString feature for the route line
        val lineFeature = JsonObject()
        lineFeature.addProperty("type", "Feature")

        val lineGeometry = JsonObject()
        lineGeometry.addProperty("type", "LineString")
        val lineCoordinates = JsonArray()

        for (wp in route.waypoints) {
            val coord = JsonArray()
            val (wgsLat, wgsLng) = CoordTransform.gcj02ToWgs84(wp.latitude, wp.longitude)
            coord.add(wgsLng)
            coord.add(wgsLat)
            lineCoordinates.add(coord)
        }
        lineGeometry.add("coordinates", lineCoordinates)
        lineFeature.add("geometry", lineGeometry)

        val lineProperties = JsonObject()
        lineProperties.addProperty("name", route.name)
        lineProperties.addProperty("isLooping", route.isLooping)
        lineFeature.add("properties", lineProperties)

        features.add(lineFeature)

        // 2. Point features for waypoints
        for (wp in route.waypoints) {
            val pointFeature = JsonObject()
            pointFeature.addProperty("type", "Feature")

            val pointGeometry = JsonObject()
            pointGeometry.addProperty("type", "Point")
            val coord = JsonArray()
            val (wgsLat, wgsLng) = CoordTransform.gcj02ToWgs84(wp.latitude, wp.longitude)
            coord.add(wgsLng)
            coord.add(wgsLat)
            pointGeometry.add("coordinates", coord)
            pointFeature.add("geometry", pointGeometry)

            val pointProps = JsonObject()
            pointProps.addProperty("name", wp.name)
            wp.remarks?.let { pointProps.addProperty("remarks", it) }
            wp.floor?.let { pointProps.addProperty("floor", it) }
            pointFeature.add("properties", pointProps)

            features.add(pointFeature)
        }

        featureCollection.add("features", features)
        return gson.toJson(featureCollection)
    }

    fun importGeoJsonToWaypoints(geoJsonStr: String): List<Waypoint> {
        val waypoints = mutableListOf<Waypoint>()
        try {
            val root = gson.fromJson(geoJsonStr, JsonObject::class.java) ?: return emptyList()
            val type = root.get("type")?.asString

            val featuresArray = if (type == "FeatureCollection") {
                root.getAsJsonArray("features")
            } else if (type == "Feature") {
                val arr = JsonArray()
                arr.add(root)
                arr
            } else {
                return emptyList()
            }

            for (elem in featuresArray) {
                val feature = elem.asJsonObject
                val geom = feature.getAsJsonObject("geometry") ?: continue
                val geomType = geom.get("type")?.asString ?: continue

                if (geomType == "Point") {
                    val coords = geom.getAsJsonArray("coordinates") ?: continue
                    if (coords.size() >= 2) {
                        val wgsLng = coords.get(0).asDouble
                        val wgsLat = coords.get(1).asDouble
                        val (gcjLat, gcjLng) = CoordTransform.wgs84ToGcj02(wgsLat, wgsLng)

                        val props = feature.getAsJsonObject("properties")
                        val name = props?.get("name")?.asString ?: "GeoJSON地点"
                        val remarks = props?.get("remarks")?.asString
                        val floor = props?.get("floor")?.asInt

                        waypoints.add(
                            Waypoint(
                                id = 0L,
                                name = name,
                                latitude = gcjLat,
                                longitude = gcjLng,
                                remarks = remarks,
                                floor = floor
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // Parse error
        }
        return waypoints
    }

    fun importGeoJsonToRoute(geoJsonStr: String): Pair<Route, List<Waypoint>>? {
        val waypoints = importGeoJsonToWaypoints(geoJsonStr)
        if (waypoints.isEmpty()) return null

        var routeName = "GeoJSON Route"
        var isLooping = false

        try {
            val root = gson.fromJson(geoJsonStr, JsonObject::class.java)
            val features = root?.getAsJsonArray("features")
            if (features != null) {
                for (elem in features) {
                    val props = elem.asJsonObject.getAsJsonObject("properties")
                    if (props != null && props.has("isLooping")) {
                        isLooping = props.get("isLooping").asBoolean
                    }
                    if (props != null && props.has("name") && elem.asJsonObject.getAsJsonObject("geometry")?.get("type")?.asString == "LineString") {
                        routeName = props.get("name").asString
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }

        val route = Route(
            id = 0L,
            name = routeName,
            isLooping = isLooping,
            waypoints = waypoints.toMutableList()
        )
        return Pair(route, waypoints)
    }
}
