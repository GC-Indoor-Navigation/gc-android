package com.gc.collector.ui.camera

private const val oneSecondNs = 1_000_000_000L
private const val maxEarlyToleranceNs = 5_000_000L
private const val noTimestampNs = Long.MIN_VALUE

internal class RuntimeFrameRateLimiter {
    private var nextAllowedSensorTimestampNs = noTimestampNs

    @Synchronized
    fun shouldEmit(
        sensorTimestampNs: Long,
        targetFps: Int,
    ): Boolean {
        if (targetFps <= 0) return true

        val targetIntervalNs = oneSecondNs / targetFps
        val earlyToleranceNs = minOf(targetIntervalNs / 10L, maxEarlyToleranceNs)
        if (nextAllowedSensorTimestampNs == noTimestampNs) {
            nextAllowedSensorTimestampNs = sensorTimestampNs + targetIntervalNs
            return true
        }

        if (sensorTimestampNs + earlyToleranceNs < nextAllowedSensorTimestampNs) {
            return false
        }

        do {
            nextAllowedSensorTimestampNs += targetIntervalNs
        } while (nextAllowedSensorTimestampNs <= sensorTimestampNs)

        return true
    }

    @Synchronized
    fun reset() {
        nextAllowedSensorTimestampNs = noTimestampNs
    }

    @Synchronized
    fun nextAllowedSensorTimestampNsForTest(): Long {
        return nextAllowedSensorTimestampNs
    }
}
