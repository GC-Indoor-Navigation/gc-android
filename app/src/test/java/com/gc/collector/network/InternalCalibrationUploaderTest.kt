package com.gc.collector.network

import com.gc.collector.camera.CapturedFrame
import com.gc.collector.model.FrameMetadata
import okhttp3.MultipartBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InternalCalibrationUploaderTest {
    @Test
    fun buildsMultipartUploadRequest() {
        val request = buildInternalCalibrationRequest(
            baseUrl = HttpBaseUrl("http://192.168.0.10:8080"),
            frame = sampleFrame(),
        )

        assertEquals("POST", request.method)
        assertEquals("http://192.168.0.10:8080/capture/internal-calibration", request.url.toString())
        assertTrue(request.body is MultipartBody)
        assertTrue(request.body?.contentType().toString().startsWith("multipart/form-data"))
    }

    private fun sampleFrame(): CapturedFrame {
        return CapturedFrame(
            jpegBytes = byteArrayOf(1, 2, 3),
            sensorTimestampNs = 300L,
            metadata = FrameMetadata(
                cameraId = "camera_01",
                deviceId = "device_01",
                frameSequence = 7L,
                sessionId = null,
                deviceTimestampMs = 100L,
                deviceMonotonicNs = 200L,
                width = 1280,
                height = 720,
                fpsTarget = 10,
                focusMode = "auto",
                focusLocked = false,
                exposureLocked = false,
                whiteBalanceLocked = false,
                zoomDisabled = true,
                orientationDeg = 90,
                focusLockRequested = false,
                focusLockSupport = "supported",
                focusLockApplied = "not_applied",
                exposureLockRequested = false,
                exposureLockSupport = "supported",
                exposureLockApplied = "not_applied",
                whiteBalanceLockRequested = false,
                whiteBalanceLockSupport = "supported",
                whiteBalanceLockApplied = "not_applied",
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
            ),
        )
    }
}
