package com.gc.collector.ui.camera

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Range
import com.gc.collector.model.ResolutionOption

fun loadBackCameraResolutionOptions(context: Context): List<ResolutionOption> {
    val cameraManager = context.getSystemService(CameraManager::class.java)
    val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
        cameraManager.getCameraCharacteristics(id)
            .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
    } ?: cameraManager.cameraIdList.firstOrNull()
    val characteristics = cameraId?.let(cameraManager::getCameraCharacteristics) ?: return emptyList()
    val streamMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

    return streamMap
        ?.getOutputSizes(ImageFormat.YUV_420_888)
        .orEmpty()
        .map { size -> ResolutionOption.fromDimensions(size.width, size.height) }
        .distinctBy { option -> option.width to option.height }
        .sortedWith(
            compareBy<ResolutionOption> { option -> option.width * option.height }
                .thenBy { option -> option.width }
                .thenBy { option -> option.height },
        )
}

fun chooseBackCameraFpsRange(
    context: Context,
    targetFps: Int,
): Range<Int> {
    val fpsRanges = loadBackCameraFpsRanges(context)
    return fpsRanges.firstOrNull { range -> range.lower == targetFps && range.upper == targetFps }
        ?: fpsRanges.firstOrNull { range -> range.upper == targetFps }
        ?: fpsRanges
            .filter { range -> targetFps >= range.lower && targetFps <= range.upper }
            .minWithOrNull(
                compareBy<Range<Int>> { range -> range.upper - range.lower }
                    .thenBy { range -> kotlin.math.abs(range.upper - targetFps) },
            )
        ?: Range(targetFps, targetFps)
}

private fun loadBackCameraFpsRanges(context: Context): List<Range<Int>> {
    val cameraManager = context.getSystemService(CameraManager::class.java)
    val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
        cameraManager.getCameraCharacteristics(id)
            .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
    } ?: cameraManager.cameraIdList.firstOrNull()
    val characteristics = cameraId?.let(cameraManager::getCameraCharacteristics) ?: return emptyList()

    return characteristics
        .get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
        .orEmpty()
        .distinctBy { range -> range.lower to range.upper }
        .sortedWith(
            compareBy<Range<Int>> { range -> range.lower }
                .thenBy { range -> range.upper },
        )
}
