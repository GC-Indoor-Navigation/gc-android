package com.gc.collector.network

import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import com.google.protobuf.WireFormat
import java.io.ByteArrayOutputStream

data class GrpcFramePacket(
    val metadata: GrpcFrameMetadata,
    val jpegBytes: ByteArray,
) {
    fun toByteArray(): ByteArray {
        val metadataBytes = metadata.toByteArray()
        return protoBytes {
            writeByteArray(1, metadataBytes)
            writeByteArray(2, jpegBytes)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GrpcFramePacket

        if (metadata != other.metadata) return false
        return jpegBytes.contentEquals(other.jpegBytes)
    }

    override fun hashCode(): Int {
        var result = metadata.hashCode()
        result = 31 * result + jpegBytes.contentHashCode()
        return result
    }
}

data class GrpcFrameMetadata(
    val cameraId: String,
    val deviceId: String,
    val frameSequence: Long,
    val deviceTimestampMs: Long,
    val deviceMonotonicNs: Long,
    val width: Int,
    val height: Int,
    val format: String,
    val fpsTarget: Int,
    val focusMode: String,
    val focusLocked: Boolean,
    val exposureLocked: Boolean,
    val whiteBalanceLocked: Boolean,
    val zoomDisabled: Boolean,
    val orientationDeg: Int,
    val sensorTimestampNs: Long,
) {
    fun toByteArray(): ByteArray {
        return protoBytes {
            writeString(1, cameraId)
            writeString(2, deviceId)
            writeInt64(3, frameSequence)
            writeInt64(4, deviceTimestampMs)
            writeInt64(5, deviceMonotonicNs)
            writeInt32(6, width)
            writeInt32(7, height)
            writeString(8, format)
            writeInt32(9, fpsTarget)
            writeString(10, focusMode)
            writeBool(11, focusLocked)
            writeBool(12, exposureLocked)
            writeBool(13, whiteBalanceLocked)
            writeBool(14, zoomDisabled)
            writeInt32(15, orientationDeg)
            writeInt64(16, sensorTimestampNs)
        }
    }
}

data class StreamFramesResponse(
    val receivedFrames: Long = 0L,
    val message: String = "",
) {
    fun toByteArray(): ByteArray {
        return protoBytes {
            writeInt64(1, receivedFrames)
            writeString(2, message)
        }
    }

    companion object {
        fun fromByteArray(bytes: ByteArray): StreamFramesResponse {
            val input = CodedInputStream.newInstance(bytes)
            var receivedFrames = 0L
            var message = ""

            while (!input.isAtEnd) {
                val tag = input.readTag()
                if (tag == 0) break

                when (WireFormat.getTagFieldNumber(tag)) {
                    1 -> receivedFrames = input.readInt64()
                    2 -> message = input.readString()
                    else -> input.skipField(tag)
                }
            }

            return StreamFramesResponse(
                receivedFrames = receivedFrames,
                message = message,
            )
        }
    }
}

private inline fun protoBytes(write: CodedOutputStream.() -> Unit): ByteArray {
    val output = ByteArrayOutputStream()
    val codedOutput = CodedOutputStream.newInstance(output)
    codedOutput.write()
    codedOutput.flush()
    return output.toByteArray()
}
