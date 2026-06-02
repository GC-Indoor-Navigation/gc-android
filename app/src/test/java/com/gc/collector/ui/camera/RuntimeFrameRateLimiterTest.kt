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
        assertFalse(limiter.shouldEmit(sensorTimestampNs = 90_000_000L, targetFps = 10))
    }

    @Test
    fun frameSlightlyBeforeTargetIntervalIsEmittedWithTolerance() {
        val limiter = RuntimeFrameRateLimiter()

        assertTrue(limiter.shouldEmit(sensorTimestampNs = 0L, targetFps = 10))
        assertTrue(limiter.shouldEmit(sensorTimestampNs = 99_999_999L, targetFps = 10))
    }

    @Test
    fun frameAtTargetIntervalIsEmitted() {
        val limiter = RuntimeFrameRateLimiter()

        assertTrue(limiter.shouldEmit(sensorTimestampNs = 0L, targetFps = 10))
        assertTrue(limiter.shouldEmit(sensorTimestampNs = 100_000_000L, targetFps = 10))
    }

    @Test
    fun approximateThirtyFpsInputCanEmitTenFpsSchedule() {
        val limiter = RuntimeFrameRateLimiter()

        assertTrue(limiter.shouldEmit(sensorTimestampNs = 0L, targetFps = 10))
        assertFalse(limiter.shouldEmit(sensorTimestampNs = 33_333_333L, targetFps = 10))
        assertFalse(limiter.shouldEmit(sensorTimestampNs = 66_666_666L, targetFps = 10))
        assertTrue(limiter.shouldEmit(sensorTimestampNs = 99_999_999L, targetFps = 10))
        assertFalse(limiter.shouldEmit(sensorTimestampNs = 133_333_332L, targetFps = 10))
        assertFalse(limiter.shouldEmit(sensorTimestampNs = 166_666_665L, targetFps = 10))
        assertTrue(limiter.shouldEmit(sensorTimestampNs = 199_999_998L, targetFps = 10))
    }

    @Test
    fun delayedFrameAdvancesScheduleWithoutPermanentDrift() {
        val limiter = RuntimeFrameRateLimiter()

        assertTrue(limiter.shouldEmit(sensorTimestampNs = 0L, targetFps = 10))
        assertTrue(limiter.shouldEmit(sensorTimestampNs = 450_000_000L, targetFps = 10))

        assertFalse(limiter.shouldEmit(sensorTimestampNs = 475_000_000L, targetFps = 10))
        assertTrue(limiter.shouldEmit(sensorTimestampNs = 500_000_000L, targetFps = 10))
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
