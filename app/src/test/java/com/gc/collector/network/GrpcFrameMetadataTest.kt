package com.gc.collector.network

import com.google.protobuf.CodedInputStream
import com.google.protobuf.WireFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class GrpcFrameMetadataTest {
    @Test
    fun encodesManualExposureFields() {
        val bytes = GrpcFrameMetadata(
            cameraId = "camera_01",
            deviceId = "device_01",
            frameSequence = 7L,
            deviceTimestampMs = 100L,
            deviceMonotonicNs = 200L,
            width = 1280,
            height = 720,
            format = "jpeg",
            fpsTarget = 10,
            focusMode = "locked",
            focusLocked = true,
            exposureLocked = false,
            whiteBalanceLocked = true,
            zoomDisabled = true,
            orientationDeg = 90,
            sensorTimestampNs = 300L,
            focusLockRequested = true,
            focusLockSupport = "supported",
            focusLockApplied = "applied",
            exposureLockRequested = false,
            exposureLockSupport = "supported",
            exposureLockApplied = "not_applied",
            whiteBalanceLockRequested = true,
            whiteBalanceLockSupport = "supported",
            whiteBalanceLockApplied = "applied",
            fpsTargetSupport = "supported",
            resolutionSupport = "supported",
            manualExposureSupport = "supported",
            manualExposureRequested = true,
            manualExposureApplied = "applied",
            isoRequested = 400,
            isoApplied = 400,
            exposureTimeNsRequested = 10_000_000L,
            exposureTimeNsApplied = 10_000_000L,
            focalLengthMm = 5.4f,
        ).toByteArray()

        val parsed = parseFields(bytes)

        assertEquals(true, parsed[29])
        assertEquals("applied", parsed[30])
        assertEquals(400, parsed[31])
        assertEquals(400, parsed[32])
        assertEquals(10_000_000L, parsed[33])
        assertEquals(10_000_000L, parsed[34])
        assertEquals(5.4f, parsed[35] as Float, 0.0001f)
    }

    private fun parseFields(bytes: ByteArray): Map<Int, Any> {
        val input = CodedInputStream.newInstance(bytes)
        val values = mutableMapOf<Int, Any>()

        while (!input.isAtEnd) {
            val tag = input.readTag()
            if (tag == 0) break

            when (val field = WireFormat.getTagFieldNumber(tag)) {
                29 -> values[field] = input.readBool()
                30 -> values[field] = input.readString()
                31 -> values[field] = input.readInt32()
                32 -> values[field] = input.readInt32()
                33 -> values[field] = input.readInt64()
                34 -> values[field] = input.readInt64()
                35 -> values[field] = input.readFloat()
                else -> input.skipField(tag)
            }
        }

        return values
    }
}
