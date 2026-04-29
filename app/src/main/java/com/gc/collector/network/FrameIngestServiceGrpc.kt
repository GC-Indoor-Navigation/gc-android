package com.gc.collector.network

import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.MethodDescriptor
import io.grpc.stub.ClientCalls
import io.grpc.stub.StreamObserver
import java.io.ByteArrayInputStream
import java.io.InputStream

object FrameIngestServiceGrpc {
    private const val SERVICE_NAME = "gc.collector.v1.FrameIngestService"

    private val streamFramesMethod: MethodDescriptor<GrpcFramePacket, StreamFramesResponse> =
        MethodDescriptor.newBuilder<GrpcFramePacket, StreamFramesResponse>()
            .setType(MethodDescriptor.MethodType.CLIENT_STREAMING)
            .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE_NAME, "StreamFrames"))
            .setSampledToLocalTracing(true)
            .setRequestMarshaller(FramePacketMarshaller)
            .setResponseMarshaller(StreamFramesResponseMarshaller)
            .build()

    fun newStub(channel: Channel): FrameIngestStub = FrameIngestStub(channel)

    class FrameIngestStub internal constructor(
        private val channel: Channel,
        private val callOptions: CallOptions = CallOptions.DEFAULT,
    ) {
        fun streamFrames(responseObserver: StreamObserver<StreamFramesResponse>): StreamObserver<GrpcFramePacket> {
            return ClientCalls.asyncClientStreamingCall(
                channel.newCall(streamFramesMethod, callOptions),
                responseObserver,
            )
        }
    }
}

private object FramePacketMarshaller : MethodDescriptor.Marshaller<GrpcFramePacket> {
    override fun stream(value: GrpcFramePacket): InputStream {
        return ByteArrayInputStream(value.toByteArray())
    }

    override fun parse(stream: InputStream): GrpcFramePacket {
        throw UnsupportedOperationException("Android collector only sends FramePacket messages.")
    }
}

private object StreamFramesResponseMarshaller : MethodDescriptor.Marshaller<StreamFramesResponse> {
    override fun stream(value: StreamFramesResponse): InputStream {
        return ByteArrayInputStream(value.toByteArray())
    }

    override fun parse(stream: InputStream): StreamFramesResponse {
        return StreamFramesResponse.fromByteArray(stream.readBytes())
    }
}
