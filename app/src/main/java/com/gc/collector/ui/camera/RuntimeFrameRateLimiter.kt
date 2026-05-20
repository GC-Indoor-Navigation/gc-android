package com.gc.collector.ui.camera

import java.util.concurrent.atomic.AtomicLong

private const val oneSecondNs = 1_000_000_000L
private const val noTimestampNs = Long.MIN_VALUE

internal class RuntimeFrameRateLimiter {
    private val lastEmittedSensorTimestampNs = AtomicLong(noTimestampNs)

    fun shouldEmit(
        sensorTimestampNs: Long,
        targetFps: Int,
    ): Boolean {
        if (targetFps <= 0) return true

        val minimumIntervalNs = oneSecondNs / targetFps
        while (true) {
            val lastTimestampNs = lastEmittedSensorTimestampNs.get()
            if (lastTimestampNs == noTimestampNs || sensorTimestampNs <= lastTimestampNs) {
                return lastEmittedSensorTimestampNs.compareAndSet(lastTimestampNs, sensorTimestampNs)
            }

            if (sensorTimestampNs - lastTimestampNs < minimumIntervalNs) {
                return false
            }

            if (lastEmittedSensorTimestampNs.compareAndSet(lastTimestampNs, sensorTimestampNs)) {
                return true
            }
        }
    }

    fun reset() {
        lastEmittedSensorTimestampNs.set(noTimestampNs)
    }
}
