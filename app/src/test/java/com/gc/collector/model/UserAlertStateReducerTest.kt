package com.gc.collector.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserAlertStateReducerTest {
    @Test
    fun acceptsValidNonExpiredAlert() {
        val (state, outcome) = UserAlertStateReducer.reduceSseData(
            state = UserAlertState(),
            data = samplePayload(eventId = "alert-1", severity = "warning"),
            nowMs = 1_780_624_971_101L,
        )

        assertTrue(outcome is UserAlertEventOutcome.Accepted)
        assertEquals("alert-1", state.latestAlert?.eventId)
        assertEquals(ProcessingAlertSeverity.Warning, state.latestAlert?.severity)
        assertEquals(1L, state.receivedCount)
        assertEquals(0L, state.expiredCount)
        assertEquals(0L, state.duplicateCount)
        assertEquals(0L, state.parseFailureCount)
        assertTrue("alert-1" in state.handledEventIds)
        assertEquals("Alert warning: alert-1", state.status)
    }

    @Test
    fun dropsExpiredAlert() {
        val (state, outcome) = UserAlertStateReducer.reduceSseData(
            state = UserAlertState(),
            data = samplePayload(eventId = "expired-alert"),
            nowMs = 1_780_624_971_103L,
        )

        assertTrue(outcome is UserAlertEventOutcome.Expired)
        assertNull(state.latestAlert)
        assertEquals(0L, state.receivedCount)
        assertEquals(1L, state.expiredCount)
        assertEquals(0L, state.duplicateCount)
        assertEquals(0L, state.parseFailureCount)
        assertTrue("expired-alert" !in state.handledEventIds)
        assertEquals("Alert expired: expired-alert", state.status)
    }

    @Test
    fun dropsDuplicateAlert() {
        val accepted = UserAlertStateReducer.reduceSseData(
            state = UserAlertState(),
            data = samplePayload(eventId = "alert-1"),
            nowMs = 1_780_624_971_101L,
        ).first

        val (state, outcome) = UserAlertStateReducer.reduceSseData(
            state = accepted,
            data = samplePayload(eventId = "alert-1"),
            nowMs = 1_780_624_971_101L,
        )

        assertTrue(outcome is UserAlertEventOutcome.Duplicate)
        assertEquals("alert-1", state.latestAlert?.eventId)
        assertEquals(1L, state.receivedCount)
        assertEquals(0L, state.expiredCount)
        assertEquals(1L, state.duplicateCount)
        assertEquals(0L, state.parseFailureCount)
        assertEquals("Alert duplicate: alert-1", state.status)
    }

    @Test
    fun parseFailureDoesNotReplaceLatestAlert() {
        val accepted = UserAlertStateReducer.reduceSseData(
            state = UserAlertState(),
            data = samplePayload(eventId = "alert-1"),
            nowMs = 1_780_624_971_101L,
        ).first

        val (state, outcome) = UserAlertStateReducer.reduceSseData(
            state = accepted,
            data = "{not-json",
            nowMs = 1_780_624_971_101L,
        )

        assertTrue(outcome is UserAlertEventOutcome.ParseFailed)
        assertEquals("alert-1", state.latestAlert?.eventId)
        assertEquals(1L, state.receivedCount)
        assertEquals(0L, state.expiredCount)
        assertEquals(0L, state.duplicateCount)
        assertEquals(1L, state.parseFailureCount)
        assertTrue(state.status.startsWith("Alert parse failed:"))
    }

    @Test
    fun acceptsDifferentEventIds() {
        val first = UserAlertStateReducer.reduceSseData(
            state = UserAlertState(),
            data = samplePayload(eventId = "alert-1", severity = "info"),
            nowMs = 1_780_624_971_101L,
        ).first

        val (state, outcome) = UserAlertStateReducer.reduceSseData(
            state = first,
            data = samplePayload(eventId = "alert-2", severity = "danger"),
            nowMs = 1_780_624_971_101L,
        )

        assertTrue(outcome is UserAlertEventOutcome.Accepted)
        assertEquals("alert-2", state.latestAlert?.eventId)
        assertEquals(ProcessingAlertSeverity.Danger, state.latestAlert?.severity)
        assertEquals(2L, state.receivedCount)
        assertEquals(setOf("alert-1", "alert-2"), state.handledEventIds)
        assertEquals("Alert danger: alert-2", state.status)
    }

    private fun samplePayload(
        eventId: String,
        severity: String = "warning",
    ): String {
        return """
            {
              "event_id": "$eventId",
              "frame_set_id": 100,
              "relay_run_id": 1,
              "timestamp_ms": 1780624911102,
              "severity": "$severity",
              "distance_m": 0.62,
              "joint": "pelvis",
              "obstacle_id": "unknown",
              "ttl_ms": 60000,
              "source": {
                "processor": "mmpose_triangulation",
                "camera_devices": ["android_device_001"]
              },
              "received_at_ms": 1780624911413,
              "expires_at_ms": 1780624971102,
              "routing": {
                "camera_devices": ["android_device_001"],
                "session_id": null,
                "delivery_status": "not_delivered"
              }
            }
        """.trimIndent()
    }
}
