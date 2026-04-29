package com.gc.collector.network

import com.gc.collector.camera.CapturedFrame

interface FrameSender {
    fun start(
        host: String,
        port: Int,
        usePlaintext: Boolean = true,
    )

    fun send(frame: CapturedFrame): SendResult

    fun stop()
}

sealed interface SendResult {
    data object Sent : SendResult
    data class Failed(val message: String) : SendResult
    data object NotStarted : SendResult
}
