package com.gc.collector.model

import android.os.SystemClock

object FrameMetadataFactory {
    fun create(
        settings: CameraCaptureSettings,
        frameSequence: Long,
        width: Int,
        height: Int,
    ): FrameMetadata {
        return FrameMetadata(
            cameraId = settings.cameraId,
            deviceId = settings.deviceId,
            frameSequence = frameSequence,
            deviceTimestampMs = System.currentTimeMillis(),
            deviceMonotonicNs = SystemClock.elapsedRealtimeNanos(),
            width = width,
            height = height,
            fpsTarget = settings.fpsTarget,
            focusMode = if (settings.focusLocked) "locked" else "auto",
            focusLocked = settings.focusLocked,
            exposureLocked = settings.exposureLocked,
            whiteBalanceLocked = settings.whiteBalanceLocked,
            zoomDisabled = settings.zoomDisabled,
            orientationDeg = settings.orientationDeg,
        )
    }
}
