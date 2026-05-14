package com.gc.collector.model

import kotlinx.serialization.Serializable

@Serializable
data class CameraCaptureSettings(
    val cameraId: String = "camera_01",
    val deviceId: String = "android_device_001",
    val serverUrl: String = "localhost:50051",
    val resolution: ResolutionOption = ResolutionOption.HD,
    val fpsTarget: Int = 10,
    val focusMode: String = "unknown",
    val focusLocked: Boolean = false,
    val exposureLocked: Boolean = false,
    val whiteBalanceLocked: Boolean = false,
    val zoomDisabled: Boolean = true,
    val orientationDeg: Int = 90,
    val manualExposureEnabled: Boolean = false,
    val iso: Int = 400,
    val exposureTimeNs: Long = 10_000_000L,
)

@Serializable
data class ResolutionOption(
    val label: String,
    val width: Int,
    val height: Int,
) {
    companion object {
        val QVGA = ResolutionOption("320 x 240", 320, 240)
        val VGA = ResolutionOption("640 x 480", 640, 480)
        val NHD = ResolutionOption("640 x 360", 640, 360)
        val SVGA = ResolutionOption("800 x 600", 800, 600)
        val QHD_LITE = ResolutionOption("960 x 540", 960, 540)
        val XGA = ResolutionOption("1024 x 768", 1024, 768)
        val HD = ResolutionOption("1280 x 720", 1280, 720)
        val SXGA = ResolutionOption("1280 x 960", 1280, 960)
        val HD_PLUS = ResolutionOption("1600 x 900", 1600, 900)
        val FULL_HD = ResolutionOption("1920 x 1080", 1920, 1080)
        val QHD = ResolutionOption("2560 x 1440", 2560, 1440)
        val UHD = ResolutionOption("3840 x 2160", 3840, 2160)

        val commonOptions = listOf(
            QVGA,
            VGA,
            NHD,
            SVGA,
            QHD_LITE,
            XGA,
            HD,
            SXGA,
            HD_PLUS,
            FULL_HD,
            QHD,
            UHD,
        )

        fun fromDimensions(width: Int, height: Int): ResolutionOption {
            val normalizedWidth = maxOf(width, height)
            val normalizedHeight = minOf(width, height)
            return commonOptions.firstOrNull { option ->
                option.width == normalizedWidth && option.height == normalizedHeight
            } ?: ResolutionOption(
                label = "$normalizedWidth x $normalizedHeight",
                width = normalizedWidth,
                height = normalizedHeight,
            )
        }
    }
}
