package com.gc.collector.model

import org.junit.Assert.assertEquals
import org.junit.Test

class FpsCalculatorTest {
    @Test
    fun firstFrameStartsWindowAndKeepsPreviousFps() {
        val result = FpsCalculator.calculate(
            sensorTimestampNs = 123_000L,
            lastWindowStartedNs = null,
            framesInWindow = 0,
            previousFps = 12.5f,
        )

        assertEquals(12.5f, result.currentFps, 0.0001f)
        assertEquals(123_000L, result.windowStartedNs)
        assertEquals(1, result.framesInWindow)
    }

    @Test
    fun frameBeforeOneSecondKeepsPreviousFpsAndIncrementsFrameCount() {
        val result = FpsCalculator.calculate(
            sensorTimestampNs = 600_000_000L,
            lastWindowStartedNs = 100_000_000L,
            framesInWindow = 3,
            previousFps = 7.5f,
        )

        assertEquals(7.5f, result.currentFps, 0.0001f)
        assertEquals(100_000_000L, result.windowStartedNs)
        assertEquals(4, result.framesInWindow)
    }

    @Test
    fun frameAtOneSecondCalculatesFpsAndResetsWindow() {
        val result = FpsCalculator.calculate(
            sensorTimestampNs = 1_000_000_000L,
            lastWindowStartedNs = 0L,
            framesInWindow = 9,
            previousFps = 0f,
        )

        assertEquals(10f, result.currentFps, 0.0001f)
        assertEquals(1_000_000_000L, result.windowStartedNs)
        assertEquals(0, result.framesInWindow)
    }

    @Test
    fun frameAfterOneSecondUsesElapsedTimeForFps() {
        val result = FpsCalculator.calculate(
            sensorTimestampNs = 2_000_000_000L,
            lastWindowStartedNs = 0L,
            framesInWindow = 3,
            previousFps = 0f,
        )

        assertEquals(2f, result.currentFps, 0.0001f)
        assertEquals(2_000_000_000L, result.windowStartedNs)
        assertEquals(0, result.framesInWindow)
    }
}
