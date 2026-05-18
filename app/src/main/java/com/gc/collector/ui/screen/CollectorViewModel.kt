package com.gc.collector.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gc.collector.camera.CapturedFrame
import com.gc.collector.model.CameraCaptureUiState
import com.gc.collector.model.CalibrationCaptureStateReducer
import com.gc.collector.model.CalibrationUploadOutcome
import com.gc.collector.model.CameraControlStatus
import com.gc.collector.model.CollectorUiState
import com.gc.collector.model.RuntimeFrameCaptureStateReducer
import com.gc.collector.network.FrameSendUiStateReducer
import com.gc.collector.network.InternalCalibrationUploadOutcomeMapper
import com.gc.collector.network.InternalCalibrationUploadResult
import com.gc.collector.network.SendResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CollectorViewModel : ViewModel() {
    private val _screenState = MutableStateFlow(CollectorScreenState())
    val screenState: StateFlow<CollectorScreenState> = _screenState.asStateFlow()
    private var runtimeFpsWindowStartedNs: Long? = null
    private var runtimeFramesInWindow: Int = 0

    fun setCollectorUiState(state: CollectorUiState) {
        _screenState.update { current ->
            current.copy(collectorUiState = state)
        }
    }

    fun updateCollectorUiState(transform: (CollectorUiState) -> CollectorUiState) {
        _screenState.update { current ->
            current.copy(collectorUiState = transform(current.collectorUiState))
        }
    }

    fun setCameraCaptureUiState(state: CameraCaptureUiState) {
        _screenState.update { current ->
            current.copy(cameraCaptureUiState = state)
        }
    }

    fun updateCameraCaptureUiState(transform: (CameraCaptureUiState) -> CameraCaptureUiState) {
        _screenState.update { current ->
            current.copy(cameraCaptureUiState = transform(current.cameraCaptureUiState))
        }
    }

    fun onCameraPermissionResult(granted: Boolean) {
        updateCameraCaptureUiState { state ->
            state.withCameraPermissionResult(granted)
        }
    }

    fun onCameraReady() {
        updateCameraCaptureUiState { state ->
            state.withCameraReady()
        }
    }

    fun onCameraError(message: String) {
        updateCameraCaptureUiState { state ->
            state.withCameraError(message)
        }
    }

    fun onCameraControlStatus(status: CameraControlStatus) {
        updateCollectorUiState { state ->
            state.copy(cameraControlStatus = status)
        }
    }

    fun onNetworkStatusChanged(message: String) {
        updateCameraCaptureUiState { state ->
            state.withNetworkStatus(message)
        }
    }

    fun onCloseDetailsPanel() {
        updateCameraCaptureUiState { state ->
            state.closeDetailsPanel()
        }
    }

    fun onToggleDetailsPanel() {
        updateCameraCaptureUiState { state ->
            state.toggleDetailsPanel()
        }
    }

    fun onSingleCaptureRequested() {
        updateCameraCaptureUiState { state ->
            state.requestCalibrationCapture()
        }
    }

    fun onCalibrationUploadCompleted(
        frameSequence: Long,
        outcome: CalibrationUploadOutcome,
    ) {
        updateCameraCaptureUiState { state ->
            state.completeCalibrationUpload(
                frameSequence = frameSequence,
                outcome = outcome,
            )
        }
    }

    fun onCalibrationFrameCaptured(
        frame: CapturedFrame,
        uploadFrame: (CapturedFrame) -> InternalCalibrationUploadResult,
    ) {
        updateCollectorUiState { state ->
            state.copy(
                stats = CalibrationCaptureStateReducer.applyCapturedFrame(
                    stats = state.stats,
                    metadata = frame.metadata,
                ),
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            val uploadResult = uploadFrame(frame)
            onCalibrationUploadCompleted(
                frameSequence = frame.metadata.frameSequence,
                outcome = InternalCalibrationUploadOutcomeMapper.toOutcome(uploadResult),
            )
        }
    }

    fun resetRuntimeFpsWindow() {
        runtimeFpsWindowStartedNs = null
        runtimeFramesInWindow = 0
    }

    fun onRuntimeFrameCaptured(
        frame: CapturedFrame,
        sendFrame: (CapturedFrame) -> SendResult,
    ) {
        val current = screenState.value
        if (!current.collectorUiState.isCapturing) return

        val nextFrameState = RuntimeFrameCaptureStateReducer.applyCapturedFrame(
            stats = current.collectorUiState.stats,
            metadata = frame.metadata,
            sensorTimestampNs = frame.sensorTimestampNs,
            lastWindowStartedNs = runtimeFpsWindowStartedNs,
            framesInWindow = runtimeFramesInWindow,
        )
        runtimeFpsWindowStartedNs = nextFrameState.fpsWindowStartedNs
        runtimeFramesInWindow = nextFrameState.framesInWindow
        setCollectorUiState(current.collectorUiState.copy(stats = nextFrameState.stats))

        viewModelScope.launch(Dispatchers.IO) {
            onRuntimeFrameSendResult(sendFrame(frame))
        }
    }

    fun onRuntimeFrameSendResult(result: SendResult) {
        _screenState.update { current ->
            val nextState = FrameSendUiStateReducer.reduce(
                collectorUiState = current.collectorUiState,
                cameraCaptureUiState = current.cameraCaptureUiState,
                result = result,
            )
            current.copy(
                collectorUiState = nextState.collectorUiState,
                cameraCaptureUiState = nextState.cameraCaptureUiState,
            )
        }
    }
}
