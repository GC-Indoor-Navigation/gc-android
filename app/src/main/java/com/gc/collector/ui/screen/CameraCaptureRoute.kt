package com.gc.collector.ui.screen

import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.gc.collector.network.GrpcFrameSender
import com.gc.collector.network.InternalCalibrationUploader

private const val collectorLogTag = "GcCollector"

@Composable
fun CameraCaptureRoute(
    screenState: CollectorScreenState,
    hasCameraPermission: Boolean,
    isLandscape: Boolean,
    collectorViewModel: CollectorViewModel,
    onRequestCameraPermission: () -> Unit,
    onExitCapture: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState = screenState.collectorUiState
    val cameraCaptureUiState = screenState.cameraCaptureUiState
    val settings = uiState.settings
    val frameSender = remember { GrpcFrameSender() }
    val calibrationUploader = remember { InternalCalibrationUploader() }

    KeepScreenOn(enabled = true)

    BackHandler(enabled = cameraCaptureUiState.detailsPanelOpen) {
        collectorViewModel.onCloseDetailsPanel()
    }

    BackHandler(enabled = !cameraCaptureUiState.detailsPanelOpen) {
        collectorViewModel.onStreamStopRequested {
            frameSender.stop()
        }
        onExitCapture()
    }

    DisposableEffect(Unit) {
        onDispose {
            frameSender.stop()
        }
    }

    CameraCaptureScreen(
        modifier = modifier,
        settings = settings,
        stats = uiState.stats,
        sessionId = uiState.sessionId,
        hasCameraPermission = hasCameraPermission,
        cameraStatus = cameraCaptureUiState.cameraStatus,
        networkStatus = cameraCaptureUiState.networkStatus,
        calibrationStatus = cameraCaptureUiState.calibrationStatus,
        controlStatus = uiState.cameraControlStatus,
        isCapturing = uiState.isCapturing,
        isDetailsOpen = cameraCaptureUiState.detailsPanelOpen,
        isLandscape = isLandscape,
        singleCaptureRequestId = cameraCaptureUiState.singleCaptureRequestId,
        singleCaptureEnabled = cameraCaptureUiState.singleCaptureEnabled(
            hasCameraPermission = hasCameraPermission,
            isCapturing = uiState.isCapturing,
        ),
        singleCaptureInProgress = cameraCaptureUiState.singleCaptureInProgress,
        nextFrameSequence = { uiState.stats.frameSequence + 1L },
        onRequestCameraPermission = onRequestCameraPermission,
        onFrameCaptured = { frame ->
            collectorViewModel.onRuntimeFrameCaptured(frame) { capturedFrame ->
                frameSender.send(capturedFrame)
            }
        },
        onSingleFrameCaptured = { frame ->
            collectorViewModel.onCalibrationFrameCaptured(frame) { capturedFrame ->
                calibrationUploader.upload(
                    baseUrl = settings.calibrationHttpBaseUrl,
                    frame = capturedFrame,
                )
            }
        },
        onCameraReady = {
            collectorViewModel.onCameraReady()
        },
        onCameraControlStatus = { status ->
            collectorViewModel.onCameraControlStatus(status)
        },
        onCameraError = { message ->
            collectorViewModel.onCameraError(message)
        },
        onStart = {
            val nowMs = System.currentTimeMillis()
            val nowNs = SystemClock.elapsedRealtimeNanos()
            collectorViewModel.onStreamStartRequested(
                deviceTimestampMs = nowMs,
                deviceMonotonicNs = nowNs,
                startStream = { endpoint ->
                    runCatching {
                        frameSender.start(
                            host = endpoint.host,
                            port = endpoint.port,
                            usePlaintext = endpoint.usePlaintext,
                        )
                    }
                },
                onSessionStarted = { sessionId ->
                    Log.i(collectorLogTag, "Collector session started: $sessionId")
                },
            )
        },
        onStop = {
            collectorViewModel.onStreamStopRequested {
                frameSender.stop()
            }
        },
        onSingleCapture = {
            collectorViewModel.onSingleCaptureRequested()
        },
        onToggleDetails = {
            collectorViewModel.onToggleDetailsPanel()
        },
        onSettingsChange = { updated ->
            collectorViewModel.updateCollectorUiState { state -> state.copy(settings = updated) }
        },
    )
}
