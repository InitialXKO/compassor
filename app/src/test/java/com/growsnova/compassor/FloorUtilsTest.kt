package com.growsnova.compassor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FloorUtilsTest {

    @Test
    fun testExtractFloorFromText() {
        assertEquals(1, FloorUtils.extractFloorFromText("一楼"))
        assertEquals(2, FloorUtils.extractFloorFromText("二层"))
        assertEquals(3, FloorUtils.extractFloorFromText("三楼"))
        assertEquals(10, FloorUtils.extractFloorFromText("十层"))
        assertEquals(12, FloorUtils.extractFloorFromText("十二楼"))
        assertEquals(25, FloorUtils.extractFloorFromText("二十五层"))

        assertEquals(-1, FloorUtils.extractFloorFromText("负一层"))
        assertEquals(-2, FloorUtils.extractFloorFromText("地下二楼"))
        assertEquals(-1, FloorUtils.extractFloorFromText("负1楼"))
        assertEquals(-2, FloorUtils.extractFloorFromText("地下2层"))
        assertEquals(-1, FloorUtils.extractFloorFromText("B1"))
        assertEquals(-2, FloorUtils.extractFloorFromText("B2层"))

        assertEquals(3, FloorUtils.extractFloorFromText("L3"))
        assertEquals(12, FloorUtils.extractFloorFromText("L12"))
        assertEquals(2, FloorUtils.extractFloorFromText("F2"))
        assertEquals(2, FloorUtils.extractFloorFromText("2F"))

        // Exclusions (shop numbers / long unit numbers)
        assertNull(FloorUtils.extractFloorFromText("L1003"))
        assertNull(FloorUtils.extractFloorFromText("L1003商铺"))
        assertNull(FloorUtils.extractFloorFromText("L3商铺"))
        assertNull(FloorUtils.extractFloorFromText("1003号商铺"))
    }
}
