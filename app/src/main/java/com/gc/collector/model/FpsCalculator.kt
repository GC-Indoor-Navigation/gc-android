package com.gc.collector.model

data class FpsCalculationResult(
    val currentFps: Float,
    val windowStartedNs: Long,
    val framesInWindow: Int,
)

object FpsCalculator {
    private const val OneSecondNs = 1_000_000_000L

    fun calculate(
        sensorTimestampNs: Long,
        lastWindowStartedNs: Long?,
        framesInWindow: Int,
        previousFps: Float,
    ): FpsCalculationResult {
        val startedNs = lastWindowStartedNs
        if (startedNs == null) {
            return FpsCalculationResult(
                currentFps = previousFps,
                windowStartedNs = sensorTimestampNs,
                framesInWindow = 1,
            )
        }

        val elapsedNs = sensorTimestampNs - startedNs
        val nextFrameCount = framesInWindow + 1
        if (elapsedNs >= OneSecondNs) {
            return FpsCalculationResult(
                currentFps = nextFrameCount * OneSecondNs.toFloat() / elapsedNs,
                windowStartedNs = sensorTimestampNs,
                framesInWindow = 0,
            )
        }

        return FpsCalculationResult(
            currentFps = previousFps,
            windowStartedNs = startedNs,
            framesInWindow = nextFrameCount,
        )
    }
}
