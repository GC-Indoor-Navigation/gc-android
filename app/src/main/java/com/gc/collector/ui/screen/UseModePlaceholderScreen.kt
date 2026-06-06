package com.gc.collector.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gc.collector.model.CameraCaptureSettings
import com.gc.collector.model.UserAlertState
import com.gc.collector.model.UserModeConnectionState
import com.gc.collector.model.UserModeConnectionStatus

@Composable
fun UseModeScreen(
    settings: CameraCaptureSettings,
    connectionState: UserModeConnectionState,
    alertState: UserAlertState,
    onHttpBaseUrlChange: (String) -> Unit,
    onDeviceIdChange: (String) -> Unit,
    onToggleUserMode: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canStart = settings.calibrationHttpBaseUrl.isNotBlank() && settings.deviceId.isNotBlank()
    val controlsEnabled = !connectionState.enabled

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Use Mode",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        UserModeToggleSection(
            connectionState = connectionState,
            enabled = connectionState.enabled || canStart,
            onToggleUserMode = onToggleUserMode,
        )
        UserModeEndpointSection(
            settings = settings,
            enabled = controlsEnabled,
            onHttpBaseUrlChange = onHttpBaseUrlChange,
            onDeviceIdChange = onDeviceIdChange,
        )
        UserModeConnectionSection(connectionState = connectionState)
        UserModeAlertSection(alertState = alertState)
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
                onClick = { onToggleUserMode(!connectionState.enabled) },
                enabled = connectionState.enabled || canStart,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (connectionState.enabled) "Stop" else "Start")
            }
        }
    }
}

@Composable
private fun UserModeToggleSection(
    connectionState: UserModeConnectionState,
    enabled: Boolean,
    onToggleUserMode: (Boolean) -> Unit,
) {
    Surface(
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = "Alert Listener",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = connectionState.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = connectionState.enabled,
                onCheckedChange = onToggleUserMode,
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun UserModeEndpointSection(
    settings: CameraCaptureSettings,
    enabled: Boolean,
    onHttpBaseUrlChange: (String) -> Unit,
    onDeviceIdChange: (String) -> Unit,
) {
    UserModeSection(title = "Endpoint") {
        OutlinedTextField(
            value = settings.calibrationHttpBaseUrl,
            onValueChange = onHttpBaseUrlChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = true,
            label = { Text("Stream Server HTTP URL") },
        )
        OutlinedTextField(
            value = settings.deviceId,
            onValueChange = onDeviceIdChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = true,
            label = { Text("Device ID") },
        )
        UserModeRow(label = "SSE path", value = "/phone/alerts/events")
    }
}

@Composable
private fun UserModeConnectionSection(connectionState: UserModeConnectionState) {
    UserModeSection(title = "Connection") {
        UserModeRow(label = "State", value = connectionState.status.displayName())
        UserModeRow(label = "Attempt", value = connectionState.connectAttempt.toString())
        UserModeRow(label = "Reconnects", value = connectionState.reconnectCount.toString())
        UserModeRow(label = "Last connected", value = connectionState.lastConnectedAtMs?.toString() ?: "-")
        UserModeRow(label = "Last error", value = connectionState.lastError ?: "-")
    }
}

@Composable
private fun UserModeAlertSection(alertState: UserAlertState) {
    val latestAlert = alertState.latestAlert
    UserModeSection(title = "Latest Alert") {
        UserModeRow(label = "Status", value = alertState.status)
        UserModeRow(label = "Received", value = alertState.receivedCount.toString())
        UserModeRow(label = "Expired", value = alertState.expiredCount.toString())
        UserModeRow(label = "Duplicate", value = alertState.duplicateCount.toString())
        UserModeRow(label = "Parse failed", value = alertState.parseFailureCount.toString())
        UserModeRow(label = "Event ID", value = latestAlert?.eventId ?: "-")
        UserModeRow(label = "Severity", value = latestAlert?.severity?.name?.lowercase() ?: "-")
        UserModeRow(label = "Distance", value = latestAlert?.distanceM?.toString() ?: "-")
        UserModeRow(label = "Joint", value = latestAlert?.joint ?: "-")
        UserModeRow(label = "Obstacle", value = latestAlert?.obstacleId ?: "-")
    }
}

@Composable
private fun UserModeSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            content()
        }
    }
}

@Composable
private fun UserModeRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.45f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.55f),
        )
    }
}

private fun UserModeConnectionStatus.displayName(): String {
    return when (this) {
        UserModeConnectionStatus.Idle -> "idle"
        UserModeConnectionStatus.Connecting -> "connecting"
        UserModeConnectionStatus.Connected -> "connected"
        UserModeConnectionStatus.Reconnecting -> "reconnecting"
        UserModeConnectionStatus.Stopped -> "stopped"
        UserModeConnectionStatus.Error -> "error"
    }
}
