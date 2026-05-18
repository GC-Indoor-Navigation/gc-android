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

    fun withCameraPermissionResult(granted: Boolean): CameraCaptureUiState {
        return copy(
            cameraStatus = if (granted) {
                "Camera permission granted"
            } else {
                "Camera permission denied"
            },
        )
    }

    fun withCameraReady(): CameraCaptureUiState {
        return copy(cameraStatus = "Back camera preview ready")
    }

    fun withCameraError(message: String): CameraCaptureUiState {
        return copy(cameraStatus = message)
    }

    fun withNetworkStatus(message: String): CameraCaptureUiState {
        return copy(networkStatus = message)
    }

    fun closeDetailsPanel(): CameraCaptureUiState {
        return copy(detailsPanelOpen = false)
    }

    fun toggleDetailsPanel(): CameraCaptureUiState {
        return copy(detailsPanelOpen = !detailsPanelOpen)
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
