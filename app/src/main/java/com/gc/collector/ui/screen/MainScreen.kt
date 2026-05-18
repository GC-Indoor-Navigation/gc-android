package com.gc.collector.ui.screen

import android.Manifest
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker.PERMISSION_GRANTED
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.gc.collector.model.ResolutionOption
import com.gc.collector.model.toAppliedState
import com.gc.collector.ui.camera.loadBackCameraResolutionOptions
import com.gc.collector.ui.theme.GcandroidTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class CollectorScreen {
    ModeSelection,
    CameraSetup,
    CameraCapture,
    UseMode,
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    providedCollectorViewModel: CollectorViewModel? = null,
) {
    val context = LocalContext.current
    val collectorViewModel = providedCollectorViewModel ?: remember(context) {
        val owner = context as? ViewModelStoreOwner
        if (owner != null) {
            ViewModelProvider(owner)[CollectorViewModel::class.java]
        } else {
            CollectorViewModel()
        }
    }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    var currentScreenName by rememberSaveable { mutableStateOf(CollectorScreen.ModeSelection.name) }
    val screenState by collectorViewModel.screenState.collectAsState()
    val uiState = screenState.collectorUiState
    val currentScreen = CollectorScreen.valueOf(currentScreenName)
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PERMISSION_GRANTED,
        )
    }
    var resolutionOptions by remember { mutableStateOf(ResolutionOption.commonOptions) }
    var resolutionOptionsStatus by rememberSaveable { mutableStateOf("common resolution presets") }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
            collectorViewModel.onCameraPermissionResult(granted)
        },
    )
    val settings = uiState.settings

    BackHandler(enabled = currentScreen == CollectorScreen.CameraSetup || currentScreen == CollectorScreen.UseMode) {
        currentScreenName = CollectorScreen.ModeSelection.name
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
                    collectorViewModel.updateCollectorUiState { state ->
                        state.copy(settings = state.settings.copy(resolution = loadedOptions.chooseFallbackResolution()))
                    }
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
                onSettingsChange = { updated ->
                    collectorViewModel.updateCollectorUiState { state -> state.copy(settings = updated) }
                },
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

    CameraCaptureRoute(
        modifier = modifier,
        screenState = screenState,
        hasCameraPermission = hasCameraPermission,
        isLandscape = isLandscape,
        collectorViewModel = collectorViewModel,
        onRequestCameraPermission = {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        },
        onExitCapture = { currentScreenName = CollectorScreen.CameraSetup.name },
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
        MainScreen(providedCollectorViewModel = CollectorViewModel())
    }
}
