package com.gc.collector.model

data class CameraCaptureUiState(
    val cameraStatus: String = "Camera preview not started",
    val networkStatus: String = "gRPC disconnected",
    val detailsPanelOpen: Boolean = false,
    val calibrationCapture: CalibrationCaptureState = CalibrationCaptureState(),
) {
    val calibrationStatus: String
        get() = calibrationCapture.status

    val singleCaptureRequestId: Long
        get() = calibrationCapture.captureRequestId

    val singleCaptureInProgress: Boolean
        get() = calibrationCapture.uploadInProgress

    fun singleCaptureEnabled(
        hasCameraPermission: Boolean,
        isCapturing: Boolean,
    ): Boolean {
        return hasCameraPermission && !isCapturing && !calibrationCapture.uploadInProgress
    }

    fun requestCalibrationCapture(): CameraCaptureUiState {
        return copy(
            calibrationCapture = CalibrationCaptureStateReducer.requestCapture(calibrationCapture),
        )
    }

    fun completeCalibrationUpload(
        frameSequence: Long,
        outcome: CalibrationUploadOutcome,
    ): CameraCaptureUiState {
        return copy(
            calibrationCapture = CalibrationCaptureStateReducer.completeUpload(
                state = calibrationCapture,
                frameSequence = frameSequence,
                outcome = outcome,
            ),
        )
    }
}
