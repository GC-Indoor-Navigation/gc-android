package com.gc.collector.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gc.collector.model.CameraCaptureSettings
import com.gc.collector.model.CameraControlStatus
import com.gc.collector.model.CaptureStats
import com.gc.collector.model.toMetadataState

@Composable
fun StatusPanel(
    settings: CameraCaptureSettings,
    stats: CaptureStats,
    sessionId: String?,
    isCapturing: Boolean,
    cameraStatus: String,
    networkStatus: String,
    calibrationStatus: String,
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
                    StatusRow("Session ID", sessionId ?: "-")
                    StatusRow("Camera", cameraStatus)
                    StatusRow("Frame sequence", stats.frameSequence.toString())
                    StatusRow("Last timestamp ms", stats.lastDeviceTimestampMs?.toString() ?: "-")
                    StatusRow("Last monotonic ns", stats.lastDeviceMonotonicNs?.toString() ?: "-")
                    StatusRow("Current FPS", "%.1f".format(stats.currentFps))
                }

                StatusSection(title = "Transport") {
                    StatusRow("Network", networkStatus)
                    StatusRow("Calibration upload", calibrationStatus)
                    StatusRow("Sent / failed", "${stats.sentCount} / ${stats.failedCount}")
                    StatusRow("Dropped frames", stats.droppedFrames.toString())
                }

                StatusSection(title = "Camera Controls") {
                    StatusRow("Focus mode", settings.focusMode)
                    ControlStatusRow(
                        label = "Focus lock",
                        requested = settings.focusLocked,
                        supported = controlStatus.focusLockSupported,
                        applied = controlStatus.focusLockApplied,
                    )
                    ControlStatusRow(
                        label = "Exposure lock",
                        requested = settings.exposureLocked,
                        supported = controlStatus.exposureLockSupported,
                        applied = controlStatus.exposureLockApplied,
                    )
                    ControlStatusRow(
                        label = "White balance lock",
                        requested = settings.whiteBalanceLocked,
                        supported = controlStatus.whiteBalanceLockSupported,
                        applied = controlStatus.whiteBalanceLockApplied,
                    )
                    StatusRow("Zoom disabled", settings.zoomDisabled.toString())
                    StatusRow("Orientation", "${settings.orientationDeg} deg")
                }

                StatusSection(title = "Manual Exposure") {
                    ControlStatusRow(
                        label = "Manual exposure",
                        requested = settings.manualExposureEnabled,
                        supported = controlStatus.manualExposureSupported,
                        applied = controlStatus.manualExposureApplied,
                    )
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
private fun ControlStatusRow(
    label: String,
    requested: Boolean,
    supported: Boolean?,
    applied: Boolean?,
) {
    StatusRow(
        label,
        cameraControlDisplayState(
            requested = requested,
            supported = supported,
            applied = applied,
        ),
    )
}

private fun cameraControlDisplayState(
    requested: Boolean,
    supported: Boolean?,
    applied: Boolean?,
): String {
    return when {
        supported == false -> "unsupported"
        !requested -> "off"
        applied == true -> "applied"
        applied == false -> "not applied"
        else -> "checking"
    }
}

private fun CaptureStats.summaryText(isCapturing: Boolean): String {
    val state = if (isCapturing) "running" else "stopped"
    return "$state, seq $frameSequence, ${"%.1f".format(currentFps)} FPS"
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
