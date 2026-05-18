package com.gc.collector.model

data class StreamSessionState(
    val uiState: CollectorUiState,
    val networkStatus: String,
)

object StreamSessionStateReducer {
    fun startSucceeded(
        state: CollectorUiState,
        sessionId: String,
        deviceTimestampMs: Long,
        deviceMonotonicNs: Long,
        endpointHost: String,
        endpointPort: Int,
    ): StreamSessionState {
        return StreamSessionState(
            uiState = state.copy(
                isCapturing = true,
                sessionId = sessionId,
                stats = state.stats.copy(
                    frameSequence = 0L,
                    lastDeviceTimestampMs = deviceTimestampMs,
                    lastDeviceMonotonicNs = deviceMonotonicNs,
                    sentCount = 0L,
                    failedCount = 0L,
                    droppedFrames = 0L,
                    currentFps = 0f,
                ),
            ),
            networkStatus = "gRPC connected to $endpointHost:$endpointPort",
        )
    }

    fun startFailed(
        state: CollectorUiState,
        message: String,
    ): StreamSessionState {
        return StreamSessionState(
            uiState = state.copy(
                isCapturing = false,
                sessionId = null,
                stats = state.stats.copy(failedCount = state.stats.failedCount + 1L),
            ),
            networkStatus = message,
        )
    }

    fun stopped(state: CollectorUiState): StreamSessionState {
        return StreamSessionState(
            uiState = state.copy(
                isCapturing = false,
                sessionId = null,
            ),
            networkStatus = "gRPC stopped",
        )
    }
}
