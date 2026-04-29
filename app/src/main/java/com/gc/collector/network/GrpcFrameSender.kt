package com.gc.collector.network

import com.gc.collector.camera.CapturedFrame
import io.grpc.ManagedChannel
import io.grpc.okhttp.OkHttpChannelBuilder
import io.grpc.stub.StreamObserver
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class GrpcFrameSender : FrameSender {
    private var channel: ManagedChannel? = null
    private var requestObserver: StreamObserver<GrpcFramePacket>? = null
    private val started = AtomicBoolean(false)
    private val failed = AtomicBoolean(false)
    private val sentCount = AtomicLong(0L)
    private var lastErrorMessage: String? = null

    override fun start(
        host: String,
        port: Int,
        usePlaintext: Boolean,
    ) {
        stop()

        val channelBuilder = OkHttpChannelBuilder.forAddress(host, port)
        if (usePlaintext) {
            channelBuilder.usePlaintext()
        }

        val newChannel = channelBuilder.build()
        val stub = FrameIngestServiceGrpc.newStub(newChannel)
        val responseObserver = object : StreamObserver<StreamFramesResponse> {
            override fun onNext(value: StreamFramesResponse) = Unit

            override fun onError(t: Throwable) {
                failed.set(true)
                started.set(false)
                lastErrorMessage = t.message ?: t::class.java.simpleName
            }

            override fun onCompleted() {
                started.set(false)
            }
        }

        channel = newChannel
        requestObserver = stub.streamFrames(responseObserver)
        failed.set(false)
        started.set(true)
        sentCount.set(0L)
        lastErrorMessage = null
    }

    override fun send(frame: CapturedFrame): SendResult {
        val observer = requestObserver ?: return SendResult.NotStarted
        if (!started.get()) return SendResult.NotStarted
        if (failed.get()) return SendResult.Failed(lastErrorMessage ?: "gRPC stream failed")

        return runCatching {
            observer.onNext(frame.toFramePacket())
            sentCount.incrementAndGet()
            SendResult.Sent
        }.getOrElse { error ->
            failed.set(true)
            started.set(false)
            lastErrorMessage = error.message ?: error::class.java.simpleName
            SendResult.Failed(lastErrorMessage ?: "failed to send frame")
        }
    }

    override fun stop() {
        requestObserver?.let { observer ->
            runCatching {
                observer.onCompleted()
            }
        }
        requestObserver = null
        channel?.shutdown()
        channel = null
        started.set(false)
    }

    fun sentFrames(): Long = sentCount.get()

    fun lastError(): String? = lastErrorMessage
}
