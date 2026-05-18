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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker.PERMISSION_GRANTED
import com.gc.collector.camera.CapturedFrame
import com.gc.collector.model.CameraCaptureSettings
import com.gc.collector.model.CameraControlStatus
import com.gc.collector.model.CaptureStats
import com.gc.collector.model.CollectorUiState
import com.gc.collector.model.ResolutionOption
import com.gc.collector.model.SessionIdFactory
import com.gc.collector.model.toAppliedState
import com.gc.collector.network.GrpcFrameSender
import com.gc.collector.network.InternalCalibrationUploader
import com.gc.collector.network.InternalCalibrationUploadResult
import com.gc.collector.network.SendResult
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
                val fps = calculateCurrentFps(
                    frame = frame,
                    lastWindowStartedNs = lastFpsWindowStartedNs,
                    framesInWindow = framesInCurrentWindow,
                    onWindowReset = { startedNs ->
                        lastFpsWindowStartedNs = startedNs
                        framesInCurrentWindow = 0
                    },
                    onFrameCounted = { countedFrames ->
                        framesInCurrentWindow = countedFrames
                    },
                    previousFps = uiState.stats.currentFps,
                )

                uiState = uiState.copy(
                    stats = uiState.stats.copy(
                        frameSequence = frame.metadata.frameSequence,
                        lastDeviceTimestampMs = frame.metadata.deviceTimestampMs,
                        lastDeviceMonotonicNs = frame.metadata.deviceMonotonicNs,
                        currentFps = fps,
                    ),
                )

                coroutineScope.launch(Dispatchers.IO) {
                    val sendResult = frameSender.send(frame)
                    withContext(Dispatchers.Main) {
                        when (sendResult) {
                            SendResult.Sent -> {
                                uiState = uiState.copy(
                                    stats = uiState.stats.copy(
                                        sentCount = uiState.stats.sentCount + 1L,
                                    ),
                                )
                                networkStatus = "gRPC streaming"
                            }

                            is SendResult.Failed -> {
                                uiState = uiState.copy(
                                    stats = uiState.stats.copy(
                                        failedCount = uiState.stats.failedCount + 1L,
                                    ),
                                )
                                networkStatus = sendResult.message
                            }

                            SendResult.NotStarted -> {
                                uiState = uiState.copy(
                                    stats = uiState.stats.copy(
                                        droppedFrames = uiState.stats.droppedFrames + 1L,
                                    ),
                                )
                                networkStatus = "gRPC stream not started"
                            }
                        }
                    }
                }
            }
        },
        onSingleFrameCaptured = { frame ->
            uiState = uiState.copy(
                stats = uiState.stats.copy(
                    frameSequence = frame.metadata.frameSequence,
                    lastDeviceTimestampMs = frame.metadata.deviceTimestampMs,
                    lastDeviceMonotonicNs = frame.metadata.deviceMonotonicNs,
                ),
            )

            coroutineScope.launch(Dispatchers.IO) {
                val uploadResult = calibrationUploader.upload(
                    baseUrl = settings.calibrationHttpBaseUrl,
                    frame = frame,
                )
                withContext(Dispatchers.Main) {
                    calibrationUploadInProgress = false
                    calibrationStatus = when (uploadResult) {
                        InternalCalibrationUploadResult.Uploaded ->
                            "Calibration uploaded: ${frame.metadata.frameSequence}"

                        is InternalCalibrationUploadResult.Failed ->
                            "Calibration failed: ${uploadResult.message}"
                    }
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
            calibrationUploadInProgress = true
            calibrationStatus = "Calibration capture requested"
            calibrationCaptureRequestId += 1L
        },
        onToggleDetails = {
            detailsPanelOpen = !detailsPanelOpen
        },
        onSettingsChange = { updated -> uiState = uiState.copy(settings = updated) },
    )
}

private fun calculateCurrentFps(
    frame: CapturedFrame,
    lastWindowStartedNs: Long?,
    framesInWindow: Int,
    onWindowReset: (Long) -> Unit,
    onFrameCounted: (Int) -> Unit,
    previousFps: Float,
): Float {
    val startedNs = lastWindowStartedNs
    if (startedNs == null) {
        onWindowReset(frame.sensorTimestampNs)
        onFrameCounted(1)
        return previousFps
    }

    val elapsedNs = frame.sensorTimestampNs - startedNs
    val nextFrameCount = framesInWindow + 1
    if (elapsedNs >= 1_000_000_000L) {
        val fps = nextFrameCount * 1_000_000_000f / elapsedNs
        onWindowReset(frame.sensorTimestampNs)
        return fps
    }

    onFrameCounted(nextFrameCount)
    return previousFps
}

private fun List<ResolutionOption>.chooseFallbackResolution(): ResolutionOption {
    return firstOrNull { option -> option == ResolutionOption.HD }
        ?: minBy { option ->
            val areaDiff = kotlin.math.abs((option.width * option.height) - (ResolutionOption.HD.width * ResolutionOption.HD.height))
            val aspectDiff = kotlin.math.abs((option.width.toFloat() / option.height) - (16f / 9f))
            areaDiff + (aspectDiff * 100_000).toInt()
        }
}

private fun collectorUiStateSaver(): Saver<CollectorUiState, Any> {
    return listSaver(
        save = { state ->
            listOf(
                state.isCapturing,
                state.settings.cameraId,
                state.settings.deviceId,
                state.settings.serverUrl,
                state.settings.resolution.label,
                state.settings.resolution.width,
                state.settings.resolution.height,
                state.settings.fpsTarget,
                state.settings.focusMode,
                state.settings.focusLocked,
                state.settings.exposureLocked,
                state.settings.whiteBalanceLocked,
                state.settings.zoomDisabled,
                state.settings.orientationDeg,
                state.settings.manualExposureEnabled,
                state.settings.iso,
                state.settings.exposureTimeNs,
                state.stats.frameSequence,
                state.stats.lastDeviceTimestampMs,
                state.stats.lastDeviceMonotonicNs,
                state.stats.sentCount,
                state.stats.failedCount,
                state.stats.droppedFrames,
                state.stats.currentFps,
                state.cameraControlStatus.focusLockSupported,
                state.cameraControlStatus.focusLockApplied,
                state.cameraControlStatus.exposureLockSupported,
                state.cameraControlStatus.exposureLockApplied,
                state.cameraControlStatus.whiteBalanceLockSupported,
                state.cameraControlStatus.whiteBalanceLockApplied,
                state.cameraControlStatus.fpsTargetSupported,
                state.cameraControlStatus.resolutionSupported,
                state.cameraControlStatus.manualExposureSupported,
                state.cameraControlStatus.manualExposureApplied,
                state.cameraControlStatus.isoApplied,
                state.cameraControlStatus.exposureTimeNsApplied,
                state.cameraControlStatus.focalLengthMm,
                state.cameraControlStatus.zoomSupported,
                state.sessionId,
                state.settings.calibrationHttpBaseUrl,
            )
        },
        restore = { values ->
            CollectorUiState(
                isCapturing = values[0] as Boolean,
                settings = CameraCaptureSettings(
                    cameraId = values[1] as String,
                    deviceId = values[2] as String,
                    serverUrl = values[3] as String,
                    calibrationHttpBaseUrl = values.getOrNull(39) as? String ?: CameraCaptureSettings().calibrationHttpBaseUrl,
                    resolution = ResolutionOption(
                        label = values[4] as String,
                        width = (values[5] as Number).toInt(),
                        height = (values[6] as Number).toInt(),
                    ),
                    fpsTarget = (values[7] as Number).toInt(),
                    focusMode = values[8] as String,
                    focusLocked = values[9] as Boolean,
                    exposureLocked = values[10] as Boolean,
                    whiteBalanceLocked = values[11] as Boolean,
                    zoomDisabled = values[12] as Boolean,
                    orientationDeg = (values[13] as Number).toInt(),
                    manualExposureEnabled = values[14] as Boolean,
                    iso = (values[15] as Number).toInt(),
                    exposureTimeNs = (values[16] as Number).toLong(),
                ),
                stats = CaptureStats(
                    frameSequence = (values[17] as Number).toLong(),
                    lastDeviceTimestampMs = (values[18] as Number?)?.toLong(),
                    lastDeviceMonotonicNs = (values[19] as Number?)?.toLong(),
                    sentCount = (values[20] as Number).toLong(),
                    failedCount = (values[21] as Number).toLong(),
                    droppedFrames = (values[22] as Number).toLong(),
                    currentFps = (values[23] as Number).toFloat(),
                ),
                cameraControlStatus = CameraControlStatus(
                    focusLockSupported = values[24] as Boolean?,
                    focusLockApplied = values[25] as Boolean?,
                    exposureLockSupported = values[26] as Boolean?,
                    exposureLockApplied = values[27] as Boolean?,
                    whiteBalanceLockSupported = values[28] as Boolean?,
                    whiteBalanceLockApplied = values[29] as Boolean?,
                    fpsTargetSupported = values[30] as Boolean?,
                    resolutionSupported = values[31] as Boolean?,
                    manualExposureSupported = values[32] as Boolean?,
                    manualExposureApplied = values[33] as Boolean?,
                    isoApplied = (values[34] as Number?)?.toInt(),
                    exposureTimeNsApplied = (values[35] as Number?)?.toLong(),
                    focalLengthMm = (values[36] as Number?)?.toFloat(),
                    zoomSupported = values[37] as Boolean?,
                ),
                sessionId = values.getOrNull(38) as String?,
            )
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun MainScreenPreview() {
    GcandroidTheme {
        MainScreen()
    }
}
