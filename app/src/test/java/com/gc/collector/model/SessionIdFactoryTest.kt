package com.gc.collector.model

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionIdFactoryTest {
    @Test
    fun createsStableSessionIdForSameDeviceAndTimestamp() {
        val first = SessionIdFactory.create(
            deviceId = "android 01",
            timestampMs = 1_768_649_530_123L,
        )
        val second = SessionIdFactory.create(
            deviceId = "android 01",
            timestampMs = 1_768_649_530_123L,
        )

        assertTrue(first == second)
        assertTrue(first.startsWith("android_01_"))
    }

    @Test
    fun createsDifferentSessionIdForDifferentStartTime() {
        val first = SessionIdFactory.create(
            deviceId = "android_01",
            timestampMs = 1_768_649_530_123L,
        )
        val second = SessionIdFactory.create(
            deviceId = "android_01",
            timestampMs = 1_768_649_531_123L,
        )

        assertNotEquals(first, second)
    }

    @Test
    fun fallsBackWhenDeviceIdIsBlank() {
        val sessionId = SessionIdFactory.create(
            deviceId = " ",
            timestampMs = 1_768_649_530_123L,
        )

        assertTrue(sessionId.startsWith("android_device_"))
    }
}
