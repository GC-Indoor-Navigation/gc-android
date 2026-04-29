package com.gc.collector.ui.camera

import android.content.Context
import android.hardware.camera2.CaptureRequest
import android.util.Range
import android.util.Size
import android.view.Surface
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.gc.collector.camera.CapturedFrame
import com.gc.collector.camera.JpegFrameEncoder
import com.gc.collector.model.CameraCaptureSettings
import com.gc.collector.model.FrameMetadataFactory
import java.util.concurrent.Executor

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    isAnalyzing: Boolean,
    settings: CameraCaptureSettings,
    nextFrameSequence: () -> Long,
    onFrameCaptured: (CapturedFrame) -> Unit,
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

    LaunchedEffect(context, lifecycleOwner, previewView, isAnalyzing, settings, nextFrameSequence, onFrameCaptured) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
            {
                runCatching {
                    val cameraProvider = cameraProviderFuture.get()
                    val previewBuilder = Preview.Builder()
                    previewBuilder.applyCaptureSettings(settings)
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

private fun Preview.Builder.applyCaptureSettings(settings: CameraCaptureSettings) {
    setTargetResolution(settings.resolution.toSize())
    setTargetRotation(settings.orientationDeg.toSurfaceRotation())
    Camera2Interop.Extender(this).apply {
        applyCommonCaptureSettings(settings)
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

private fun com.gc.collector.model.ResolutionOption.toSize(): Size {
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
