package com.gc.collector.network

import com.gc.collector.model.CameraCaptureUiState
import com.gc.collector.model.CollectorUiState

data class FrameSendUiState(
    val collectorUiState: CollectorUiState,
    val cameraCaptureUiState: CameraCaptureUiState,
)

object FrameSendUiStateReducer {
    fun reduce(
        collectorUiState: CollectorUiState,
        cameraCaptureUiState: CameraCaptureUiState,
        result: SendResult,
    ): FrameSendUiState {
        val sendState = FrameSendResultReducer.reduce(
            stats = collectorUiState.stats,
            result = result,
        )

        return FrameSendUiState(
            collectorUiState = collectorUiState.copy(stats = sendState.stats),
            cameraCaptureUiState = cameraCaptureUiState.withNetworkStatus(sendState.networkStatus),
        )
    }
}
