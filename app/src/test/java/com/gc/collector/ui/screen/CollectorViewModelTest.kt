package com.gc.collector.ui.screen

import com.gc.collector.model.CameraCaptureUiState
import com.gc.collector.model.CollectorUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectorViewModelTest {
    @Test
    fun initialStateUsesDefaultCollectorAndCameraCaptureState() {
        val viewModel = CollectorViewModel()
        val state = viewModel.screenState.value

        assertFalse(state.collectorUiState.isCapturing)
        assertNull(state.collectorUiState.sessionId)
        assertEquals("Camera preview not started", state.cameraCaptureUiState.cameraStatus)
        assertEquals("gRPC disconnected", state.cameraCaptureUiState.networkStatus)
        assertFalse(state.cameraCaptureUiState.detailsPanelOpen)
        assertEquals("Calibration idle", state.cameraCaptureUiState.calibrationStatus)
    }

    @Test
    fun setCollectorUiStateReplacesCollectorStateOnly() {
        val viewModel = CollectorViewModel()

        viewModel.setCollectorUiState(CollectorUiState(isCapturing = true, sessionId = "session_01"))

        val state = viewModel.screenState.value
        assertTrue(state.collectorUiState.isCapturing)
        assertEquals("session_01", state.collectorUiState.sessionId)
        assertEquals("Camera preview not started", state.cameraCaptureUiState.cameraStatus)
    }

    @Test
    fun updateCollectorUiStateTransformsCollectorStateOnly() {
        val viewModel = CollectorViewModel()

        viewModel.updateCollectorUiState { state ->
            state.copy(isCapturing = true)
        }

        val state = viewModel.screenState.value
        assertTrue(state.collectorUiState.isCapturing)
        assertEquals("gRPC disconnected", state.cameraCaptureUiState.networkStatus)
    }

    @Test
    fun setCameraCaptureUiStateReplacesCameraCaptureStateOnly() {
        val viewModel = CollectorViewModel()

        viewModel.setCameraCaptureUiState(CameraCaptureUiState(networkStatus = "gRPC streaming"))

        val state = viewModel.screenState.value
        assertFalse(state.collectorUiState.isCapturing)
        assertEquals("gRPC streaming", state.cameraCaptureUiState.networkStatus)
    }

    @Test
    fun updateCameraCaptureUiStateTransformsCameraCaptureStateOnly() {
        val viewModel = CollectorViewModel()

        viewModel.updateCameraCaptureUiState { state ->
            state.withCameraReady()
        }

        val state = viewModel.screenState.value
        assertNull(state.collectorUiState.sessionId)
        assertEquals("Back camera preview ready", state.cameraCaptureUiState.cameraStatus)
    }
}
