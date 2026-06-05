package com.gc.collector.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessingAlertTest {
    @Test
    fun parsesProcessingAlertPayload() {
        val alert = ProcessingAlertJsonParser.parse(samplePayload()).getOrThrow()

        assertEquals("manual-sse-live-test", alert.eventId)
        assertEquals(100L, alert.frameSetId)
        assertEquals(1L, alert.relayRunId)
        assertEquals(1780624911102L, alert.timestampMs)
        assertEquals(ProcessingAlertSeverity.Warning, alert.severity)
        assertEquals(0.62, alert.distanceM ?: 0.0, 0.001)
        assertEquals("pelvis", alert.joint)
        assertEquals("unknown", alert.obstacleId)
        assertEquals(60_000L, alert.ttlMs)
        assertEquals("mmpose_triangulation", alert.source.processor)
        assertEquals(listOf("android_device_001", "android_device_002"), alert.source.cameraDevices)
        assertEquals(1780624911413L, alert.receivedAtMs)
        assertEquals(1780624971102L, alert.expiresAtMs)
        assertEquals(listOf("android_device_001", "android_device_002"), alert.routing?.cameraDevices)
        assertNull(alert.routing?.sessionId)
        assertEquals("not_delivered", alert.routing?.deliveryStatus)
    }

    @Test
    fun parsesNullableOptionalFieldsAndUnknownFields() {
        val payload = """
            {
              "event_id": "nullable-alert",
              "frame_set_id": 101,
              "relay_run_id": null,
              "timestamp_ms": 1780624911102,
              "severity": "danger",
              "distance_m": null,
              "joint": null,
              "obstacle_id": null,
              "ttl_ms": 60000,
              "source": {
                "processor": "mmpose_triangulation",
                "camera_devices": ["android_device_001"],
                "extra_source_field": "ignored"
              },
              "received_at_ms": 1780624911413,
              "expires_at_ms": 1780624971102,
              "routing": {
                "camera_devices": ["android_device_001"],
                "session_id": null,
                "delivery_status": "not_delivered",
                "extra_routing_field": "ignored"
              },
              "extra_top_level_field": "ignored"
            }
        """.trimIndent()

        val alert = ProcessingAlertJsonParser.parse(payload).getOrThrow()

        assertEquals(ProcessingAlertSeverity.Danger, alert.severity)
        assertNull(alert.relayRunId)
        assertNull(alert.distanceM)
        assertNull(alert.joint)
        assertNull(alert.obstacleId)
    }

    @Test
    fun treatsAlertAsExpiredOnlyWhenNowIsGreaterThanExpiresAt() {
        val alert = ProcessingAlertJsonParser.parse(samplePayload()).getOrThrow()

        assertFalse(alert.isExpired(nowMs = alert.expiresAtMs))
        assertTrue(alert.isExpired(nowMs = alert.expiresAtMs + 1))
    }

    @Test
    fun returnsFailureForInvalidSeverity() {
        val payload = samplePayload().replace("\"severity\": \"warning\"", "\"severity\": \"critical\"")

        assertTrue(ProcessingAlertJsonParser.parse(payload).isFailure)
    }

    private fun samplePayload(): String {
        return """
            {
              "event_id": "manual-sse-live-test",
              "frame_set_id": 100,
              "relay_run_id": 1,
              "timestamp_ms": 1780624911102,
              "severity": "warning",
              "distance_m": 0.62,
              "joint": "pelvis",
              "obstacle_id": "unknown",
              "ttl_ms": 60000,
              "source": {
                "processor": "mmpose_triangulation",
                "camera_devices": [
                  "android_device_001",
                  "android_device_002"
                ]
              },
              "received_at_ms": 1780624911413,
              "expires_at_ms": 1780624971102,
              "routing": {
                "camera_devices": [
                  "android_device_001",
                  "android_device_002"
                ],
                "session_id": null,
                "delivery_status": "not_delivered"
              }
            }
        """.trimIndent()
    }
}
