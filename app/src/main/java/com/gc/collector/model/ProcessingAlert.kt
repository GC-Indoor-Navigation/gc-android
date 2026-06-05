package com.gc.collector.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ProcessingAlert(
    @SerialName("event_id")
    val eventId: String,
    @SerialName("frame_set_id")
    val frameSetId: Long,
    @SerialName("relay_run_id")
    val relayRunId: Long? = null,
    @SerialName("timestamp_ms")
    val timestampMs: Long,
    val severity: ProcessingAlertSeverity,
    @SerialName("distance_m")
    val distanceM: Double? = null,
    val joint: String? = null,
    @SerialName("obstacle_id")
    val obstacleId: String? = null,
    @SerialName("ttl_ms")
    val ttlMs: Long,
    val source: ProcessingAlertSource,
    @SerialName("received_at_ms")
    val receivedAtMs: Long,
    @SerialName("expires_at_ms")
    val expiresAtMs: Long,
    val routing: ProcessingAlertRouting? = null,
) {
    fun isExpired(nowMs: Long): Boolean = nowMs > expiresAtMs
}

@Serializable
enum class ProcessingAlertSeverity {
    @SerialName("info")
    Info,

    @SerialName("warning")
    Warning,

    @SerialName("danger")
    Danger,
}

@Serializable
data class ProcessingAlertSource(
    val processor: String,
    @SerialName("camera_devices")
    val cameraDevices: List<String> = emptyList(),
)

@Serializable
data class ProcessingAlertRouting(
    @SerialName("camera_devices")
    val cameraDevices: List<String> = emptyList(),
    @SerialName("session_id")
    val sessionId: String? = null,
    @SerialName("delivery_status")
    val deliveryStatus: String? = null,
)

object ProcessingAlertJsonParser {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun parse(data: String): Result<ProcessingAlert> {
        return runCatching {
            json.decodeFromString(ProcessingAlert.serializer(), data)
        }
    }
}
