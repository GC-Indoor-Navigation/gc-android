package com.gc.collector.model

data class CameraCaptureUiState(
    val cameraStatus: String = "Camera preview not started",
    val networkStatus: String = "gRPC disconnected",
    val detailsPanelOpen: Boolean = false,
    val calibrationCapture: CalibrationCaptureState = CalibrationCaptureState(),
) {
    fun singleCaptureEnabled(
        hasCameraPermission: Boolean,
        isCapturing: Boolean,
    ): Boolean {
        return hasCameraPermission && !isCapturing && !calibrationCapture.uploadInProgress
    }
}
