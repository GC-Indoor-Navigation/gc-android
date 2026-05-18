package com.gc.collector.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamSessionStateReducerTest {
    @Test
    fun startSucceededStartsCaptureAndResetsSessionStats() {
        val next = StreamSessionStateReducer.startSucceeded(
            state = CollectorUiState(
                isCapturing = false,
                sessionId = null,
                stats = sampleStats(),
            ),
            sessionId = "device_01_2026-05-18T10-00-00",
            deviceTimestampMs = 1_779_055_200_000L,
            deviceMonotonicNs = 12_345_678_900L,
            endpointHost = "192.168.0.10",
            endpointPort = 50051,
        )

        assertTrue(next.uiState.isCapturing)
        assertEquals("device_01_2026-05-18T10-00-00", next.uiState.sessionId)
        assertEquals(0L, next.uiState.stats.frameSequence)
        assertEquals(1_779_055_200_000L, next.uiState.stats.lastDeviceTimestampMs)
        assertEquals(12_345_678_900L, next.uiState.stats.lastDeviceMonotonicNs)
        assertEquals(0L, next.uiState.stats.sentCount)
        assertEquals(0L, next.uiState.stats.failedCount)
        assertEquals(0L, next.uiState.stats.droppedFrames)
        assertEquals(0f, next.uiState.stats.currentFps, 0.0001f)
        assertEquals("gRPC connected to 192.168.0.10:50051", next.networkStatus)
    }

    @Test
    fun startFailedStopsCaptureClearsSessionAndIncrementsFailedCount() {
        val next = StreamSessionStateReducer.startFailed(
            state = CollectorUiState(
                isCapturing = true,
                sessionId = "session_01",
                stats = sampleStats(),
            ),
            message = "Invalid gRPC endpoint",
        )

        assertFalse(next.uiState.isCapturing)
        assertNull(next.uiState.sessionId)
        assertEquals(99L, next.uiState.stats.frameSequence)
        assertEquals(100L, next.uiState.stats.lastDeviceTimestampMs)
        assertEquals(200L, next.uiState.stats.lastDeviceMonotonicNs)
        assertEquals(10L, next.uiState.stats.sentCount)
        assertEquals(3L, next.uiState.stats.failedCount)
        assertEquals(3L, next.uiState.stats.droppedFrames)
        assertEquals(12.5f, next.uiState.stats.currentFps, 0.0001f)
        assertEquals("Invalid gRPC endpoint", next.networkStatus)
    }

    @Test
    fun stoppedStopsCaptureAndPreservesStats() {
        val next = StreamSessionStateReducer.stopped(
            CollectorUiState(
                isCapturing = true,
                sessionId = "session_01",
                stats = sampleStats(),
            ),
        )

        assertFalse(next.uiState.isCapturing)
        assertNull(next.uiState.sessionId)
        assertEquals(sampleStats(), next.uiState.stats)
        assertEquals("gRPC stopped", next.networkStatus)
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
