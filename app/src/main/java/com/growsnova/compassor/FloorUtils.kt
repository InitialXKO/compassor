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

        // 带 F/L 前缀："F1", "F3", "f2", "L1", "L3"
        val stripped = str.removePrefix("F").removePrefix("f").removePrefix("L").removePrefix("l")
        val parsed = stripped.toIntOrNull()
        return if (parsed != null && parsed != 0 && parsed < 100) parsed else null
    }

    fun parseFloor(floor: Int?): Int? = if (floor != null && floor != 0) floor else null

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

    /** 从中文数字或字符串解析 1~99 的整数 */
    private fun parseChineseNumeral(str: String): Int? {
        val map = mapOf(
            '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4,
            '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9, '十' to 10
        )
        if (str.isEmpty()) return null
        if (str.length == 1) return map[str[0]]
        if (str == "十") return 10
        if (str.startsWith("十")) {
            val digit = map[str[1]] ?: return null
            return 10 + digit
        }
        if (str.endsWith("十")) {
            val tens = map[str[0]] ?: return null
            return tens * 10
        }
        if (str.length == 3 && str[1] == '十') {
            val tens = map[str[0]] ?: return null
            val ones = map[str[2]] ?: return null
            return tens * 10 + ones
        }
        return null
    }

    /** 从文本中解析楼层信息，如 "3楼", "二层", "L3", "B1层", "负2层", "F3", "地下1层", "2F" 等 */
    fun extractFloorFromText(text: String?): Int? {
        if (text.isNullOrBlank()) return null

        val shopSuffixBlock = """(?![0-9]|号|铺|店|室|单元|商铺|柜台)"""

        // 1. 地下 / 负层 / B层
        val cnBasementMatch = Regex("""(?:地下|负)\s*([一二两三四五六七八九十]{1,3})\s*[楼层]""").find(text)
        if (cnBasementMatch != null) {
            val num = parseChineseNumeral(cnBasementMatch.groupValues[1])
            if (num != null && num in 1..99) return -num
        }

        val numBasementMatch = Regex("""(?:地下|负)\s*([1-9]\d?)\s*[楼层]""").find(text)
        if (numBasementMatch != null) {
            val num = numBasementMatch.groupValues[1].toIntOrNull()
            if (num != null && num in 1..99) return -num
        }

        val bFloorMatch = Regex("""(?<![A-Za-z0-9])[Bb]([1-9]\d?)$shopSuffixBlock""").find(text)
        if (bFloorMatch != null) {
            val num = bFloorMatch.groupValues[1].toIntOrNull()
            if (num != null && num in 1..99) return -num
        }

        // 2. 地上楼层
        val cnFloorMatch = Regex("""([一二两三四五六七八九十]{1,3})\s*[楼层]""").find(text)
        if (cnFloorMatch != null) {
            val num = parseChineseNumeral(cnFloorMatch.groupValues[1])
            if (num != null && num in 1..99) return num
        }

        val numFloorMatch = Regex("""([1-9]\d?)\s*[楼层]""").find(text)
        if (numFloorMatch != null) {
            val num = numFloorMatch.groupValues[1].toIntOrNull()
            if (num != null && num in 1..99) return num
        }

        val lfPrefixMatch = Regex("""(?<![A-Za-z0-9])[LlFf]([1-9]\d?)$shopSuffixBlock""").find(text)
        if (lfPrefixMatch != null) {
            val num = lfPrefixMatch.groupValues[1].toIntOrNull()
            if (num != null && num in 1..99) return num
        }

        val fSuffixMatch = Regex("""(?<![A-Za-z0-9])([1-9]\d?)[Ff]$shopSuffixBlock""").find(text)
        if (fSuffixMatch != null) {
            val num = fSuffixMatch.groupValues[1].toIntOrNull()
            if (num != null && num in 1..99) return num
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
