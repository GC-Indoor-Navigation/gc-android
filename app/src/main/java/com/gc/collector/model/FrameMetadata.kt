package com.gc.collector.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FrameMetadata(
    @SerialName("camera_id")
    val cameraId: String,
    @SerialName("device_id")
    val deviceId: String,
    @SerialName("frame_sequence")
    val frameSequence: Long,
    @SerialName("device_timestamp_ms")
    val deviceTimestampMs: Long,
    @SerialName("device_monotonic_ns")
    val deviceMonotonicNs: Long,
    val width: Int,
    val height: Int,
    val format: String = "jpeg",
    @SerialName("fps_target")
    val fpsTarget: Int,
    @SerialName("focus_mode")
    val focusMode: String,
    @SerialName("focus_locked")
    val focusLocked: Boolean,
    @SerialName("exposure_locked")
    val exposureLocked: Boolean,
    @SerialName("white_balance_locked")
    val whiteBalanceLocked: Boolean,
    @SerialName("zoom_disabled")
    val zoomDisabled: Boolean,
    @SerialName("orientation_deg")
    val orientationDeg: Int,
)
