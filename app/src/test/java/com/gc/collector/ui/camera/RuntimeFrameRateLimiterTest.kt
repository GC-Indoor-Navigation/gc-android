package com.gc.collector.ui.camera

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeFrameRateLimiterTest {
    @Test
    fun firstFrameIsEmitted() {
        val limiter = RuntimeFrameRateLimiter()

        assertTrue(limiter.shouldEmit(sensorTimestampNs = 1_000L, targetFps = 10))
    }

    @Test
    fun frameBeforeTargetIntervalIsDropped() {
        val limiter = RuntimeFrameRateLimiter()

        assertTrue(limiter.shouldEmit(sensorTimestampNs = 0L, targetFps = 10))
        assertFalse(limiter.shouldEmit(sensorTimestampNs = 99_000_000L, targetFps = 10))
    }

    @Test
    fun frameAtTargetIntervalIsEmitted() {
        val limiter = RuntimeFrameRateLimiter()

        assertTrue(limiter.shouldEmit(sensorTimestampNs = 0L, targetFps = 10))
        assertTrue(limiter.shouldEmit(sensorTimestampNs = 100_000_000L, targetFps = 10))
    }

    @Test
    fun resetAllowsNextFrameImmediately() {
        val limiter = RuntimeFrameRateLimiter()

        assertTrue(limiter.shouldEmit(sensorTimestampNs = 0L, targetFps = 10))
        assertFalse(limiter.shouldEmit(sensorTimestampNs = 50_000_000L, targetFps = 10))

        limiter.reset()

        assertTrue(limiter.shouldEmit(sensorTimestampNs = 50_000_000L, targetFps = 10))
    }

    @Test
    fun nonPositiveTargetFpsDoesNotDropFrames() {
        val limiter = RuntimeFrameRateLimiter()

        assertTrue(limiter.shouldEmit(sensorTimestampNs = 0L, targetFps = 0))
        assertTrue(limiter.shouldEmit(sensorTimestampNs = 1L, targetFps = 0))
    }
}
