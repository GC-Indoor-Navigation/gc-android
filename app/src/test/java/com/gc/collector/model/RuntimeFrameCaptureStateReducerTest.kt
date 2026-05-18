package com.gc.collector.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeFrameCaptureStateReducerTest {
    @Test
    fun firstCapturedFrameUpdatesMetadataAndStartsFpsWindow() {
        val next = RuntimeFrameCaptureStateReducer.applyCapturedFrame(
            stats = CaptureStats(
                frameSequence = 1L,
                lastDeviceTimestampMs = 2L,
                lastDeviceMonotonicNs = 3L,
                sentCount = 10L,
                failedCount = 1L,
                droppedFrames = 2L,
                currentFps = 9.5f,
            ),
            metadata = sampleMetadata(frameSequence = 77L),
            sensorTimestampNs = 123_000L,
            lastWindowStartedNs = null,
            framesInWindow = 0,
        )

        assertEquals(77L, next.stats.frameSequence)
        assertEquals(1_775_404_088_703L, next.stats.lastDeviceTimestampMs)
        assertEquals(8_234_567_812_345L, next.stats.lastDeviceMonotonicNs)
        assertEquals(10L, next.stats.sentCount)
        assertEquals(1L, next.stats.failedCount)
        assertEquals(2L, next.stats.droppedFrames)
        assertEquals(9.5f, next.stats.currentFps, 0.0001f)
        assertEquals(123_000L, next.fpsWindowStartedNs)
        assertEquals(1, next.framesInWindow)
    }

    @Test
    fun capturedFrameAtOneSecondCalculatesFpsAndResetsWindow() {
        val next = RuntimeFrameCaptureStateReducer.applyCapturedFrame(
            stats = CaptureStats(currentFps = 0f),
            metadata = sampleMetadata(frameSequence = 10L),
            sensorTimestampNs = 1_000_000_000L,
            lastWindowStartedNs = 0L,
            framesInWindow = 9,
        )

        assertEquals(10L, next.stats.frameSequence)
        assertEquals(10f, next.stats.currentFps, 0.0001f)
        assertEquals(1_000_000_000L, next.fpsWindowStartedNs)
        assertEquals(0, next.framesInWindow)
    }

    private fun sampleMetadata(frameSequence: Long): FrameMetadata {
        return FrameMetadata(
            cameraId = "camera_01",
            deviceId = "device_01",
            frameSequence = frameSequence,
            sessionId = "session_01",
            deviceTimestampMs = 1_775_404_088_703L,
            deviceMonotonicNs = 8_234_567_812_345L,
            width = 1280,
            height = 720,
            fpsTarget = 10,
            focusMode = "locked",
            focusLocked = true,
            exposureLocked = true,
            whiteBalanceLocked = true,
            zoomDisabled = true,
            orientationDeg = 90,
            focusLockRequested = true,
            focusLockSupport = "supported",
            focusLockApplied = "applied",
            exposureLockRequested = true,
            exposureLockSupport = "supported",
            exposureLockApplied = "applied",
            whiteBalanceLockRequested = true,
            whiteBalanceLockSupport = "supported",
            whiteBalanceLockApplied = "applied",
            fpsTargetSupport = "supported",
            resolutionSupport = "supported",
            manualExposureSupport = "supported",
            manualExposureRequested = false,
            manualExposureApplied = "not_applied",
            isoRequested = 400,
            isoApplied = null,
            exposureTimeNsRequested = 10_000_000L,
            exposureTimeNsApplied = null,
            focalLengthMm = null,
        )
    }
}
