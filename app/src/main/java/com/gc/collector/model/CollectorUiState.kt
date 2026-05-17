package com.gc.collector.model

import kotlinx.serialization.Serializable

@Serializable
data class CollectorUiState(
    val isCapturing: Boolean = false,
    val sessionId: String? = null,
    val settings: CameraCaptureSettings = CameraCaptureSettings(),
    val stats: CaptureStats = CaptureStats(),
    val cameraControlStatus: CameraControlStatus = CameraControlStatus(),
)
