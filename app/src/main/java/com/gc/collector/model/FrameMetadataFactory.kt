package com.gc.collector.model

import android.os.SystemClock

object FrameMetadataFactory {
    fun create(
        settings: CameraCaptureSettings,
        controlStatus: CameraControlStatus,
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
            focusLockRequested = settings.focusLocked,
            focusLockSupport = controlStatus.focusLockSupported.toMetadataState(),
            focusLockApplied = controlStatus.focusLockApplied.toAppliedState(),
            exposureLockRequested = settings.exposureLocked,
            exposureLockSupport = controlStatus.exposureLockSupported.toMetadataState(),
            exposureLockApplied = controlStatus.exposureLockApplied.toAppliedState(),
            whiteBalanceLockRequested = settings.whiteBalanceLocked,
            whiteBalanceLockSupport = controlStatus.whiteBalanceLockSupported.toMetadataState(),
            whiteBalanceLockApplied = controlStatus.whiteBalanceLockApplied.toAppliedState(),
            fpsTargetSupport = controlStatus.fpsTargetSupported.toMetadataState(),
            resolutionSupport = controlStatus.resolutionSupported.toMetadataState(),
            manualExposureSupport = controlStatus.manualExposureSupported.toMetadataState(),
            manualExposureRequested = settings.manualExposureEnabled,
            manualExposureApplied = controlStatus.manualExposureApplied.toAppliedState(),
            isoRequested = settings.iso,
            isoApplied = controlStatus.isoApplied,
            exposureTimeNsRequested = settings.exposureTimeNs,
            exposureTimeNsApplied = controlStatus.exposureTimeNsApplied,
            focalLengthMm = controlStatus.focalLengthMm,
        )
    }
}
