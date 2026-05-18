package com.gc.collector.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gc.collector.model.CameraCaptureSettings
import com.gc.collector.model.ResolutionOption

private val fpsOptions = listOf(5, 10, 15, 30)

@Composable
fun SettingsPanel(
    settings: CameraCaptureSettings,
    resolutionOptions: List<ResolutionOption> = ResolutionOption.commonOptions,
    resolutionOptionsStatus: String? = null,
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
                OutlinedTextField(
                    value = settings.calibrationHttpBaseUrl,
                    onValueChange = { onSettingsChange(settings.copy(calibrationHttpBaseUrl = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isCapturing,
                    singleLine = true,
                    label = { Text("Calibration HTTP URL") },
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

                ResolutionSelector(
                    selectedResolution = settings.resolution,
                    resolutionOptions = resolutionOptions,
                    resolutionOptionsStatus = resolutionOptionsStatus,
                    enabled = !isCapturing,
                    onResolutionChange = { option -> onSettingsChange(settings.copy(resolution = option)) },
                )

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
private fun ResolutionSelector(
    selectedResolution: ResolutionOption,
    resolutionOptions: List<ResolutionOption>,
    resolutionOptionsStatus: String?,
    enabled: Boolean,
    onResolutionChange: (ResolutionOption) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Resolution",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = { expanded = !expanded },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(selectedResolution.label)
                Text(
                    text = if (expanded) "Hide" else "Change",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        resolutionOptionsStatus?.let { status ->
            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(visible = expanded && enabled) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 1.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(8.dp),
            ) {
                Column(
                    modifier = Modifier
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    resolutionOptions.forEach { option ->
                        val selected = option == selectedResolution
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    } else {
                                        Color.Transparent
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                )
                                .clickable {
                                    onResolutionChange(option)
                                    expanded = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                            if (selected) {
                                Text(
                                    text = "Selected",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                    }
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

private fun CameraCaptureSettings.summaryText(): String {
    val exposure = if (manualExposureEnabled) {
        "manual ISO $iso"
    } else {
        "auto exposure"
    }
    return "${resolution.label}, ${fpsTarget} FPS, $exposure, $cameraId, $deviceId"
}
