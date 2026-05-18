package com.gc.collector.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
}
