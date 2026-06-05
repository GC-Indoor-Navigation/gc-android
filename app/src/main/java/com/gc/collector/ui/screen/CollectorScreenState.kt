package com.gc.collector.ui.screen

import com.gc.collector.model.CameraCaptureUiState
import com.gc.collector.model.CollectorUiState
import com.gc.collector.model.UserAlertState
import com.gc.collector.model.UserModeConnectionState

data class CollectorScreenState(
    val collectorUiState: CollectorUiState = CollectorUiState(),
    val cameraCaptureUiState: CameraCaptureUiState = CameraCaptureUiState(),
    val userModeConnectionState: UserModeConnectionState = UserModeConnectionState(),
    val userAlertState: UserAlertState = UserAlertState(),
)
