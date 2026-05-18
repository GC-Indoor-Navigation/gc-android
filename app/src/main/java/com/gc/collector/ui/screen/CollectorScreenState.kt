package com.gc.collector.ui.screen

import com.gc.collector.model.CameraCaptureUiState
import com.gc.collector.model.CollectorUiState

data class CollectorScreenState(
    val collectorUiState: CollectorUiState = CollectorUiState(),
    val cameraCaptureUiState: CameraCaptureUiState = CameraCaptureUiState(),
)
