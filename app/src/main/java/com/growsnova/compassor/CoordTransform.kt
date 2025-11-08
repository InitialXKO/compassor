package com.growsnova.compassor

/**
 * 坐标转换工具（WGS‑84 ⇄ GCJ‑02）
 * 参考公开的 Java 实现并直接转写为 Kotlin。
 * 
 * 手机 GPS 获得的原始坐标采用国际标准 WGS‑84，而高德地图对外提供的坐标是 GCJ‑02（火星坐标）。
 * 如果直接把两者的经纬度在同一地图上叠加，必然出现数十到上百米的偏移。
 * 必须先转换得到 GCJ‑02 坐标后再在高德地图上绘制。
 */
object CoordTransform {
    private const val PI = Math.PI
    private const val A = 6378245.0            // 长半轴
    private const val EE = 0.00669342162296594323   // 偏心率平方

    /** 判断坐标是否在中国境内，境外直接返回原坐标 */
    private fun outOfChina(lat: Double, lng: Double): Boolean {
        return (lng < 72.004 || lng > 137.8347) ||
               (lat < 0.8293 || lat > 55.8271)
    }

    /** 纬度偏移量 */
    private fun transformLat(x: Double, y: Double): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y +
                  0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x))
        ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * Math.sin(y * PI) + 40.0 * Math.sin(y / 3.0 * PI)) * 2.0 / 3.0
        ret += (160.0 * Math.sin(y / 12.0 * PI) + 320 * Math.sin(y * PI / 30.0)) * 2.0 / 3.0
        return ret
    }

    /** 经度偏移量 */
    private fun transformLng(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x +
                  0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x))
        ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * Math.sin(x * PI) + 40.0 * Math.sin(x / 3.0 * PI)) * 2.0 / 3.0
        ret += (150.0 * Math.sin(x / 12.0 * PI) + 300.0 * Math.sin(x / 30.0 * PI)) * 2.0 / 3.0
        return ret
    }

    /** WGS‑84 → GCJ‑02（高德/腾讯/谷歌国内坐标系） */
    fun wgs84ToGcj02(lat: Double, lng: Double): Pair<Double, Double> {
        if (outOfChina(lat, lng)) return Pair(lat, lng)

        val dLat = transformLat(lng - 105.0, lat - 35.0)
        val dLng = transformLng(lng - 105.0, lat - 35.0)
        val radLat = lat / 180.0 * PI
        var magic = Math.sin(radLat)
        magic = 1 - EE * magic * magic
        val sqrtMagic = Math.sqrt(magic)
        val mgLat = lat + dLat * 180.0 / ((A * (1 - EE)) / (magic * sqrtMagic) * PI)
        val mgLng = lng + dLng * 180.0 / (A / sqrtMagic * Math.cos(radLat) * PI)
        return Pair(mgLat, mgLng)
    }

    /** GCJ‑02 → WGS‑84（一次迭代，误差约 1‑2 m） */
    fun gcj02ToWgs84(lat: Double, lng: Double): Pair<Double, Double> {
        if (outOfChina(lat, lng)) return Pair(lat, lng)

        // 先把 GCJ‑02 当作 WGS‑84 进行一次正向转换，再做反向修正
        val (tmpLat, tmpLng) = wgs84ToGcj02(lat, lng)
        val wgsLat = lat * 2 - tmpLat
        val wgsLng = lng * 2 - tmpLng
        return Pair(wgsLat, wgsLng)
    }
}
