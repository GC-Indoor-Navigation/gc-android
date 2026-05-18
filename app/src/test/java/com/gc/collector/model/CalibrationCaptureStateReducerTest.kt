package com.gc.collector.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationCaptureStateReducerTest {
    @Test
    fun requestCaptureStartsUploadAndIncrementsRequestId() {
        val next = CalibrationCaptureStateReducer.requestCapture(
            CalibrationCaptureState(
                status = "Calibration idle",
                captureRequestId = 4L,
                uploadInProgress = false,
            ),
        )

        assertEquals("Calibration capture requested", next.status)
        assertEquals(5L, next.captureRequestId)
        assertTrue(next.uploadInProgress)
    }

    @Test
    fun applyCapturedFrameCopiesMetadataIntoStats() {
        val stats = CalibrationCaptureStateReducer.applyCapturedFrame(
            stats = CaptureStats(
                frameSequence = 1L,
                lastDeviceTimestampMs = 2L,
                lastDeviceMonotonicNs = 3L,
                sentCount = 10L,
                failedCount = 1L,
                droppedFrames = 2L,
                currentFps = 9.5f,
            ),
            metadata = sampleMetadata(),
        )

        assertEquals(77L, stats.frameSequence)
        assertEquals(1_775_404_088_703L, stats.lastDeviceTimestampMs)
        assertEquals(8_234_567_812_345L, stats.lastDeviceMonotonicNs)
        assertEquals(10L, stats.sentCount)
        assertEquals(1L, stats.failedCount)
        assertEquals(2L, stats.droppedFrames)
        assertEquals(9.5f, stats.currentFps, 0.0001f)
    }

    @Test
    fun completeUploadSuccessStopsUploadAndSetsStatus() {
        val next = CalibrationCaptureStateReducer.completeUpload(
            state = CalibrationCaptureState(
                status = "Calibration capture requested",
                captureRequestId = 3L,
                uploadInProgress = true,
            ),
            frameSequence = 77L,
            outcome = CalibrationUploadOutcome.Uploaded,
        )

        assertEquals("Calibration uploaded: 77", next.status)
        assertEquals(3L, next.captureRequestId)
        assertFalse(next.uploadInProgress)
    }

    @Test
    fun completeUploadFailureStopsUploadAndSetsStatus() {
        val next = CalibrationCaptureStateReducer.completeUpload(
            state = CalibrationCaptureState(
                status = "Calibration capture requested",
                captureRequestId = 3L,
                uploadInProgress = true,
            ),
            frameSequence = 77L,
            outcome = CalibrationUploadOutcome.Failed("HTTP 500"),
        )

        assertEquals("Calibration failed: HTTP 500", next.status)
        assertEquals(3L, next.captureRequestId)
        assertFalse(next.uploadInProgress)
    }

    private fun sampleMetadata(): FrameMetadata {
        return FrameMetadata(
            cameraId = "camera_01",
            deviceId = "device_01",
            frameSequence = 77L,
            sessionId = null,
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
