package com.gc.collector.ui.screen

import android.Manifest
import android.app.Activity
import android.content.res.Configuration
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker.PERMISSION_GRANTED
import com.gc.collector.model.CameraCaptureSettings
import com.gc.collector.model.CameraControlStatus
import com.gc.collector.model.CalibrationCaptureState
import com.gc.collector.model.CalibrationCaptureStateReducer
import com.gc.collector.model.CalibrationUploadOutcome
import com.gc.collector.model.CaptureStats
import com.gc.collector.model.CollectorUiState
import com.gc.collector.model.FpsCalculator
import com.gc.collector.model.ResolutionOption
import com.gc.collector.model.SessionIdFactory
import com.gc.collector.model.toAppliedState
import com.gc.collector.network.FrameSendResultReducer
import com.gc.collector.network.GrpcFrameSender
import com.gc.collector.network.InternalCalibrationUploader
import com.gc.collector.network.InternalCalibrationUploadResult
import com.gc.collector.network.parseGrpcEndpoint
import com.gc.collector.ui.camera.loadBackCameraResolutionOptions
import com.gc.collector.ui.theme.GcandroidTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val collectorLogTag = "GcCollector"

private enum class CollectorScreen {
    ModeSelection,
    CameraSetup,
    CameraCapture,
    UseMode,
}

@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val activity = LocalContext.current as? Activity
    DisposableEffect(activity, enabled) {
        if (enabled) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    var currentScreenName by rememberSaveable { mutableStateOf(CollectorScreen.ModeSelection.name) }
    var uiState by rememberSaveable(stateSaver = collectorUiStateSaver()) {
        mutableStateOf(CollectorUiState())
    }
    val currentScreen = CollectorScreen.valueOf(currentScreenName)
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PERMISSION_GRANTED,
        )
    }
    var cameraStatus by remember { mutableStateOf("Camera preview not started") }
    var detailsPanelOpen by rememberSaveable { mutableStateOf(false) }
    var lastFpsWindowStartedNs by remember { mutableStateOf<Long?>(null) }
    var framesInCurrentWindow by remember { mutableStateOf(0) }
    var networkStatus by rememberSaveable { mutableStateOf("gRPC disconnected") }
    var calibrationStatus by rememberSaveable { mutableStateOf("Calibration idle") }
    var calibrationCaptureRequestId by rememberSaveable { mutableStateOf(0L) }
    var calibrationUploadInProgress by rememberSaveable { mutableStateOf(false) }
    var resolutionOptions by remember { mutableStateOf(ResolutionOption.commonOptions) }
    var resolutionOptionsStatus by rememberSaveable { mutableStateOf("common resolution presets") }
    val frameSender = remember { GrpcFrameSender() }
    val calibrationUploader = remember { InternalCalibrationUploader() }
    val coroutineScope = rememberCoroutineScope()
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
            cameraStatus = if (granted) {
                "Camera permission granted"
            } else {
                "Camera permission denied"
            }
        },
    )
    val settings = uiState.settings
    val stats = uiState.stats
    val calibrationCaptureState = CalibrationCaptureState(
        status = calibrationStatus,
        captureRequestId = calibrationCaptureRequestId,
        uploadInProgress = calibrationUploadInProgress,
    )

    KeepScreenOn(enabled = currentScreen == CollectorScreen.CameraCapture)

    BackHandler(enabled = currentScreen == CollectorScreen.CameraSetup || currentScreen == CollectorScreen.UseMode) {
        currentScreenName = CollectorScreen.ModeSelection.name
    }

    BackHandler(enabled = detailsPanelOpen) {
        detailsPanelOpen = false
    }

    BackHandler(enabled = currentScreen == CollectorScreen.CameraCapture && !detailsPanelOpen) {
        frameSender.stop()
        currentScreenName = CollectorScreen.CameraSetup.name
        uiState = uiState.copy(isCapturing = false, sessionId = null)
        networkStatus = "gRPC stopped"
    }

    DisposableEffect(Unit) {
        onDispose {
            frameSender.stop()
        }
    }

    LaunchedEffect(Unit) {
        if (currentScreen == CollectorScreen.CameraCapture && !hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(currentScreen) {
        if (currentScreen == CollectorScreen.CameraSetup) {
            val loadedOptions = withContext(Dispatchers.Default) {
                runCatching { loadBackCameraResolutionOptions(context) }.getOrDefault(emptyList())
            }
            if (loadedOptions.isNotEmpty()) {
                resolutionOptions = loadedOptions
                resolutionOptionsStatus = "supported by back camera (${loadedOptions.size})"
                if (settings.resolution !in loadedOptions) {
                    uiState = uiState.copy(settings = settings.copy(resolution = loadedOptions.chooseFallbackResolution()))
                }
            } else {
                resolutionOptions = ResolutionOption.commonOptions
                resolutionOptionsStatus = "using common presets"
            }
        }
    }

    when (currentScreen) {
        CollectorScreen.ModeSelection -> {
            ModeSelectionScreen(
                modifier = modifier,
                onCameraMode = { currentScreenName = CollectorScreen.CameraSetup.name },
                onUseMode = { currentScreenName = CollectorScreen.UseMode.name },
            )
            return
        }

        CollectorScreen.CameraSetup -> {
            CameraSetupScreen(
                modifier = modifier,
                settings = settings,
                resolutionOptions = resolutionOptions,
                resolutionOptionsStatus = resolutionOptionsStatus,
                onSettingsChange = { updated -> uiState = uiState.copy(settings = updated) },
                onBack = { currentScreenName = CollectorScreen.ModeSelection.name },
                onContinue = {
                    currentScreenName = CollectorScreen.CameraCapture.name
                    if (!hasCameraPermission) {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
            )
            return
        }

        CollectorScreen.UseMode -> {
            UseModePlaceholderScreen(
                modifier = modifier,
                onBack = { currentScreenName = CollectorScreen.ModeSelection.name },
            )
            return
        }

        CollectorScreen.CameraCapture -> Unit
    }

    CameraCaptureScreen(
        modifier = modifier,
        settings = settings,
        stats = stats,
        sessionId = uiState.sessionId,
        hasCameraPermission = hasCameraPermission,
        cameraStatus = cameraStatus,
        networkStatus = networkStatus,
        calibrationStatus = calibrationStatus,
        controlStatus = uiState.cameraControlStatus,
        isCapturing = uiState.isCapturing,
        isDetailsOpen = detailsPanelOpen,
        isLandscape = isLandscape,
        singleCaptureRequestId = calibrationCaptureRequestId,
        singleCaptureEnabled = hasCameraPermission && !uiState.isCapturing && !calibrationUploadInProgress,
        singleCaptureInProgress = calibrationUploadInProgress,
        nextFrameSequence = { uiState.stats.frameSequence + 1L },
        onRequestCameraPermission = {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        },
        onFrameCaptured = { frame ->
            if (uiState.isCapturing) {
                val fpsResult = FpsCalculator.calculate(
                    sensorTimestampNs = frame.sensorTimestampNs,
                    lastWindowStartedNs = lastFpsWindowStartedNs,
                    framesInWindow = framesInCurrentWindow,
                    previousFps = uiState.stats.currentFps,
                )
                lastFpsWindowStartedNs = fpsResult.windowStartedNs
                framesInCurrentWindow = fpsResult.framesInWindow

                uiState = uiState.copy(
                    stats = uiState.stats.copy(
                        frameSequence = frame.metadata.frameSequence,
                        lastDeviceTimestampMs = frame.metadata.deviceTimestampMs,
                        lastDeviceMonotonicNs = frame.metadata.deviceMonotonicNs,
                        currentFps = fpsResult.currentFps,
                    ),
                )

                coroutineScope.launch(Dispatchers.IO) {
                    val sendResult = frameSender.send(frame)
                    withContext(Dispatchers.Main) {
                        val nextState = FrameSendResultReducer.reduce(
                            stats = uiState.stats,
                            result = sendResult,
                        )
                        uiState = uiState.copy(stats = nextState.stats)
                        networkStatus = nextState.networkStatus
                    }
                }
            }
        },
        onSingleFrameCaptured = { frame ->
            uiState = uiState.copy(
                stats = CalibrationCaptureStateReducer.applyCapturedFrame(
                    stats = uiState.stats,
                    metadata = frame.metadata,
                ),
            )

            coroutineScope.launch(Dispatchers.IO) {
                val uploadResult = calibrationUploader.upload(
                    baseUrl = settings.calibrationHttpBaseUrl,
                    frame = frame,
                )
                withContext(Dispatchers.Main) {
                    val outcome = when (uploadResult) {
                        InternalCalibrationUploadResult.Uploaded -> CalibrationUploadOutcome.Uploaded
                        is InternalCalibrationUploadResult.Failed -> CalibrationUploadOutcome.Failed(uploadResult.message)
                    }
                    val nextState = CalibrationCaptureStateReducer.completeUpload(
                        state = CalibrationCaptureState(
                            status = calibrationStatus,
                            captureRequestId = calibrationCaptureRequestId,
                            uploadInProgress = calibrationUploadInProgress,
                        ),
                        frameSequence = frame.metadata.frameSequence,
                        outcome = outcome,
                    )
                    calibrationStatus = nextState.status
                    calibrationCaptureRequestId = nextState.captureRequestId
                    calibrationUploadInProgress = nextState.uploadInProgress
                }
            }
        },
        onCameraReady = {
            cameraStatus = "Back camera preview ready"
        },
        onCameraControlStatus = { status ->
            uiState = uiState.copy(cameraControlStatus = status)
        },
        onCameraError = { message ->
            cameraStatus = message
        },
        onStart = {
            val nowMs = System.currentTimeMillis()
            val nowNs = SystemClock.elapsedRealtimeNanos()
            val sessionId = SessionIdFactory.create(settings.deviceId, nowMs)
            parseGrpcEndpoint(settings.serverUrl)
                .onSuccess { endpoint ->
                    runCatching {
                        frameSender.start(
                            host = endpoint.host,
                            port = endpoint.port,
                            usePlaintext = endpoint.usePlaintext,
                        )
                    }.onSuccess {
                        Log.i(collectorLogTag, "Collector session started: $sessionId")
                        networkStatus = "gRPC connected to ${endpoint.host}:${endpoint.port}"
                        uiState = uiState.copy(
                            isCapturing = true,
                            sessionId = sessionId,
                            stats = stats.copy(
                                frameSequence = 0L,
                                lastDeviceTimestampMs = nowMs,
                                lastDeviceMonotonicNs = nowNs,
                                sentCount = 0L,
                                failedCount = 0L,
                                droppedFrames = 0L,
                                currentFps = 0f,
                            ),
                        )
                        lastFpsWindowStartedNs = null
                        framesInCurrentWindow = 0
                    }.onFailure { error ->
                        networkStatus = error.message ?: "Failed to start gRPC stream"
                        uiState = uiState.copy(
                            isCapturing = false,
                            sessionId = null,
                            stats = stats.copy(failedCount = stats.failedCount + 1L),
                        )
                    }
                }
                .onFailure { error ->
                    networkStatus = error.message ?: "Invalid gRPC endpoint"
                    uiState = uiState.copy(
                        isCapturing = false,
                        sessionId = null,
                        stats = stats.copy(failedCount = stats.failedCount + 1L),
                    )
                }
        },
        onStop = {
            frameSender.stop()
            uiState = uiState.copy(isCapturing = false, sessionId = null)
            networkStatus = "gRPC stopped"
        },
        onSingleCapture = {
            val nextState = CalibrationCaptureStateReducer.requestCapture(calibrationCaptureState)
            calibrationStatus = nextState.status
            calibrationCaptureRequestId = nextState.captureRequestId
            calibrationUploadInProgress = nextState.uploadInProgress
        },
        onToggleDetails = {
            detailsPanelOpen = !detailsPanelOpen
        },
        onSettingsChange = { updated -> uiState = uiState.copy(settings = updated) },
    )
}

private fun List<ResolutionOption>.chooseFallbackResolution(): ResolutionOption {
    return firstOrNull { option -> option == ResolutionOption.HD }
        ?: minBy { option ->
            val areaDiff = kotlin.math.abs((option.width * option.height) - (ResolutionOption.HD.width * ResolutionOption.HD.height))
            val aspectDiff = kotlin.math.abs((option.width.toFloat() / option.height) - (16f / 9f))
            areaDiff + (aspectDiff * 100_000).toInt()
        }
}

@Preview(showBackground = true)
@Composable
private fun MainScreenPreview() {
    GcandroidTheme {
        MainScreen()
    }
}
