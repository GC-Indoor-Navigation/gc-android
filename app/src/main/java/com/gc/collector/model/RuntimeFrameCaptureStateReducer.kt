package com.gc.collector.model

data class RuntimeFrameCaptureState(
    val stats: CaptureStats,
    val fpsWindowStartedNs: Long?,
    val framesInWindow: Int,
)

object RuntimeFrameCaptureStateReducer {
    fun applyCapturedFrame(
        stats: CaptureStats,
        metadata: FrameMetadata,
        sensorTimestampNs: Long,
        lastWindowStartedNs: Long?,
        framesInWindow: Int,
    ): RuntimeFrameCaptureState {
        val fpsResult = FpsCalculator.calculate(
            sensorTimestampNs = sensorTimestampNs,
            lastWindowStartedNs = lastWindowStartedNs,
            framesInWindow = framesInWindow,
            previousFps = stats.currentFps,
        )

        return RuntimeFrameCaptureState(
            stats = stats.copy(
                frameSequence = metadata.frameSequence,
                lastDeviceTimestampMs = metadata.deviceTimestampMs,
                lastDeviceMonotonicNs = metadata.deviceMonotonicNs,
                currentFps = fpsResult.currentFps,
            ),
            fpsWindowStartedNs = fpsResult.windowStartedNs,
            framesInWindow = fpsResult.framesInWindow,
        )
    }
}
