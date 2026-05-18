package com.gc.collector.ui.screen

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import com.gc.collector.model.CalibrationCaptureState
import com.gc.collector.model.CameraCaptureUiState

fun cameraCaptureUiStateSaver(): Saver<CameraCaptureUiState, Any> {
    return listSaver(
        save = { state ->
            listOf(
                state.cameraStatus,
                state.networkStatus,
                state.detailsPanelOpen,
                state.calibrationCapture.status,
                state.calibrationCapture.captureRequestId,
                state.calibrationCapture.uploadInProgress,
            )
        },
        restore = { values ->
            CameraCaptureUiState(
                cameraStatus = values[0] as String,
                networkStatus = values[1] as String,
                detailsPanelOpen = values[2] as Boolean,
                calibrationCapture = CalibrationCaptureState(
                    status = values[3] as String,
                    captureRequestId = values[4] as Long,
                    uploadInProgress = values[5] as Boolean,
                ),
            )
        },
    )
}
