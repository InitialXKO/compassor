package com.growsnova.compassor

import android.content.Context

object FloorUtils {
    /** 将高德 indoorData.floor 的各种格式归一化为 Int? */
    fun parseFloor(raw: Any?): Int? {
        if (raw == null) return null
        if (raw is Int) return if (raw != 0) raw else null
        val str = raw.toString().trim()
        if (str.isEmpty()) return null

        // 纯数字："1", "-1", "3"
        str.toIntOrNull()?.let { return if (it != 0) it else null }

        // 带 F 前缀："F1", "F3", "f2"
        val stripped = str.removePrefix("F").removePrefix("f")
        val parsed = stripped.toIntOrNull()
        return if (parsed != null && parsed != 0) parsed else null
    }

    fun parseFloor(floor: Int?): Int? = if (floor != 0) floor else null

    /** 从 POI 对象（indoorData, title, snippet, address, typeDes 等）中提取或解析楼层 */
    fun extractFloorFromPoi(poiItem: com.amap.api.services.core.PoiItem?): Int? {
        if (poiItem == null) return null

        // 优先解析 indoorData.floor
        val indoorFloor = parseFloor(poiItem.indoorData?.floor)
        if (indoorFloor != null) return indoorFloor

        // 遍历各类文本信息（snippet/address/title/typeDes）中包含的楼层特征
        val textSources = listOfNotNull(
            poiItem.snippet,
            poiItem.title,
            poiItem.typeDes,
            poiItem.adName
        )

        for (text in textSources) {
            val floor = extractFloorFromText(text)
            if (floor != null) return floor
        }

        return null
    }

    /** 从文本中解析楼层信息，如 "3楼", "B1层", "负2层", "F3", "地下1层", "2F" 等 */
    fun extractFloorFromText(text: String?): Int? {
        if (text.isNullOrBlank()) return null

        // 1. 地下 / 负层 / B层：例如 "地下1楼", "地下2层", "负1楼", "负2层", "B1层", "B2楼", "B1", "b2"
        val basementRegexes = listOf(
            Regex("""(?:地下|负)\s*([1-9]\d*)\s*[楼层]"""),
            Regex("""(?<![A-Za-z0-9])[Bb]([1-9]\d*)\s*(?:[楼层]|F|f)?""")
        )
        for (regex in basementRegexes) {
            val match = regex.find(text)
            if (match != null) {
                val num = match.groupValues[1].toIntOrNull()
                if (num != null && num > 0) return -num
            }
        }

        // 2. 地上楼层：例如 "3楼", "3层", "12层", "F3", "f3", "3F", "3f"
        val floorRegexes = listOf(
            Regex("""([1-9]\d*)\s*[楼层]"""),
            Regex("""(?<![A-Za-z0-9])[Ff]([1-9]\d*)(?![0-9])"""),
            Regex("""(?<![A-Za-z0-9])([1-9]\d*)[Ff](?![A-Za-z0-9])""")
        )
        for (regex in floorRegexes) {
            val match = regex.find(text)
            if (match != null) {
                val num = match.groupValues[1].toIntOrNull()
                if (num != null && num > 0) return num
            }
        }

        return null
    }

    /** 格式化显示："3楼", "-1楼" → "F3", "B1" 等 */
    fun formatFloor(floor: Int?, context: Context): String? {
        if (floor == null) return null
        return if (floor >= 0) {
            context.getString(R.string.floor_format, floor)
        } else {
            context.getString(R.string.basement_floor_format, -floor)
        }
    }
}
