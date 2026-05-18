package com.gc.collector.ui.screen

import androidx.lifecycle.ViewModel
import com.gc.collector.model.CameraCaptureUiState
import com.gc.collector.model.CalibrationUploadOutcome
import com.gc.collector.model.CameraControlStatus
import com.gc.collector.model.CollectorUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class CollectorViewModel : ViewModel() {
    private val _screenState = MutableStateFlow(CollectorScreenState())
    val screenState: StateFlow<CollectorScreenState> = _screenState.asStateFlow()

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
}
