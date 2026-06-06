package com.gc.collector.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gc.collector.model.CameraCaptureSettings
import com.gc.collector.model.ProcessingAlert
import com.gc.collector.model.ProcessingAlertSeverity
import com.gc.collector.model.UserAlertState
import com.gc.collector.model.UserModeConnectionState
import com.gc.collector.model.UserModeConnectionStatus
import kotlinx.coroutines.delay

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
    KeepScreenOn(enabled = connectionState.enabled)

    if (connectionState.enabled) {
        UserModeActiveScreen(
            connectionState = connectionState,
            alertState = alertState,
            onStop = { onToggleUserMode(false) },
            modifier = modifier,
        )
        return
    }

    UserModeSetupScreen(
        settings = settings,
        canStart = canStart,
        onHttpBaseUrlChange = onHttpBaseUrlChange,
        onDeviceIdChange = onDeviceIdChange,
        onStart = { onToggleUserMode(true) },
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
private fun UserModeSetupScreen(
    settings: CameraCaptureSettings,
    canStart: Boolean,
    onHttpBaseUrlChange: (String) -> Unit,
    onDeviceIdChange: (String) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Use Mode Setup",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            UserModeEndpointSection(
                settings = settings,
                enabled = true,
                onHttpBaseUrlChange = onHttpBaseUrlChange,
                onDeviceIdChange = onDeviceIdChange,
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
                    onClick = onStart,
                    enabled = canStart,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Start")
                }
            }
        }
    }
}

@Composable
private fun UserModeActiveScreen(
    connectionState: UserModeConnectionState,
    alertState: UserAlertState,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(connectionState.enabled, alertState.latestAlert?.expiresAtMs) {
        while (connectionState.enabled) {
            nowMs = System.currentTimeMillis()
            delay(500L)
        }
    }
    val visualState = resolveUserModeActiveVisualState(
        connectionState = connectionState,
        latestAlert = alertState.latestAlert,
        nowMs = nowMs,
    )

    Surface(
        modifier = modifier.fillMaxSize(),
        color = visualState.backgroundColor,
        contentColor = visualState.contentColor,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Use Mode",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                UserModeStatusCircle(visualState = visualState)
            }
            Button(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Stop")
            }
        }
    }
}

@Composable
private fun UserModeStatusCircle(visualState: UserModeActiveVisualState) {
    Surface(
        modifier = Modifier.size(240.dp),
        shape = CircleShape,
        color = visualState.circleColor,
        contentColor = visualState.circleContentColor,
        tonalElevation = 4.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = visualState.label,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = visualState.message,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
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
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 2.dp,
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

private data class UserModeActiveVisualState(
    val label: String,
    val message: String,
    val backgroundColor: Color,
    val contentColor: Color,
    val circleColor: Color,
    val circleContentColor: Color,
)

private fun resolveUserModeActiveVisualState(
    connectionState: UserModeConnectionState,
    latestAlert: ProcessingAlert?,
    nowMs: Long,
): UserModeActiveVisualState {
    val activeAlert = latestAlert?.takeIf { alert -> !alert.isExpired(nowMs) }
    return when (activeAlert?.severity) {
        ProcessingAlertSeverity.Danger -> UserModeActiveVisualState(
            label = "DANGER",
            message = activeAlert.distanceM?.let { distance -> "%.2f m".format(distance) } ?: "Alert",
            backgroundColor = Color(0xFF1B1B1B),
            contentColor = Color.White,
            circleColor = Color(0xFFD91E18),
            circleContentColor = Color.White,
        )

        ProcessingAlertSeverity.Warning -> UserModeActiveVisualState(
            label = "WARNING",
            message = activeAlert.distanceM?.let { distance -> "%.2f m".format(distance) } ?: "Alert",
            backgroundColor = Color(0xFF1B1B1B),
            contentColor = Color.White,
            circleColor = Color(0xFFD91E18),
            circleContentColor = Color.White,
        )

        ProcessingAlertSeverity.Info -> UserModeActiveVisualState(
            label = "ON",
            message = "Listening",
            backgroundColor = Color(0xFF1B1B1B),
            contentColor = Color.White,
            circleColor = Color(0xFF1E8E3E),
            circleContentColor = Color.White,
        )

        null -> connectionState.toActiveVisualState()
    }
}

private fun UserModeConnectionState.toActiveVisualState(): UserModeActiveVisualState {
    return when (status) {
        UserModeConnectionStatus.Connected -> UserModeActiveVisualState(
            label = "ON",
            message = "Listening",
            backgroundColor = Color(0xFF1B1B1B),
            contentColor = Color.White,
            circleColor = Color(0xFF1E8E3E),
            circleContentColor = Color.White,
        )

        UserModeConnectionStatus.Connecting,
        UserModeConnectionStatus.Reconnecting -> UserModeActiveVisualState(
            label = "WAIT",
            message = "Connecting",
            backgroundColor = Color(0xFF1B1B1B),
            contentColor = Color.White,
            circleColor = Color(0xFF6B7280),
            circleContentColor = Color.White,
        )

        UserModeConnectionStatus.Error -> UserModeActiveVisualState(
            label = "ERROR",
            message = "Connection",
            backgroundColor = Color(0xFF1B1B1B),
            contentColor = Color.White,
            circleColor = Color(0xFFB3261E),
            circleContentColor = Color.White,
        )

        UserModeConnectionStatus.Idle,
        UserModeConnectionStatus.Stopped -> UserModeActiveVisualState(
            label = "OFF",
            message = "Stopped",
            backgroundColor = Color(0xFF1B1B1B),
            contentColor = Color.White,
            circleColor = Color(0xFF6B7280),
            circleContentColor = Color.White,
        )
    }
}
