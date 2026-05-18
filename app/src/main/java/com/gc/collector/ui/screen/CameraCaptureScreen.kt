package com.gc.collector.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gc.collector.camera.CapturedFrame
import com.gc.collector.model.CameraCaptureSettings
import com.gc.collector.model.CameraControlStatus
import com.gc.collector.model.CaptureStats
import com.gc.collector.model.ResolutionOption
import com.gc.collector.ui.camera.CameraPreview

@Composable
fun CameraCaptureScreen(
    settings: CameraCaptureSettings,
    stats: CaptureStats,
    sessionId: String?,
    hasCameraPermission: Boolean,
    cameraStatus: String,
    networkStatus: String,
    calibrationStatus: String,
    controlStatus: CameraControlStatus,
    isCapturing: Boolean,
    isDetailsOpen: Boolean,
    isLandscape: Boolean,
    singleCaptureRequestId: Long,
    singleCaptureEnabled: Boolean,
    singleCaptureInProgress: Boolean,
    nextFrameSequence: () -> Long,
    onRequestCameraPermission: () -> Unit,
    onFrameCaptured: (CapturedFrame) -> Unit,
    onSingleFrameCaptured: (CapturedFrame) -> Unit,
    onCameraControlStatus: (CameraControlStatus) -> Unit,
    onCameraReady: () -> Unit,
    onCameraError: (String) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onSingleCapture: () -> Unit,
    onToggleDetails: () -> Unit,
    onSettingsChange: (CameraCaptureSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val panelWidth = if (isLandscape) {
            minOf(maxWidth * 0.42f, 380.dp)
        } else {
            minOf(maxWidth * 0.88f, 360.dp)
        }
        val controlsModifier = if (isLandscape) {
            Modifier
                .align(Alignment.CenterEnd)
                .safeDrawingPadding()
                .padding(end = 8.dp)
        } else {
            Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(bottom = 24.dp)
        }

        CameraPanel(
            modifier = Modifier.fillMaxSize(),
            hasCameraPermission = hasCameraPermission,
            cameraStatus = cameraStatus,
            resolution = settings.resolution,
            onRequestPermission = onRequestCameraPermission,
            isCapturing = isCapturing,
            settings = settings,
            sessionId = sessionId,
            singleCaptureRequestId = singleCaptureRequestId,
            controlStatus = controlStatus,
            nextFrameSequence = nextFrameSequence,
            onFrameCaptured = onFrameCaptured,
            onSingleFrameCaptured = onSingleFrameCaptured,
            onCameraControlStatus = onCameraControlStatus,
            onCameraReady = onCameraReady,
            onCameraError = onCameraError,
        )

        CaptureOverlayControls(
            isCapturing = isCapturing,
            isDetailsOpen = isDetailsOpen,
            isLandscape = isLandscape,
            onStart = onStart,
            onStop = onStop,
            onSingleCapture = onSingleCapture,
            singleCaptureEnabled = singleCaptureEnabled,
            singleCaptureInProgress = singleCaptureInProgress,
            onToggleDetails = onToggleDetails,
            modifier = controlsModifier,
        )

        if (isLandscape) {
            AnimatedVisibility(
                visible = isDetailsOpen,
                enter = slideInHorizontally(initialOffsetX = { fullWidth -> fullWidth / 2 }),
                exit = slideOutHorizontally(targetOffsetX = { fullWidth -> fullWidth / 2 }),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .safeDrawingPadding()
                    .padding(top = 148.dp, end = 12.dp, bottom = 12.dp),
            ) {
                SlideDetailsPanel(
                    modifier = Modifier
                        .width(panelWidth)
                        .fillMaxHeight()
                        .padding(vertical = 0.dp, horizontal = 0.dp),
                    settings = settings,
                    stats = stats,
                    sessionId = sessionId,
                    isCapturing = isCapturing,
                    cameraStatus = cameraStatus,
                    networkStatus = networkStatus,
                    calibrationStatus = calibrationStatus,
                    controlStatus = controlStatus,
                    isLandscape = true,
                    includeSettings = false,
                    onSettingsChange = onSettingsChange,
                )
            }
        } else {
            AnimatedVisibility(
                visible = isDetailsOpen,
                enter = slideInVertically(initialOffsetY = { fullHeight -> fullHeight }),
                exit = slideOutVertically(targetOffsetY = { fullHeight -> fullHeight }),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .safeDrawingPadding()
                    .padding(start = 12.dp, end = 12.dp, bottom = 104.dp),
            ) {
                SlideDetailsPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.58f),
                    settings = settings,
                    stats = stats,
                    sessionId = sessionId,
                    isCapturing = isCapturing,
                    cameraStatus = cameraStatus,
                    networkStatus = networkStatus,
                    calibrationStatus = calibrationStatus,
                    controlStatus = controlStatus,
                    isLandscape = false,
                    includeSettings = false,
                    onSettingsChange = onSettingsChange,
                )
            }
        }
    }
}

@Composable
private fun SlideDetailsPanel(
    settings: CameraCaptureSettings,
    stats: CaptureStats,
    sessionId: String?,
    isCapturing: Boolean,
    cameraStatus: String,
    networkStatus: String,
    calibrationStatus: String,
    controlStatus: CameraControlStatus,
    isLandscape: Boolean,
    includeSettings: Boolean,
    onSettingsChange: (CameraCaptureSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 4.dp,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(if (isLandscape) 10.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (isLandscape) 10.dp else 12.dp),
        ) {
            Text(
                text = "Details",
                style = if (isLandscape) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (includeSettings) {
                SettingsPanel(
                    settings = settings,
                    isCapturing = isCapturing,
                    initiallyExpanded = true,
                    onSettingsChange = onSettingsChange,
                )
            }
            StatusPanel(
                settings = settings,
                stats = stats,
                sessionId = sessionId,
                isCapturing = isCapturing,
                cameraStatus = cameraStatus,
                networkStatus = networkStatus,
                calibrationStatus = calibrationStatus,
                controlStatus = controlStatus,
                initiallyExpanded = true,
            )
        }
    }
}

@Composable
private fun CameraPanel(
    hasCameraPermission: Boolean,
    cameraStatus: String,
    resolution: ResolutionOption,
    onRequestPermission: () -> Unit,
    isCapturing: Boolean,
    settings: CameraCaptureSettings,
    sessionId: String?,
    singleCaptureRequestId: Long,
    controlStatus: CameraControlStatus,
    nextFrameSequence: () -> Long,
    onFrameCaptured: (CapturedFrame) -> Unit,
    onSingleFrameCaptured: (CapturedFrame) -> Unit,
    onCameraControlStatus: (CameraControlStatus) -> Unit,
    onCameraReady: () -> Unit,
    onCameraError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(Color(0xFF101820), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (hasCameraPermission) {
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                isAnalyzing = isCapturing,
                settings = settings,
                sessionId = sessionId,
                singleCaptureRequestId = singleCaptureRequestId,
                controlStatus = controlStatus,
                nextFrameSequence = nextFrameSequence,
                onFrameCaptured = onFrameCaptured,
                onSingleFrameCaptured = onSingleFrameCaptured,
                onCameraControlStatus = onCameraControlStatus,
                onCameraReady = onCameraReady,
                onCameraError = onCameraError,
            )
        } else {
            CameraPermissionPlaceholder(
                cameraStatus = cameraStatus,
                onRequestPermission = onRequestPermission,
            )
        }
    }
}

@Composable
private fun CameraPermissionPlaceholder(
    cameraStatus: String,
    onRequestPermission: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Camera permission required",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = cameraStatus,
            color = Color(0xFFB9C6D0),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        OutlinedButton(
            onClick = onRequestPermission,
            border = BorderStroke(1.dp, Color(0xFFB9C6D0)),
        ) {
            Text("Grant Camera Permission", color = Color.White)
        }
    }
}

@Composable
private fun PreviewBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(Color(0xAA000000), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
