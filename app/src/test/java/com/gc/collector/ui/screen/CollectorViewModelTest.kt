package com.gc.collector.ui.screen

import com.gc.collector.camera.CapturedFrame
import com.gc.collector.model.CameraCaptureUiState
import com.gc.collector.model.CameraControlStatus
import com.gc.collector.model.CalibrationUploadOutcome
import com.gc.collector.model.CollectorUiState
import com.gc.collector.model.FrameMetadata
import com.gc.collector.network.SendResult
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

    @Test
    fun cameraStatusEventsUpdateCameraCaptureState() {
        val viewModel = CollectorViewModel()

        viewModel.onCameraPermissionResult(granted = true)
        assertEquals("Camera permission granted", viewModel.screenState.value.cameraCaptureUiState.cameraStatus)

        viewModel.onCameraReady()
        assertEquals("Back camera preview ready", viewModel.screenState.value.cameraCaptureUiState.cameraStatus)

        viewModel.onCameraError("Camera unavailable")
        assertEquals("Camera unavailable", viewModel.screenState.value.cameraCaptureUiState.cameraStatus)
    }

    @Test
    fun cameraControlStatusEventUpdatesCollectorState() {
        val viewModel = CollectorViewModel()
        val status = CameraControlStatus(
            focusLockSupported = true,
            focusLockApplied = true,
            exposureLockSupported = false,
            whiteBalanceLockSupported = true,
            whiteBalanceLockApplied = true,
        )

        viewModel.onCameraControlStatus(status)

        assertEquals(status, viewModel.screenState.value.collectorUiState.cameraControlStatus)
    }

    @Test
    fun networkStatusEventUpdatesCameraCaptureState() {
        val viewModel = CollectorViewModel()

        viewModel.onNetworkStatusChanged("gRPC streaming")

        assertEquals("gRPC streaming", viewModel.screenState.value.cameraCaptureUiState.networkStatus)
    }

    @Test
    fun detailPanelEventsUpdateCameraCaptureState() {
        val viewModel = CollectorViewModel()

        viewModel.onToggleDetailsPanel()
        assertTrue(viewModel.screenState.value.cameraCaptureUiState.detailsPanelOpen)

        viewModel.onCloseDetailsPanel()
        assertFalse(viewModel.screenState.value.cameraCaptureUiState.detailsPanelOpen)
    }

    @Test
    fun calibrationEventsUpdateCameraCaptureState() {
        val viewModel = CollectorViewModel()

        viewModel.onSingleCaptureRequested()
        assertEquals("Calibration capture requested", viewModel.screenState.value.cameraCaptureUiState.calibrationStatus)
        assertTrue(viewModel.screenState.value.cameraCaptureUiState.singleCaptureInProgress)

        viewModel.onCalibrationUploadCompleted(
            frameSequence = 17L,
            outcome = CalibrationUploadOutcome.Uploaded,
        )
        assertEquals("Calibration uploaded: 17", viewModel.screenState.value.cameraCaptureUiState.calibrationStatus)
        assertFalse(viewModel.screenState.value.cameraCaptureUiState.singleCaptureInProgress)
    }

    @Test
    fun runtimeFrameCapturedDoesNothingWhenNotCapturing() {
        val viewModel = CollectorViewModel()
        var sendCalled = false

        viewModel.onRuntimeFrameCaptured(sampleFrame(frameSequence = 9L)) {
            sendCalled = true
            SendResult.Sent
        }

        val state = viewModel.screenState.value
        assertEquals(0L, state.collectorUiState.stats.frameSequence)
        assertFalse(sendCalled)
    }

    @Test
    fun runtimeFrameCapturedUpdatesStatsWhenCapturing() {
        val viewModel = CollectorViewModel()
        viewModel.setCollectorUiState(CollectorUiState(isCapturing = true))

        viewModel.onRuntimeFrameCaptured(
            frame = sampleFrame(frameSequence = 9L, sensorTimestampNs = 123_000L),
            sendFrame = { SendResult.NotStarted },
        )

        val stats = viewModel.screenState.value.collectorUiState.stats
        assertEquals(9L, stats.frameSequence)
        assertEquals(1_775_404_088_703L, stats.lastDeviceTimestampMs)
        assertEquals(8_234_567_812_345L, stats.lastDeviceMonotonicNs)
    }

    @Test
    fun runtimeFrameSendResultUpdatesCollectorAndCameraCaptureState() {
        val viewModel = CollectorViewModel()

        viewModel.onRuntimeFrameSendResult(SendResult.Sent)

        val state = viewModel.screenState.value
        assertEquals(1L, state.collectorUiState.stats.sentCount)
        assertEquals("gRPC streaming", state.cameraCaptureUiState.networkStatus)
    }

    private fun sampleFrame(
        frameSequence: Long,
        sensorTimestampNs: Long = 1_000L,
    ): CapturedFrame {
        return CapturedFrame(
            jpegBytes = byteArrayOf(1, 2, 3),
            metadata = FrameMetadata(
                cameraId = "camera_01",
                deviceId = "device_01",
                frameSequence = frameSequence,
                sessionId = "session_01",
                deviceTimestampMs = 1_775_404_088_703L,
                deviceMonotonicNs = 8_234_567_812_345L,
                width = 1280,
                height = 720,
                fpsTarget = 10,
                focusMode = "locked",
                focusLocked = true,
                exposureLocked = true,
                whiteBalanceLocked = true,
                zoomDisabled = true,
                orientationDeg = 90,
                focusLockRequested = true,
                focusLockSupport = "supported",
                focusLockApplied = "applied",
                exposureLockRequested = true,
                exposureLockSupport = "supported",
                exposureLockApplied = "applied",
                whiteBalanceLockRequested = true,
                whiteBalanceLockSupport = "supported",
                whiteBalanceLockApplied = "applied",
                fpsTargetSupport = "supported",
                resolutionSupport = "supported",
                manualExposureSupport = "supported",
                manualExposureRequested = false,
                manualExposureApplied = "not_applied",
                isoRequested = 400,
                isoApplied = null,
                exposureTimeNsRequested = 10_000_000L,
                exposureTimeNsApplied = null,
                focalLengthMm = null,
            ),
            sensorTimestampNs = sensorTimestampNs,
        )
    }
}
