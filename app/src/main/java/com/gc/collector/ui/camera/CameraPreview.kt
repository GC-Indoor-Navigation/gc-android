package com.gc.collector.ui.camera

import android.content.Context
import android.content.res.Configuration
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
import androidx.camera.core.ImageProxy
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
import androidx.compose.ui.platform.LocalConfiguration
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
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    isAnalyzing: Boolean,
    settings: CameraCaptureSettings,
    sessionId: String?,
    singleCaptureRequestId: Long,
    nextFrameSequence: () -> Long,
    controlStatus: CameraControlStatus,
    onFrameCaptured: (CapturedFrame) -> Unit,
    onSingleFrameCaptured: (CapturedFrame) -> Unit,
    onCameraControlStatus: (CameraControlStatus) -> Unit,
    onCameraReady: () -> Unit,
    onCameraError: (String) -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val targetRotation = configuration.toSurfaceRotation()
    val targetFpsRange = remember(context, settings.fpsTarget) {
        chooseBackCameraFpsRange(context, settings.fpsTarget)
    }
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
    }
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val currentControlStatus = rememberUpdatedState(controlStatus)
    val currentIsAnalyzing = rememberUpdatedState(isAnalyzing)
    val currentSessionId = rememberUpdatedState(sessionId)
    val currentNextFrameSequence = rememberUpdatedState(nextFrameSequence)
    val currentOnFrameCaptured = rememberUpdatedState(onFrameCaptured)
    val currentSingleCaptureRequestId = rememberUpdatedState(singleCaptureRequestId)
    val currentOnSingleFrameCaptured = rememberUpdatedState(onSingleFrameCaptured)
    val currentOnCameraControlStatus = rememberUpdatedState(onCameraControlStatus)
    val currentOnCameraReady = rememberUpdatedState(onCameraReady)
    val currentOnCameraError = rememberUpdatedState(onCameraError)
    val handledSingleCaptureRequestId = remember { AtomicLong(0L) }
    val runtimeFrameRateLimiter = remember { RuntimeFrameRateLimiter() }

    LaunchedEffect(settings.fpsTarget, sessionId) {
        runtimeFrameRateLimiter.reset()
    }

    LaunchedEffect(
        context,
        lifecycleOwner,
        previewView,
        settings,
        targetRotation,
        targetFpsRange,
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
                                currentOnCameraControlStatus.value(updatedStatus)
                            }
                        }
                    }
                    val previewBuilder = Preview.Builder()
                    previewBuilder.applyCaptureSettings(
                        settings = settings,
                        targetRotation = targetRotation,
                        targetFpsRange = targetFpsRange,
                        captureCallback = captureCallback,
                    )
                    val preview = previewBuilder.build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val analysisBuilder = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    analysisBuilder.applyCaptureSettings(
                        settings = settings,
                        targetRotation = targetRotation,
                        targetFpsRange = targetFpsRange,
                    )
                    val imageAnalysis = analysisBuilder.build()
                        .also { analysis ->
                            analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                                try {
                                    val requestId = currentSingleCaptureRequestId.value
                                    val shouldCaptureSingleFrame = requestId > 0L &&
                                        handledSingleCaptureRequestId.get() != requestId
                                    if (shouldCaptureSingleFrame) {
                                        handledSingleCaptureRequestId.set(requestId)
                                    }

                                    val sensorTimestampNs = imageProxy.imageInfo.timestamp
                                    val shouldEmitRuntimeFrame = !shouldCaptureSingleFrame &&
                                        currentIsAnalyzing.value &&
                                        runtimeFrameRateLimiter.shouldEmit(
                                            sensorTimestampNs = sensorTimestampNs,
                                            targetFps = settings.fpsTarget,
                                        )

                                    if (shouldEmitRuntimeFrame || shouldCaptureSingleFrame) {
                                        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                                        val frameSize = imageProxy.rotatedSize(rotationDegrees)
                                        val metadata = FrameMetadataFactory.create(
                                            settings = settings,
                                            controlStatus = currentControlStatus.value,
                                            frameSequence = currentNextFrameSequence.value(),
                                            sessionId = if (shouldCaptureSingleFrame) null else currentSessionId.value,
                                            width = frameSize.width,
                                            height = frameSize.height,
                                            orientationDeg = rotationDegrees,
                                        )
                                        val jpegBytes = JpegFrameEncoder.encode(
                                            imageProxy = imageProxy,
                                            rotationDegrees = rotationDegrees,
                                        )
                                        val capturedFrame = CapturedFrame(
                                            jpegBytes = jpegBytes,
                                            metadata = metadata,
                                            sensorTimestampNs = sensorTimestampNs,
                                        )
                                        mainExecutor.execute {
                                            if (shouldCaptureSingleFrame) {
                                                currentOnSingleFrameCaptured.value(capturedFrame)
                                            } else {
                                                currentOnFrameCaptured.value(capturedFrame)
                                            }
                                        }
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
                    currentOnCameraControlStatus.value(cameraControlStatus)
                    currentOnCameraReady.value()
                }.onFailure { error ->
                    currentOnCameraError.value(error.message ?: "Failed to bind camera preview")
                }
            },
            mainExecutor,
        )
    }

    DisposableEffect(context, analysisExecutor) {
        onDispose {
            unbindCamera(context, mainExecutor)
            analysisExecutor.shutdown()
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
    targetRotation: Int,
    targetFpsRange: Range<Int>,
    captureCallback: CameraCaptureSession.CaptureCallback,
) {
    setTargetResolution(settings.resolution.toSize(targetRotation))
    setTargetRotation(targetRotation)
    Camera2Interop.Extender(this).apply {
        applyCommonCaptureSettings(settings, targetFpsRange)
        setSessionCaptureCallback(captureCallback)
    }
}

private fun ImageAnalysis.Builder.applyCaptureSettings(
    settings: CameraCaptureSettings,
    targetRotation: Int,
    targetFpsRange: Range<Int>,
) {
    setTargetResolution(settings.resolution.toSize(targetRotation))
    setTargetRotation(targetRotation)
    Camera2Interop.Extender(this).apply {
        applyCommonCaptureSettings(settings, targetFpsRange)
    }
}

private fun Camera2Interop.Extender<*>.applyCommonCaptureSettings(
    settings: CameraCaptureSettings,
    targetFpsRange: Range<Int>,
) {
    if (settings.manualExposureEnabled) {
        setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
        setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, settings.iso)
        setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, settings.exposureTimeNs)
    } else {
        setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
    }

    if (settings.focusLocked) {
        setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
        setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, 0f)
    } else {
        setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
    }
    setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, settings.exposureLocked && !settings.manualExposureEnabled)
    setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, targetFpsRange)
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
    val focalLengths = camera2Info.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)

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
        manualExposureApplied = null,
        isoApplied = null,
        exposureTimeNsApplied = null,
        focalLengthMm = focalLengths?.firstOrNull(),
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
    val aeMode = request.get(CaptureRequest.CONTROL_AE_MODE)
    val awbLock = request.get(CaptureRequest.CONTROL_AWB_LOCK)
    val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
    val awbState = result.get(CaptureResult.CONTROL_AWB_STATE)
    val isoApplied = result.get(CaptureResult.SENSOR_SENSITIVITY)
    val exposureTimeNsApplied = result.get(CaptureResult.SENSOR_EXPOSURE_TIME)
    val focalLengthMm = result.get(CaptureResult.LENS_FOCAL_LENGTH) ?: focalLengthMm

    return copy(
        focusLockApplied = if (settings.focusLocked) {
            afMode == CaptureRequest.CONTROL_AF_MODE_OFF
        } else {
            afMode == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
        },
        exposureLockApplied = if (settings.exposureLocked) {
            !settings.manualExposureEnabled &&
                aeLock == true &&
                (aeState == CaptureResult.CONTROL_AE_STATE_LOCKED || aeState == null)
        } else {
            aeLock == false || aeLock == null
        },
        whiteBalanceLockApplied = if (settings.whiteBalanceLocked) {
            awbLock == true && (awbState == CaptureResult.CONTROL_AWB_STATE_LOCKED || awbState == null)
        } else {
            awbLock == false || awbLock == null
        },
        manualExposureApplied = if (settings.manualExposureEnabled) {
            aeMode == CaptureRequest.CONTROL_AE_MODE_OFF &&
                isoApplied == settings.iso &&
                exposureTimeNsApplied == settings.exposureTimeNs
        } else {
            aeMode == CaptureRequest.CONTROL_AE_MODE_ON || aeMode == null
        },
        isoApplied = isoApplied,
        exposureTimeNsApplied = exposureTimeNsApplied,
        focalLengthMm = focalLengthMm,
    )
}

private fun Size.matches(option: ResolutionOption): Boolean {
    return (width == option.width && height == option.height) ||
        (width == option.height && height == option.width)
}

private fun ResolutionOption.toSize(targetRotation: Int): Size {
    return if (targetRotation == Surface.ROTATION_0 || targetRotation == Surface.ROTATION_180) {
        Size(height, width)
    } else {
        Size(width, height)
    }
}

private data class FrameSize(
    val width: Int,
    val height: Int,
)

private fun ImageProxy.rotatedSize(rotationDegrees: Int): FrameSize {
    val normalizedRotation = ((rotationDegrees % 360) + 360) % 360
    return if (normalizedRotation == 90 || normalizedRotation == 270) {
        FrameSize(width = height, height = width)
    } else {
        FrameSize(width = width, height = height)
    }
}

private fun Configuration.toSurfaceRotation(): Int {
    return when (orientation) {
        Configuration.ORIENTATION_LANDSCAPE -> Surface.ROTATION_90
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
