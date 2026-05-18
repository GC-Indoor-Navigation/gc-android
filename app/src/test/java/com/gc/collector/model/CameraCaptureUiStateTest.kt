package com.gc.collector.model

import org.junit.Assert.assertFalse
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
}
