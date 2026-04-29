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
    @SerialName("focus_lock_requested")
    val focusLockRequested: Boolean,
    @SerialName("focus_lock_support")
    val focusLockSupport: String,
    @SerialName("focus_lock_applied")
    val focusLockApplied: String,
    @SerialName("exposure_lock_requested")
    val exposureLockRequested: Boolean,
    @SerialName("exposure_lock_support")
    val exposureLockSupport: String,
    @SerialName("exposure_lock_applied")
    val exposureLockApplied: String,
    @SerialName("white_balance_lock_requested")
    val whiteBalanceLockRequested: Boolean,
    @SerialName("white_balance_lock_support")
    val whiteBalanceLockSupport: String,
    @SerialName("white_balance_lock_applied")
    val whiteBalanceLockApplied: String,
    @SerialName("fps_target_support")
    val fpsTargetSupport: String,
    @SerialName("resolution_support")
    val resolutionSupport: String,
    @SerialName("manual_exposure_support")
    val manualExposureSupport: String,
    @SerialName("manual_exposure_requested")
    val manualExposureRequested: Boolean,
    @SerialName("manual_exposure_applied")
    val manualExposureApplied: String,
    @SerialName("iso_requested")
    val isoRequested: Int,
    @SerialName("iso_applied")
    val isoApplied: Int?,
    @SerialName("exposure_time_ns_requested")
    val exposureTimeNsRequested: Long,
    @SerialName("exposure_time_ns_applied")
    val exposureTimeNsApplied: Long?,
    @SerialName("focal_length_mm")
    val focalLengthMm: Float?,
)
