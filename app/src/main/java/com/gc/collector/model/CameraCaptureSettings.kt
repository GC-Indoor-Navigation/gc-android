package com.gc.collector.model

import kotlinx.serialization.Serializable

@Serializable
data class CameraCaptureSettings(
    val cameraId: String = "camera_01",
    val deviceId: String = "android_device_001",
    val serverUrl: String = "http://localhost:8080/frames",
    val resolution: ResolutionOption = ResolutionOption.HD,
    val fpsTarget: Int = 10,
    val focusMode: String = "unknown",
    val focusLocked: Boolean = false,
    val exposureLocked: Boolean = false,
    val whiteBalanceLocked: Boolean = false,
    val zoomDisabled: Boolean = true,
    val orientationDeg: Int = 90,
)

@Serializable
enum class ResolutionOption(
    val label: String,
    val width: Int,
    val height: Int,
) {
    HD("1280 x 720", 1280, 720),
    FULL_HD("1920 x 1080", 1920, 1080),
    VGA("640 x 480", 640, 480),
}
