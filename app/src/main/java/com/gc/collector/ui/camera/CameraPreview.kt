package com.gc.collector.ui.camera

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.TotalCaptureResult
import android.util.Range
import android.util.Size
import android.view.Surface
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.gc.collector.camera.CapturedFrame
import com.gc.collector.camera.JpegFrameEncoder
import com.gc.collector.model.CameraControlStatus
import com.gc.collector.model.CameraCaptureSettings
import com.gc.collector.model.FrameMetadataFactory
import com.gc.collector.model.ResolutionOption
import java.util.concurrent.Executor

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    isAnalyzing: Boolean,
    settings: CameraCaptureSettings,
    nextFrameSequence: () -> Long,
    controlStatus: CameraControlStatus,
    onFrameCaptured: (CapturedFrame) -> Unit,
    onCameraControlStatus: (CameraControlStatus) -> Unit,
    onCameraReady: () -> Unit,
    onCameraError: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val currentControlStatus = rememberUpdatedState(controlStatus)

    LaunchedEffect(
        context,
        lifecycleOwner,
        previewView,
        isAnalyzing,
        settings,
        nextFrameSequence,
        onFrameCaptured,
        onCameraControlStatus,
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
            {
                runCatching {
                    val cameraProvider = cameraProviderFuture.get()
                    var lastControlStatus: CameraControlStatus? = null
                    val captureCallback = object : CameraCaptureSession.CaptureCallback() {
                        override fun onCaptureCompleted(
                            session: CameraCaptureSession,
                            request: CaptureRequest,
                            result: TotalCaptureResult,
                        ) {
                            val previousStatus = lastControlStatus ?: return
                            val updatedStatus = previousStatus.withCaptureResult(
                                settings = settings,
                                result = result,
                            )
                            if (updatedStatus != previousStatus) {
                                lastControlStatus = updatedStatus
                                onCameraControlStatus(updatedStatus)
                            }
                        }
                    }
                    val previewBuilder = Preview.Builder()
                    previewBuilder.applyCaptureSettings(
                        settings = settings,
                        captureCallback = captureCallback,
                    )
                    val preview = previewBuilder.build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val analysisBuilder = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    analysisBuilder.applyCaptureSettings(settings)
                    val imageAnalysis = analysisBuilder.build()
                        .also { analysis ->
                            analysis.setAnalyzer(mainExecutor) { imageProxy ->
                                try {
                                    if (isAnalyzing) {
                                        val metadata = FrameMetadataFactory.create(
                                            settings = settings,
                                            controlStatus = currentControlStatus.value,
                                            frameSequence = nextFrameSequence(),
                                            width = imageProxy.width,
                                            height = imageProxy.height,
                                        )
                                        val jpegBytes = JpegFrameEncoder.encode(imageProxy)
                                        onFrameCaptured(
                                            CapturedFrame(
                                                jpegBytes = jpegBytes,
                                                metadata = metadata,
                                                sensorTimestampNs = imageProxy.imageInfo.timestamp,
                                            ),
                                        )
                                    }
                                } finally {
                                    imageProxy.close()
                                }
                            }
                        }

                    cameraProvider.unbindAll()
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis,
                    )
                    if (settings.zoomDisabled) {
                        camera.cameraControl.setZoomRatio(1.0f)
                    }
                    val cameraControlStatus = camera.cameraInfo.readControlStatus(settings)
                    lastControlStatus = cameraControlStatus
                    onCameraControlStatus(cameraControlStatus)
                    onCameraReady()
                }.onFailure { error ->
                    onCameraError(error.message ?: "Failed to bind camera preview")
                }
            },
            mainExecutor,
        )
    }

    DisposableEffect(context) {
        onDispose {
            unbindCamera(context, mainExecutor)
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private fun Preview.Builder.applyCaptureSettings(
    settings: CameraCaptureSettings,
    captureCallback: CameraCaptureSession.CaptureCallback,
) {
    setTargetResolution(settings.resolution.toSize())
    setTargetRotation(settings.orientationDeg.toSurfaceRotation())
    Camera2Interop.Extender(this).apply {
        applyCommonCaptureSettings(settings)
        setSessionCaptureCallback(captureCallback)
    }
}

private fun ImageAnalysis.Builder.applyCaptureSettings(settings: CameraCaptureSettings) {
    setTargetResolution(settings.resolution.toSize())
    setTargetRotation(settings.orientationDeg.toSurfaceRotation())
    Camera2Interop.Extender(this).apply {
        applyCommonCaptureSettings(settings)
    }
}

private fun Camera2Interop.Extender<*>.applyCommonCaptureSettings(settings: CameraCaptureSettings) {
    if (settings.focusLocked) {
        setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
        setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, 0f)
    } else {
        setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
    }
    setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, settings.exposureLocked)
    setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(settings.fpsTarget, settings.fpsTarget))
    setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, settings.whiteBalanceLocked)
}

private fun androidx.camera.core.CameraInfo.readControlStatus(settings: CameraCaptureSettings): CameraControlStatus {
    val camera2Info = Camera2CameraInfo.from(this)
    val aeLockAvailable = camera2Info.getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE)
    val awbLockAvailable = camera2Info.getCameraCharacteristic(CameraCharacteristics.CONTROL_AWB_LOCK_AVAILABLE)
    val afModes = camera2Info.getCameraCharacteristic(
        CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES,
    ) ?: intArrayOf()
    val capabilities = camera2Info.getCameraCharacteristic(
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES,
    ) ?: intArrayOf()
    val fpsRanges = camera2Info.getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES).orEmpty()
    val streamMap = camera2Info.getCameraCharacteristic(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
    val maxDigitalZoom = camera2Info.getCameraCharacteristic(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)

    return CameraControlStatus(
        focusLockSupported = afModes.contains(CaptureRequest.CONTROL_AF_MODE_OFF),
        focusLockApplied = null,
        exposureLockSupported = aeLockAvailable == true,
        exposureLockApplied = null,
        whiteBalanceLockSupported = awbLockAvailable == true,
        whiteBalanceLockApplied = null,
        fpsTargetSupported = fpsRanges.any { range ->
            settings.fpsTarget >= range.lower && settings.fpsTarget <= range.upper
        },
        resolutionSupported = streamMap
            ?.getOutputSizes(ImageFormat.YUV_420_888)
            .orEmpty()
            .any { size -> size.matches(settings.resolution) },
        manualExposureSupported = capabilities.contains(
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR,
        ),
        zoomSupported = maxDigitalZoom != null && maxDigitalZoom >= 1.0f,
    )
}

private fun CameraControlStatus.withCaptureResult(
    settings: CameraCaptureSettings,
    result: TotalCaptureResult,
): CameraControlStatus {
    val request = result.request
    val afMode = request.get(CaptureRequest.CONTROL_AF_MODE)
    val aeLock = request.get(CaptureRequest.CONTROL_AE_LOCK)
    val awbLock = request.get(CaptureRequest.CONTROL_AWB_LOCK)
    val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
    val awbState = result.get(CaptureResult.CONTROL_AWB_STATE)

    return copy(
        focusLockApplied = if (settings.focusLocked) {
            afMode == CaptureRequest.CONTROL_AF_MODE_OFF
        } else {
            afMode == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
        },
        exposureLockApplied = if (settings.exposureLocked) {
            aeLock == true && (aeState == CaptureResult.CONTROL_AE_STATE_LOCKED || aeState == null)
        } else {
            aeLock == false || aeLock == null
        },
        whiteBalanceLockApplied = if (settings.whiteBalanceLocked) {
            awbLock == true && (awbState == CaptureResult.CONTROL_AWB_STATE_LOCKED || awbState == null)
        } else {
            awbLock == false || awbLock == null
        },
    )
}

private fun Size.matches(option: ResolutionOption): Boolean {
    return (width == option.width && height == option.height) ||
        (width == option.height && height == option.width)
}

private fun ResolutionOption.toSize(): Size {
    return Size(width, height)
}

private fun Int.toSurfaceRotation(): Int {
    return when (this) {
        90 -> Surface.ROTATION_90
        180 -> Surface.ROTATION_180
        270 -> Surface.ROTATION_270
        else -> Surface.ROTATION_0
    }
}

private fun unbindCamera(
    context: Context,
    executor: Executor,
) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    cameraProviderFuture.addListener(
        {
            runCatching {
                cameraProviderFuture.get().unbindAll()
            }
        },
        executor,
    )
}
