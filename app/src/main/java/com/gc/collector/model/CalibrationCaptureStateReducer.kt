package com.gc.collector.model

data class CalibrationCaptureState(
    val status: String = "Calibration idle",
    val captureRequestId: Long = 0L,
    val uploadInProgress: Boolean = false,
)

sealed interface CalibrationUploadOutcome {
    data object Uploaded : CalibrationUploadOutcome
    data class Failed(val message: String) : CalibrationUploadOutcome
}

object CalibrationCaptureStateReducer {
    fun requestCapture(state: CalibrationCaptureState): CalibrationCaptureState {
        return state.copy(
            status = "Calibration capture requested",
            captureRequestId = state.captureRequestId + 1L,
            uploadInProgress = true,
        )
    }

    fun applyCapturedFrame(
        stats: CaptureStats,
        metadata: FrameMetadata,
    ): CaptureStats {
        return stats.copy(
            frameSequence = metadata.frameSequence,
            lastDeviceTimestampMs = metadata.deviceTimestampMs,
            lastDeviceMonotonicNs = metadata.deviceMonotonicNs,
        )
    }

    fun completeUpload(
        state: CalibrationCaptureState,
        frameSequence: Long,
        outcome: CalibrationUploadOutcome,
    ): CalibrationCaptureState {
        return when (outcome) {
            CalibrationUploadOutcome.Uploaded -> state.copy(
                status = "Calibration uploaded: $frameSequence",
                uploadInProgress = false,
            )

            is CalibrationUploadOutcome.Failed -> state.copy(
                status = "Calibration failed: ${outcome.message}",
                uploadInProgress = false,
            )
        }
    }
}
