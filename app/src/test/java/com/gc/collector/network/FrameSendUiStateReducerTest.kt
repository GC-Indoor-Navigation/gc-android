package com.gc.collector.network

import com.gc.collector.model.CameraCaptureUiState
import com.gc.collector.model.CaptureStats
import com.gc.collector.model.CollectorUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class FrameSendUiStateReducerTest {
    @Test
    fun sentUpdatesCollectorStatsAndCameraNetworkStatus() {
        val next = FrameSendUiStateReducer.reduce(
            collectorUiState = sampleCollectorUiState(),
            cameraCaptureUiState = CameraCaptureUiState(networkStatus = "gRPC connected"),
            result = SendResult.Sent,
        )

        assertEquals(11L, next.collectorUiState.stats.sentCount)
        assertEquals(2L, next.collectorUiState.stats.failedCount)
        assertEquals(3L, next.collectorUiState.stats.droppedFrames)
        assertEquals("gRPC streaming", next.cameraCaptureUiState.networkStatus)
    }

    @Test
    fun failedUpdatesCollectorStatsAndCameraNetworkStatus() {
        val next = FrameSendUiStateReducer.reduce(
            collectorUiState = sampleCollectorUiState(),
            cameraCaptureUiState = CameraCaptureUiState(networkStatus = "gRPC connected"),
            result = SendResult.Failed("stream closed"),
        )

        assertEquals(10L, next.collectorUiState.stats.sentCount)
        assertEquals(3L, next.collectorUiState.stats.failedCount)
        assertEquals(3L, next.collectorUiState.stats.droppedFrames)
        assertEquals("stream closed", next.cameraCaptureUiState.networkStatus)
    }

    @Test
    fun notStartedUpdatesCollectorStatsAndCameraNetworkStatus() {
        val next = FrameSendUiStateReducer.reduce(
            collectorUiState = sampleCollectorUiState(),
            cameraCaptureUiState = CameraCaptureUiState(networkStatus = "gRPC connected"),
            result = SendResult.NotStarted,
        )

        assertEquals(10L, next.collectorUiState.stats.sentCount)
        assertEquals(2L, next.collectorUiState.stats.failedCount)
        assertEquals(4L, next.collectorUiState.stats.droppedFrames)
        assertEquals("gRPC stream not started", next.cameraCaptureUiState.networkStatus)
    }

    private fun sampleCollectorUiState(): CollectorUiState {
        return CollectorUiState(
            stats = CaptureStats(
                frameSequence = 99L,
                lastDeviceTimestampMs = 100L,
                lastDeviceMonotonicNs = 200L,
                sentCount = 10L,
                failedCount = 2L,
                droppedFrames = 3L,
                currentFps = 12.5f,
            ),
        )
    }
}
