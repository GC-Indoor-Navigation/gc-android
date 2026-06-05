package com.gc.collector.network

import java.io.IOException
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.BufferedSource

private const val phoneAlertsPath = "/phone/alerts/events"
private const val processingAlertEvent = "processing_alert"

class PhoneAlertSseClient(
    private val client: OkHttpClient = OkHttpClient(),
) {
    fun open(
        baseUrl: String,
        deviceId: String,
        onEvent: (SseEvent) -> Unit,
    ): Result<PhoneAlertSseCall> {
        val endpoint = parseHttpBaseUrl(baseUrl).getOrElse { error ->
            return Result.failure(error)
        }

        return runCatching {
            PhoneAlertSseCall(
                call = client.newCall(buildPhoneAlertSseRequest(endpoint, deviceId)),
                onEvent = onEvent,
            )
        }
    }
}

class PhoneAlertSseCall internal constructor(
    private val call: Call,
    private val onEvent: (SseEvent) -> Unit,
) {
    fun execute(): PhoneAlertSseResult {
        return try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    return PhoneAlertSseResult.ServerError(
                        code = response.code,
                        message = response.message,
                    )
                }

                val body = response.body ?: return PhoneAlertSseResult.StreamError("SSE response body is empty")
                readPhoneAlertSseEvents(body.source(), onEvent)
                PhoneAlertSseResult.Completed
            }
        } catch (error: IOException) {
            if (call.isCanceled()) {
                PhoneAlertSseResult.Cancelled
            } else {
                PhoneAlertSseResult.NetworkError(error.message ?: error::class.java.simpleName)
            }
        } catch (error: IllegalArgumentException) {
            PhoneAlertSseResult.StreamError(error.message ?: error::class.java.simpleName)
        }
    }

    fun cancel() {
        call.cancel()
    }
}

sealed interface PhoneAlertSseResult {
    data object Completed : PhoneAlertSseResult
    data object Cancelled : PhoneAlertSseResult
    data class ServerError(val code: Int, val message: String) : PhoneAlertSseResult
    data class NetworkError(val message: String) : PhoneAlertSseResult
    data class StreamError(val message: String) : PhoneAlertSseResult
}

internal fun buildPhoneAlertSseRequest(
    baseUrl: HttpBaseUrl,
    deviceId: String,
): Request {
    val normalizedDeviceId = deviceId.trim()
    require(normalizedDeviceId.isNotEmpty()) {
        "device_id is required"
    }

    val url = baseUrl
        .resolve(phoneAlertsPath)
        .toHttpUrl()
        .newBuilder()
        .addQueryParameter("device_id", normalizedDeviceId)
        .build()

    return Request.Builder()
        .url(url)
        .get()
        .header("Accept", "text/event-stream")
        .build()
}

internal fun readPhoneAlertSseEvents(
    source: BufferedSource,
    onEvent: (SseEvent) -> Unit,
): Int {
    val parser = SseEventParser()
    var deliveredCount = 0

    while (true) {
        val line = source.readUtf8Line() ?: break
        val event = parser.acceptLine(line)
        if (event?.event == processingAlertEvent) {
            onEvent(event)
            deliveredCount += 1
        }
    }

    val tailEvent = parser.flush()
    if (tailEvent?.event == processingAlertEvent) {
        onEvent(tailEvent)
        deliveredCount += 1
    }

    return deliveredCount
}
