package com.gc.collector.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun CaptureOverlayControls(
    isCapturing: Boolean,
    isDetailsOpen: Boolean,
    isLandscape: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onSingleCapture: () -> Unit,
    singleCaptureEnabled: Boolean,
    singleCaptureInProgress: Boolean,
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
            SingleCaptureButton(
                onClick = onSingleCapture,
                size = buttonSize,
                enabled = singleCaptureEnabled,
                inProgress = singleCaptureInProgress,
            )
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
            SingleCaptureButton(
                onClick = onSingleCapture,
                size = buttonSize,
                enabled = singleCaptureEnabled,
                inProgress = singleCaptureInProgress,
            )
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
private fun SingleCaptureButton(
    onClick: () -> Unit,
    size: Dp,
    enabled: Boolean,
    inProgress: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = { if (enabled) onClick() },
        modifier = modifier.size(size),
        shape = CircleShape,
        color = when {
            !enabled -> Color.White.copy(alpha = 0.36f)
            inProgress -> Color.White.copy(alpha = 0.76f)
            else -> Color.White.copy(alpha = 0.92f)
        },
        contentColor = Color.Black,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.55f)),
        shadowElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            if (inProgress) {
                Canvas(modifier = Modifier.size(size * 0.58f)) {
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.42f),
                        radius = this.size.width * 0.28f,
                        center = Offset(this.size.width * 0.5f, this.size.height * 0.5f),
                    )
                }
            }
        }
    }
}

@Composable
private fun TransparentCircleButton(
    onClick: () -> Unit,
    size: Dp,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = { if (enabled) onClick() },
        modifier = modifier.size(size),
        shape = CircleShape,
        color = if (active) {
            Color(0xCC1D4ED8)
        } else if (!enabled) {
            Color(0x33000000)
        } else {
            Color(0x66000000)
        },
        contentColor = if (enabled) Color.White else Color.White.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = if (enabled) 0.42f else 0.20f)),
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
