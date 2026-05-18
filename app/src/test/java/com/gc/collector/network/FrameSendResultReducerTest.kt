package com.gc.collector.network

import com.gc.collector.model.CaptureStats
import org.junit.Assert.assertEquals
import org.junit.Test

class FrameSendResultReducerTest {
    @Test
    fun sentIncrementsSentCountAndSetsStreamingStatus() {
        val state = FrameSendResultReducer.reduce(
            stats = sampleStats(),
            result = SendResult.Sent,
        )

        assertEquals(11L, state.stats.sentCount)
        assertEquals(2L, state.stats.failedCount)
        assertEquals(3L, state.stats.droppedFrames)
        assertEquals("gRPC streaming", state.networkStatus)
    }

    @Test
    fun failedIncrementsFailedCountAndUsesFailureMessage() {
        val state = FrameSendResultReducer.reduce(
            stats = sampleStats(),
            result = SendResult.Failed("stream closed"),
        )

        assertEquals(10L, state.stats.sentCount)
        assertEquals(3L, state.stats.failedCount)
        assertEquals(3L, state.stats.droppedFrames)
        assertEquals("stream closed", state.networkStatus)
    }

    @Test
    fun notStartedIncrementsDroppedFramesAndSetsNotStartedStatus() {
        val state = FrameSendResultReducer.reduce(
            stats = sampleStats(),
            result = SendResult.NotStarted,
        )

        assertEquals(10L, state.stats.sentCount)
        assertEquals(2L, state.stats.failedCount)
        assertEquals(4L, state.stats.droppedFrames)
        assertEquals("gRPC stream not started", state.networkStatus)
    }

    private fun sampleStats(): CaptureStats {
        return CaptureStats(
            frameSequence = 99L,
            lastDeviceTimestampMs = 100L,
            lastDeviceMonotonicNs = 200L,
            sentCount = 10L,
            failedCount = 2L,
            droppedFrames = 3L,
            currentFps = 12.5f,
        )
    }
}
