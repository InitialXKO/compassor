package com.growsnova.compassor.service

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import org.junit.Assert.assertNotNull
import org.junit.Test

class NavigationServiceTest {

    private class TestContext : ContextWrapper(null) {
        var stoppedIntent: Intent? = null

        override fun stopService(service: Intent?): Boolean {
            stoppedIntent = service
            return true
        }

        override fun getPackageName(): String = "com.growsnova.compassor"
    }

    @Test
    fun testStopServiceCallsStopServiceOnContext() {
        val testContext = TestContext()
        NavigationService.stop(testContext)

        assertNotNull(testContext.stoppedIntent)
    }
}
