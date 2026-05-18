package com.gc.collector.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraCaptureUiStateTest {
    @Test
    fun singleCaptureEnabledWhenPermissionGrantedNotStreamingAndUploadIdle() {
        val state = CameraCaptureUiState(
            calibrationCapture = CalibrationCaptureState(uploadInProgress = false),
        )

        assertTrue(
            state.singleCaptureEnabled(
                hasCameraPermission = true,
                isCapturing = false,
            ),
        )
    }

    @Test
    fun singleCaptureDisabledWithoutCameraPermission() {
        val state = CameraCaptureUiState()

        assertFalse(
            state.singleCaptureEnabled(
                hasCameraPermission = false,
                isCapturing = false,
            ),
        )
    }

    @Test
    fun singleCaptureDisabledWhileStreaming() {
        val state = CameraCaptureUiState()

        assertFalse(
            state.singleCaptureEnabled(
                hasCameraPermission = true,
                isCapturing = true,
            ),
        )
    }

    @Test
    fun singleCaptureDisabledWhileUploadInProgress() {
        val state = CameraCaptureUiState(
            calibrationCapture = CalibrationCaptureState(uploadInProgress = true),
        )

        assertFalse(
            state.singleCaptureEnabled(
                hasCameraPermission = true,
                isCapturing = false,
            ),
        )
    }

    @Test
    fun requestCalibrationCaptureUpdatesNestedCalibrationState() {
        val next = CameraCaptureUiState(
            calibrationCapture = CalibrationCaptureState(
                status = "Calibration idle",
                captureRequestId = 4L,
                uploadInProgress = false,
            ),
        ).requestCalibrationCapture()

        assertEquals("Calibration capture requested", next.calibrationStatus)
        assertEquals(5L, next.singleCaptureRequestId)
        assertTrue(next.singleCaptureInProgress)
    }

    @Test
    fun completeCalibrationUploadUpdatesNestedCalibrationState() {
        val next = CameraCaptureUiState(
            calibrationCapture = CalibrationCaptureState(
                status = "Calibration capture requested",
                captureRequestId = 4L,
                uploadInProgress = true,
            ),
        ).completeCalibrationUpload(
            frameSequence = 88L,
            outcome = CalibrationUploadOutcome.Uploaded,
        )

        assertEquals("Calibration uploaded: 88", next.calibrationStatus)
        assertEquals(4L, next.singleCaptureRequestId)
        assertFalse(next.singleCaptureInProgress)
    }

    @Test
    fun withCameraPermissionResultUpdatesCameraStatus() {
        assertEquals(
            "Camera permission granted",
            CameraCaptureUiState().withCameraPermissionResult(granted = true).cameraStatus,
        )
        assertEquals(
            "Camera permission denied",
            CameraCaptureUiState().withCameraPermissionResult(granted = false).cameraStatus,
        )
    }

    @Test
    fun withCameraReadyAndErrorUpdateCameraStatus() {
        assertEquals(
            "Back camera preview ready",
            CameraCaptureUiState().withCameraReady().cameraStatus,
        )
        assertEquals(
            "Camera unavailable",
            CameraCaptureUiState().withCameraError("Camera unavailable").cameraStatus,
        )
    }

    @Test
    fun withNetworkStatusUpdatesNetworkStatus() {
        val next = CameraCaptureUiState().withNetworkStatus("gRPC streaming")

        assertEquals("gRPC streaming", next.networkStatus)
    }

    @Test
    fun detailPanelHelpersCloseAndTogglePanel() {
        assertFalse(
            CameraCaptureUiState(detailsPanelOpen = true)
                .closeDetailsPanel()
                .detailsPanelOpen,
        )
        assertTrue(
            CameraCaptureUiState(detailsPanelOpen = false)
                .toggleDetailsPanel()
                .detailsPanelOpen,
        )
        assertFalse(
            CameraCaptureUiState(detailsPanelOpen = true)
                .toggleDetailsPanel()
                .detailsPanelOpen,
        )
    }
}
