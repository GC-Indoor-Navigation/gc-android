package com.gc.collector.network

import com.gc.collector.model.CaptureStats

data class FrameSendState(
    val stats: CaptureStats,
    val networkStatus: String,
)

object FrameSendResultReducer {
    fun reduce(
        stats: CaptureStats,
        result: SendResult,
    ): FrameSendState {
        return when (result) {
            SendResult.Sent -> FrameSendState(
                stats = stats.copy(sentCount = stats.sentCount + 1L),
                networkStatus = "gRPC streaming",
            )

            is SendResult.Failed -> FrameSendState(
                stats = stats.copy(failedCount = stats.failedCount + 1L),
                networkStatus = result.message,
            )

            SendResult.NotStarted -> FrameSendState(
                stats = stats.copy(droppedFrames = stats.droppedFrames + 1L),
                networkStatus = "gRPC stream not started",
            )
        }
    }
}
