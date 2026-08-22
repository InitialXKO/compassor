package com.growsnova.compassor

import android.content.Context

object FloorUtils {
    /** 将高德 indoorData.floor 的各种格式归一化为 Int? */
    fun parseFloor(raw: Any?): Int? {
        if (raw == null) return null
        if (raw is Int) return raw
        val str = raw.toString()
        // 纯数字："1", "-1", "3"
        str.toIntOrNull()?.let { return it }
        // 带 F 前缀："F1", "F3", "f2"
        val stripped = str.removePrefix("F").removePrefix("f")
        return stripped.toIntOrNull()
    }

    fun parseFloor(floor: Int?): Int? = floor

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
