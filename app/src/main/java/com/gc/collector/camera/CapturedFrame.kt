package com.gc.collector.camera

import com.gc.collector.model.FrameMetadata

data class CapturedFrame(
    val jpegBytes: ByteArray,
    val metadata: FrameMetadata,
    val sensorTimestampNs: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CapturedFrame

        if (!jpegBytes.contentEquals(other.jpegBytes)) return false
        if (metadata != other.metadata) return false
        return sensorTimestampNs == other.sensorTimestampNs
    }

    override fun hashCode(): Int {
        var result = jpegBytes.contentHashCode()
        result = 31 * result + metadata.hashCode()
        result = 31 * result + sensorTimestampNs.hashCode()
        return result
    }
}
