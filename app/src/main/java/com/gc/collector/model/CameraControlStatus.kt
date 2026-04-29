package com.gc.collector.model

import kotlinx.serialization.Serializable

@Serializable
data class CameraControlStatus(
    val focusLockSupported: Boolean? = null,
    val focusLockApplied: Boolean? = null,
    val exposureLockSupported: Boolean? = null,
    val exposureLockApplied: Boolean? = null,
    val whiteBalanceLockSupported: Boolean? = null,
    val whiteBalanceLockApplied: Boolean? = null,
    val fpsTargetSupported: Boolean? = null,
    val resolutionSupported: Boolean? = null,
    val manualExposureSupported: Boolean? = null,
    val manualExposureApplied: Boolean? = null,
    val isoApplied: Int? = null,
    val exposureTimeNsApplied: Long? = null,
    val focalLengthMm: Float? = null,
    val zoomSupported: Boolean? = null,
)

fun Boolean?.toMetadataState(): String {
    return when (this) {
        true -> "supported"
        false -> "unsupported"
        null -> "unknown"
    }
}

fun Boolean?.toAppliedState(): String {
    return when (this) {
        true -> "applied"
        false -> "not_applied"
        null -> "unknown"
    }
}
