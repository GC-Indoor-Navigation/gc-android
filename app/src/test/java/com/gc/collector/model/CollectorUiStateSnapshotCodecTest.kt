package com.gc.collector.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CollectorUiStateSnapshotCodecTest {
    @Test
    fun encodesExpectedSnapshotFieldOrder() {
        val state = sampleState()

        val encoded = CollectorUiStateSnapshotCodec.encode(state)

        assertEquals(40, encoded.size)
        assertEquals(true, encoded[0])
        assertEquals("camera_02", encoded[1])
        assertEquals("android_02", encoded[2])
        assertEquals("10.0.0.1:50051", encoded[3])
        assertEquals("1920 x 1080", encoded[4])
        assertEquals(1920, encoded[5])
        assertEquals(1080, encoded[6])
        assertEquals(15, encoded[7])
        assertEquals("locked", encoded[8])
        assertEquals("session_01", encoded[38])
        assertEquals("http://10.0.0.1:8080", encoded[39])
    }

    @Test
    fun decodesEncodedSnapshotWithoutLosingState() {
        val state = sampleState()

        val decoded = CollectorUiStateSnapshotCodec.decode(
            CollectorUiStateSnapshotCodec.encode(state),
        )

        assertEquals(state, decoded)
    }

    @Test
    fun decodesLegacySnapshotWithoutCalibrationHttpBaseUrl() {
        val legacyEncoded = CollectorUiStateSnapshotCodec.encode(sampleState()).dropLast(1)

        val decoded = CollectorUiStateSnapshotCodec.decode(legacyEncoded)

        assertEquals(CameraCaptureSettings().calibrationHttpBaseUrl, decoded.settings.calibrationHttpBaseUrl)
        assertEquals("session_01", decoded.sessionId)
    }

    private fun sampleState(): CollectorUiState {
        return CollectorUiState(
            isCapturing = true,
            sessionId = "session_01",
            settings = CameraCaptureSettings(
                cameraId = "camera_02",
                deviceId = "android_02",
                serverUrl = "10.0.0.1:50051",
                calibrationHttpBaseUrl = "http://10.0.0.1:8080",
                resolution = ResolutionOption.FULL_HD,
                fpsTarget = 15,
                focusMode = "locked",
                focusLocked = true,
                exposureLocked = true,
                whiteBalanceLocked = true,
                zoomDisabled = true,
                orientationDeg = 270,
                manualExposureEnabled = true,
                iso = 800,
                exposureTimeNs = 20_000_000L,
            ),
            stats = CaptureStats(
                frameSequence = 99L,
                lastDeviceTimestampMs = 1_775_404_088_703L,
                lastDeviceMonotonicNs = 8_234_567_812_345L,
                sentCount = 42L,
                failedCount = 3L,
                droppedFrames = 2L,
                currentFps = 14.8f,
            ),
            cameraControlStatus = CameraControlStatus(
                focusLockSupported = true,
                focusLockApplied = true,
                exposureLockSupported = true,
                exposureLockApplied = false,
                whiteBalanceLockSupported = false,
                whiteBalanceLockApplied = null,
                fpsTargetSupported = true,
                resolutionSupported = true,
                manualExposureSupported = true,
                manualExposureApplied = true,
                isoApplied = 780,
                exposureTimeNsApplied = 19_800_000L,
                focalLengthMm = 4.2f,
                zoomSupported = true,
            ),
        )
    }
}
