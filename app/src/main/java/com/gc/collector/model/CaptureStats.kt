package com.gc.collector.model

import kotlinx.serialization.Serializable

@Serializable
data class CaptureStats(
    val frameSequence: Long = 0L,
    val lastDeviceTimestampMs: Long? = null,
    val lastDeviceMonotonicNs: Long? = null,
    val sentCount: Long = 0L,
    val failedCount: Long = 0L,
    val droppedFrames: Long = 0L,
    val currentFps: Float = 0f,
)
