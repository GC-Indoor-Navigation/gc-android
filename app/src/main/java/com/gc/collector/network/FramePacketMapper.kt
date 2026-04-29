package com.gc.collector.network

import com.gc.collector.camera.CapturedFrame

fun CapturedFrame.toFramePacket(): GrpcFramePacket {
    val source = metadata
    return GrpcFramePacket(
        metadata = GrpcFrameMetadata(
            cameraId = source.cameraId,
            deviceId = source.deviceId,
            frameSequence = source.frameSequence,
            deviceTimestampMs = source.deviceTimestampMs,
            deviceMonotonicNs = source.deviceMonotonicNs,
            width = source.width,
            height = source.height,
            format = source.format,
            fpsTarget = source.fpsTarget,
            focusMode = source.focusMode,
            focusLocked = source.focusLocked,
            exposureLocked = source.exposureLocked,
            whiteBalanceLocked = source.whiteBalanceLocked,
            zoomDisabled = source.zoomDisabled,
            orientationDeg = source.orientationDeg,
            sensorTimestampNs = sensorTimestampNs,
        ),
        jpegBytes = jpegBytes,
    )
}
