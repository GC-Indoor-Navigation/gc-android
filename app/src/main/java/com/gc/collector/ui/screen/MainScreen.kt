package com.gc.collector.ui.screen

import android.Manifest
import android.content.res.Configuration
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker.PERMISSION_GRANTED
import com.gc.collector.camera.CapturedFrame
import com.gc.collector.model.CameraCaptureSettings
import com.gc.collector.model.CameraControlStatus
import com.gc.collector.model.CaptureStats
import com.gc.collector.model.CollectorUiState
import com.gc.collector.model.ResolutionOption
import com.gc.collector.model.toAppliedState
import com.gc.collector.model.toMetadataState
import com.gc.collector.network.GrpcFrameSender
import com.gc.collector.network.SendResult
import com.gc.collector.network.parseGrpcEndpoint
import com.gc.collector.ui.camera.CameraPreview
import com.gc.collector.ui.theme.GcandroidTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val fpsOptions = listOf(5, 10, 15, 30)

private enum class CollectorScreen {
    ModeSelection,
    CameraSetup,
    CameraCapture,
    UseMode,
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
    val frameSender = remember { GrpcFrameSender() }
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

    BackHandler(enabled = currentScreen == CollectorScreen.CameraSetup || currentScreen == CollectorScreen.UseMode) {
        currentScreenName = CollectorScreen.ModeSelection.name
    }

    BackHandler(enabled = detailsPanelOpen) {
        detailsPanelOpen = false
    }

    BackHandler(enabled = currentScreen == CollectorScreen.CameraCapture && !detailsPanelOpen) {
        frameSender.stop()
        currentScreenName = CollectorScreen.CameraSetup.name
        uiState = uiState.copy(isCapturing = false)
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
            onRequestPermission = {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            },
            isCapturing = uiState.isCapturing,
            settings = uiState.settings,
            controlStatus = uiState.cameraControlStatus,
            nextFrameSequence = { uiState.stats.frameSequence + 1L },
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
            onCameraReady = {
                cameraStatus = "Back camera preview ready"
            },
            onCameraControlStatus = { status ->
                uiState = uiState.copy(cameraControlStatus = status)
            },
            onCameraError = { message ->
                cameraStatus = message
            },
        )

        CaptureOverlayControls(
            isCapturing = uiState.isCapturing,
            isDetailsOpen = detailsPanelOpen,
            isLandscape = isLandscape,
            onStart = {
                val nowMs = System.currentTimeMillis()
                val nowNs = SystemClock.elapsedRealtimeNanos()
                parseGrpcEndpoint(settings.serverUrl)
                    .onSuccess { endpoint ->
                        runCatching {
                            frameSender.start(
                                host = endpoint.host,
                                port = endpoint.port,
                                usePlaintext = endpoint.usePlaintext,
                            )
                        }.onSuccess {
                            networkStatus = "gRPC connected to ${endpoint.host}:${endpoint.port}"
                            uiState = uiState.copy(
                                isCapturing = true,
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
                                stats = stats.copy(failedCount = stats.failedCount + 1L),
                            )
                        }
                    }
                    .onFailure { error ->
                        networkStatus = error.message ?: "Invalid gRPC endpoint"
                        uiState = uiState.copy(
                            isCapturing = false,
                            stats = stats.copy(failedCount = stats.failedCount + 1L),
                        )
                    }
            },
            onStop = {
                frameSender.stop()
                uiState = uiState.copy(isCapturing = false)
                networkStatus = "gRPC stopped"
            },
            onToggleDetails = {
                detailsPanelOpen = !detailsPanelOpen
            },
            modifier = controlsModifier,
        )

        if (isLandscape) {
            AnimatedVisibility(
                visible = detailsPanelOpen,
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
                isCapturing = uiState.isCapturing,
                cameraStatus = cameraStatus,
                networkStatus = networkStatus,
                controlStatus = uiState.cameraControlStatus,
                isLandscape = true,
                includeSettings = false,
                onSettingsChange = { updated -> uiState = uiState.copy(settings = updated) },
            )
            }
        } else {
            AnimatedVisibility(
                visible = detailsPanelOpen,
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
                    isCapturing = uiState.isCapturing,
                    cameraStatus = cameraStatus,
                    networkStatus = networkStatus,
                    controlStatus = uiState.cameraControlStatus,
                    isLandscape = false,
                    includeSettings = false,
                    onSettingsChange = { updated -> uiState = uiState.copy(settings = updated) },
                )
            }
        }
    }
}

@Composable
private fun ModeSelectionScreen(
    onCameraMode: () -> Unit,
    onUseMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "GC Android Collector",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(24.dp))
        ModeCard(
            title = "Camera Mode",
            description = "Configure capture settings and collect timestamped camera frames.",
            onClick = onCameraMode,
        )
        Spacer(modifier = Modifier.height(12.dp))
        ModeCard(
            title = "Use Mode",
            description = "Runtime device mode for future collection workflows.",
            onClick = onUseMode,
        )
    }
}

@Composable
private fun ModeCard(
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CameraSetupScreen(
    settings: CameraCaptureSettings,
    onSettingsChange: (CameraCaptureSettings) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Camera Setup",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        SettingsPanel(
            settings = settings,
            isCapturing = false,
            initiallyExpanded = true,
            showToggle = false,
            onSettingsChange = onSettingsChange,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f),
            ) {
                Text("Back")
            }
            Button(
                onClick = onContinue,
                modifier = Modifier.weight(1f),
            ) {
                Text("Open Camera")
            }
        }
    }
}

@Composable
private fun UseModePlaceholderScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Use Mode",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "This mode will be connected after the capture pipeline is complete.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(20.dp))
        OutlinedButton(onClick = onBack) {
            Text("Back")
        }
    }
}

@Composable
private fun SlideDetailsPanel(
    settings: CameraCaptureSettings,
    stats: CaptureStats,
    isCapturing: Boolean,
    cameraStatus: String,
    networkStatus: String,
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
                isCapturing = isCapturing,
                cameraStatus = cameraStatus,
                networkStatus = networkStatus,
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
    controlStatus: CameraControlStatus,
    nextFrameSequence: () -> Long,
    onFrameCaptured: (CapturedFrame) -> Unit,
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
                controlStatus = controlStatus,
                nextFrameSequence = nextFrameSequence,
                onFrameCaptured = onFrameCaptured,
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

@Composable
private fun SettingsPanel(
    settings: CameraCaptureSettings,
    isCapturing: Boolean,
    initiallyExpanded: Boolean = false,
    showToggle: Boolean = true,
    onSettingsChange: (CameraCaptureSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "Capture settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = settings.summaryText(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (showToggle) {
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(if (expanded) "Hide" else "Edit")
                    }
                }
            }

            if (expanded) {
                OutlinedTextField(
                    value = settings.serverUrl,
                    onValueChange = { onSettingsChange(settings.copy(serverUrl = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isCapturing,
                    singleLine = true,
                    label = { Text("gRPC endpoint") },
                )

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = settings.deviceId,
                        onValueChange = { onSettingsChange(settings.copy(deviceId = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isCapturing,
                        singleLine = true,
                        label = { Text("Device ID") },
                    )
                    OutlinedTextField(
                        value = settings.cameraId,
                        onValueChange = { onSettingsChange(settings.copy(cameraId = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isCapturing,
                        singleLine = true,
                        label = { Text("Camera ID") },
                    )
                }

                OptionRow(title = "Resolution") {
                    ResolutionOption.entries.forEach { option ->
                        FilterChip(
                            selected = settings.resolution == option,
                            onClick = { onSettingsChange(settings.copy(resolution = option)) },
                            enabled = !isCapturing,
                            label = { Text(option.label) },
                        )
                    }
                }

                OptionRow(title = "FPS target") {
                    fpsOptions.forEach { fps ->
                        FilterChip(
                            selected = settings.fpsTarget == fps,
                            onClick = { onSettingsChange(settings.copy(fpsTarget = fps)) },
                            enabled = !isCapturing,
                            label = { Text(fps.toString()) },
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Camera controls",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ToggleRow(
                        title = "Lock focus",
                        description = "Try fixed focus instead of continuous autofocus.",
                        checked = settings.focusLocked,
                        enabled = !isCapturing,
                        onCheckedChange = { checked ->
                            onSettingsChange(
                                settings.copy(
                                    focusLocked = checked,
                                    focusMode = if (checked) "locked" else "auto",
                                ),
                            )
                        },
                    )
                    ToggleRow(
                        title = "Lock exposure",
                        description = "Request AE lock for stable brightness.",
                        checked = settings.exposureLocked,
                        enabled = !isCapturing && !settings.manualExposureEnabled,
                        onCheckedChange = { checked ->
                            onSettingsChange(settings.copy(exposureLocked = checked))
                        },
                    )
                    ToggleRow(
                        title = "Manual exposure",
                        description = "Turn AE off and request fixed ISO / shutter time.",
                        checked = settings.manualExposureEnabled,
                        enabled = !isCapturing,
                        onCheckedChange = { checked ->
                            onSettingsChange(
                                settings.copy(
                                    manualExposureEnabled = checked,
                                    exposureLocked = if (checked) false else settings.exposureLocked,
                                ),
                            )
                        },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedTextField(
                            value = settings.iso.toString(),
                            onValueChange = { value ->
                                value.toIntOrNull()?.takeIf { it > 0 }?.let { iso ->
                                    onSettingsChange(settings.copy(iso = iso))
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isCapturing && settings.manualExposureEnabled,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            label = { Text("ISO") },
                        )
                        OutlinedTextField(
                            value = settings.exposureTimeNs.toString(),
                            onValueChange = { value ->
                                value.toLongOrNull()?.takeIf { it > 0L }?.let { exposureTimeNs ->
                                    onSettingsChange(settings.copy(exposureTimeNs = exposureTimeNs))
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isCapturing && settings.manualExposureEnabled,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            label = { Text("Shutter ns") },
                        )
                    }
                    ToggleRow(
                        title = "Lock white balance",
                        description = "Request AWB lock for stable color.",
                        checked = settings.whiteBalanceLocked,
                        enabled = !isCapturing,
                        onCheckedChange = { checked ->
                            onSettingsChange(settings.copy(whiteBalanceLocked = checked))
                        },
                    )
                    ToggleRow(
                        title = "Disable zoom",
                        description = "Keep camera zoom ratio at 1.0x.",
                        checked = settings.zoomDisabled,
                        enabled = !isCapturing,
                        onCheckedChange = { checked ->
                            onSettingsChange(settings.copy(zoomDisabled = checked))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptionRow(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun CaptureOverlayControls(
    isCapturing: Boolean,
    isDetailsOpen: Boolean,
    isLandscape: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onToggleDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val captureClick = {
        if (isCapturing) {
            onStop()
        } else {
            onStart()
        }
    }

    if (isLandscape) {
        val buttonSize = 60.dp
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TransparentCircleButton(
                onClick = captureClick,
                size = buttonSize,
            ) {
                PlayStopIcon(isCapturing = isCapturing)
            }
            TransparentCircleButton(
                onClick = onToggleDetails,
                size = buttonSize,
                active = isDetailsOpen,
            ) {
                SettingsIcon(active = isDetailsOpen)
            }
        }
    } else {
        val buttonSize = 64.dp
        Row(
            modifier = modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TransparentCircleButton(
                onClick = captureClick,
                size = buttonSize,
            ) {
                PlayStopIcon(isCapturing = isCapturing)
            }
            TransparentCircleButton(
                onClick = onToggleDetails,
                size = buttonSize,
                active = isDetailsOpen,
            ) {
                SettingsIcon(active = isDetailsOpen)
            }
        }
    }
}

@Composable
private fun TransparentCircleButton(
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(size),
        shape = CircleShape,
        color = if (active) {
            Color(0xCC1D4ED8)
        } else {
            Color(0x66000000)
        },
        contentColor = Color.White,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.42f)),
        shadowElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Composable
private fun PlayStopIcon(
    isCapturing: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(28.dp)) {
        if (isCapturing) {
            val blockWidth = size.width * 0.26f
            val gap = size.width * 0.16f
            drawRect(
                color = Color.White,
                topLeft = Offset(size.width * 0.18f, size.height * 0.16f),
                size = Size(blockWidth, size.height * 0.68f),
            )
            drawRect(
                color = Color.White,
                topLeft = Offset(size.width * 0.18f + blockWidth + gap, size.height * 0.16f),
                size = Size(blockWidth, size.height * 0.68f),
            )
        } else {
            val path = Path().apply {
                moveTo(size.width * 0.26f, size.height * 0.16f)
                lineTo(size.width * 0.26f, size.height * 0.84f)
                lineTo(size.width * 0.82f, size.height * 0.50f)
                close()
            }
            drawPath(path = path, color = Color.White)
        }
    }
}

@Composable
private fun SettingsIcon(
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val iconColor = if (active) Color.White else Color(0xFFE8EEF3)
    Canvas(modifier = modifier.size(28.dp)) {
        val strokeWidth = size.width * 0.09f
        val y1 = size.height * 0.25f
        val y2 = size.height * 0.50f
        val y3 = size.height * 0.75f

        drawLine(iconColor, Offset(size.width * 0.12f, y1), Offset(size.width * 0.88f, y1), strokeWidth)
        drawLine(iconColor, Offset(size.width * 0.12f, y2), Offset(size.width * 0.88f, y2), strokeWidth)
        drawLine(iconColor, Offset(size.width * 0.12f, y3), Offset(size.width * 0.88f, y3), strokeWidth)
        drawCircle(iconColor, radius = size.width * 0.09f, center = Offset(size.width * 0.35f, y1))
        drawCircle(iconColor, radius = size.width * 0.09f, center = Offset(size.width * 0.65f, y2))
        drawCircle(iconColor, radius = size.width * 0.09f, center = Offset(size.width * 0.45f, y3))
    }
}

@Composable
private fun StatusPanel(
    settings: CameraCaptureSettings,
    stats: CaptureStats,
    isCapturing: Boolean,
    cameraStatus: String,
    networkStatus: String,
    controlStatus: CameraControlStatus,
    initiallyExpanded: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "Status",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = stats.summaryText(isCapturing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Hide" else "Show")
                }
            }

            if (expanded) {
                StatusSection(title = "Capture") {
                    StatusRow("State", if (isCapturing) "running" else "stopped")
                    StatusRow("Camera", cameraStatus)
                    StatusRow("Frame sequence", stats.frameSequence.toString())
                    StatusRow("Last timestamp ms", stats.lastDeviceTimestampMs?.toString() ?: "-")
                    StatusRow("Last monotonic ns", stats.lastDeviceMonotonicNs?.toString() ?: "-")
                    StatusRow("Current FPS", "%.1f".format(stats.currentFps))
                }

                StatusSection(title = "Transport") {
                    StatusRow("Network", networkStatus)
                    StatusRow("Sent / failed", "${stats.sentCount} / ${stats.failedCount}")
                    StatusRow("Dropped frames", stats.droppedFrames.toString())
                }

                StatusSection(title = "Camera Controls") {
                    StatusRow("Focus mode", settings.focusMode)
                    StatusRow("Focus lock", settings.focusLocked.toRequestSummary(
                        support = controlStatus.focusLockSupported,
                        applied = controlStatus.focusLockApplied,
                    ))
                    StatusRow("Exposure lock", settings.exposureLocked.toRequestSummary(
                        support = controlStatus.exposureLockSupported,
                        applied = controlStatus.exposureLockApplied,
                    ))
                    StatusRow("White balance lock", settings.whiteBalanceLocked.toRequestSummary(
                        support = controlStatus.whiteBalanceLockSupported,
                        applied = controlStatus.whiteBalanceLockApplied,
                    ))
                    StatusRow("Zoom disabled", settings.zoomDisabled.toString())
                    StatusRow("Orientation", "${settings.orientationDeg} deg")
                }

                StatusSection(title = "Manual Exposure") {
                    StatusRow("Manual exposure", settings.manualExposureEnabled.toRequestSummary(
                        support = controlStatus.manualExposureSupported,
                        applied = controlStatus.manualExposureApplied,
                    ))
                    StatusRow("ISO req / applied", "${settings.iso} / ${controlStatus.isoApplied ?: "-"}")
                    StatusRow(
                        "Shutter ns req / applied",
                        "${settings.exposureTimeNs} / ${controlStatus.exposureTimeNsApplied ?: "-"}",
                    )
                    StatusRow("Focal length mm", controlStatus.focalLengthMm?.let { "%.2f".format(it) } ?: "-")
                }

                StatusSection(title = "Device Capability") {
                    StatusRow("FPS target", controlStatus.fpsTargetSupported.toMetadataState())
                    StatusRow("Resolution", controlStatus.resolutionSupported.toMetadataState())
                    StatusRow("Manual exposure", controlStatus.manualExposureSupported.toMetadataState())
                    StatusRow("Zoom", controlStatus.zoomSupported.toMetadataState())
                }
            }
        }
    }
}

@Composable
private fun StatusSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
        )
        content()
    }
}

@Composable
private fun StatusRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
        )
    }
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

private fun CameraCaptureSettings.summaryText(): String {
    val exposure = if (manualExposureEnabled) {
        "manual ISO $iso"
    } else {
        "auto exposure"
    }
    return "${resolution.label}, ${fpsTarget} FPS, $exposure, $cameraId, $deviceId"
}

private fun CaptureStats.summaryText(isCapturing: Boolean): String {
    val state = if (isCapturing) "running" else "stopped"
    return "$state, seq $frameSequence, ${"%.1f".format(currentFps)} FPS"
}

private fun Boolean.toRequestSummary(
    support: Boolean?,
    applied: Boolean?,
): String {
    val requested = if (this) "requested" else "not_requested"
    return "$requested / ${support.toMetadataState()} / ${applied.toAppliedState()}"
}

private fun collectorUiStateSaver(): Saver<CollectorUiState, Any> {
    return listSaver(
        save = { state ->
            listOf(
                state.isCapturing,
                state.settings.cameraId,
                state.settings.deviceId,
                state.settings.serverUrl,
                state.settings.resolution.name,
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
            )
        },
        restore = { values ->
            CollectorUiState(
                isCapturing = values[0] as Boolean,
                settings = CameraCaptureSettings(
                    cameraId = values[1] as String,
                    deviceId = values[2] as String,
                    serverUrl = values[3] as String,
                    resolution = ResolutionOption.valueOf(values[4] as String),
                    fpsTarget = (values[5] as Number).toInt(),
                    focusMode = values[6] as String,
                    focusLocked = values[7] as Boolean,
                    exposureLocked = values[8] as Boolean,
                    whiteBalanceLocked = values[9] as Boolean,
                    zoomDisabled = values[10] as Boolean,
                    orientationDeg = (values[11] as Number).toInt(),
                    manualExposureEnabled = values[12] as Boolean,
                    iso = (values[13] as Number).toInt(),
                    exposureTimeNs = (values[14] as Number).toLong(),
                ),
                stats = CaptureStats(
                    frameSequence = (values[15] as Number).toLong(),
                    lastDeviceTimestampMs = (values[16] as Number?)?.toLong(),
                    lastDeviceMonotonicNs = (values[17] as Number?)?.toLong(),
                    sentCount = (values[18] as Number).toLong(),
                    failedCount = (values[19] as Number).toLong(),
                    droppedFrames = (values[20] as Number).toLong(),
                    currentFps = (values[21] as Number).toFloat(),
                ),
                cameraControlStatus = CameraControlStatus(
                    focusLockSupported = values[22] as Boolean?,
                    focusLockApplied = values[23] as Boolean?,
                    exposureLockSupported = values[24] as Boolean?,
                    exposureLockApplied = values[25] as Boolean?,
                    whiteBalanceLockSupported = values[26] as Boolean?,
                    whiteBalanceLockApplied = values[27] as Boolean?,
                    fpsTargetSupported = values[28] as Boolean?,
                    resolutionSupported = values[29] as Boolean?,
                    manualExposureSupported = values[30] as Boolean?,
                    manualExposureApplied = values[31] as Boolean?,
                    isoApplied = (values[32] as Number?)?.toInt(),
                    exposureTimeNsApplied = (values[33] as Number?)?.toLong(),
                    focalLengthMm = (values[34] as Number?)?.toFloat(),
                    zoomSupported = values[35] as Boolean?,
                ),
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
