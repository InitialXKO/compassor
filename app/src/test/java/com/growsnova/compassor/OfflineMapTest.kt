package com.growsnova.compassor

import com.amap.api.maps.offlinemap.OfflineMapStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineMapTest {

    @Test
    fun testOfflineMapItemCreation() {
        val item = OfflineMapItem(
            cityName = "Beijing",
            cityCode = "010",
            size = 10485760L, // 10 MB
            state = OfflineMapStatus.SUCCESS,
            completeCode = 100,
            provinceName = "Beijing"
        )

        assertEquals("Beijing", item.cityName)
        assertEquals("010", item.cityCode)
        assertEquals(10485760L, item.size)
        assertEquals(OfflineMapStatus.SUCCESS, item.state)
        assertEquals(100, item.completeCode)
        assertEquals("Beijing", item.provinceName)
    }

    @Test
    fun testSizeCalculation() {
        val bytes = 52428800L // 50 MB
        val mb = bytes.toDouble() / (1024 * 1024)
        assertEquals(50.0, mb, 0.001)
    }

    @Test
    fun testOfflineMapStatusConstants() {
        assertEquals(4, OfflineMapStatus.SUCCESS)
        assertEquals(0, OfflineMapStatus.LOADING)
        assertEquals(3, OfflineMapStatus.PAUSE)
        assertEquals(-1, OfflineMapStatus.ERROR)
        assertEquals(101, OfflineMapStatus.EXCEPTION_NETWORK_LOADING)
        assertEquals(102, OfflineMapStatus.EXCEPTION_AMAP)
        assertEquals(103, OfflineMapStatus.EXCEPTION_SDCARD)
        assertEquals(1002, OfflineMapStatus.START_DOWNLOAD_FAILD)
    }
}
