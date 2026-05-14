package com.gc.collector.ui.camera

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
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
