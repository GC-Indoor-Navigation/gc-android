package com.gc.collector.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlertVoiceMessageMapperTest {
    @Test
    fun mapsWarningWithJoint() {
        val message = AlertVoiceMessageMapper.fromAlert(sampleAlert(severity = ProcessingAlertSeverity.Warning, joint = "left_knee"))

        assertEquals(AlertVoiceMessage(key = "Warning:Left knee", text = "Warning. Left knee."), message)
    }

    @Test
    fun mapsDangerWithJoint() {
        val message = AlertVoiceMessageMapper.fromAlert(sampleAlert(severity = ProcessingAlertSeverity.Danger, joint = "pelvis"))

        assertEquals(AlertVoiceMessage(key = "Danger:Pelvis", text = "Danger. Pelvis."), message)
    }

    @Test
    fun mapsDangerWithoutJoint() {
        val message = AlertVoiceMessageMapper.fromAlert(sampleAlert(severity = ProcessingAlertSeverity.Danger, joint = null))

        assertEquals(AlertVoiceMessage(key = "Danger", text = "Danger."), message)
    }

    @Test
    fun ignoresInfo() {
        val message = AlertVoiceMessageMapper.fromAlert(sampleAlert(severity = ProcessingAlertSeverity.Info, joint = "pelvis"))

        assertNull(message)
    }

    private fun sampleAlert(
        severity: ProcessingAlertSeverity,
        joint: String?,
    ): ProcessingAlert {
        return ProcessingAlert(
            eventId = "alert-1",
            frameSetId = 100L,
            relayRunId = 1L,
            timestampMs = 1_780_624_911_102L,
            severity = severity,
            distanceM = 0.62,
            joint = joint,
            obstacleId = "unknown",
            ttlMs = 60_000L,
            source = ProcessingAlertSource(
                processor = "mmpose_triangulation",
                cameraDevices = listOf("android_device_001"),
            ),
            receivedAtMs = 1_780_624_911_413L,
            expiresAtMs = 1_780_624_971_102L,
            routing = ProcessingAlertRouting(
                cameraDevices = listOf("android_device_001"),
                sessionId = null,
                deliveryStatus = "not_delivered",
            ),
        )
    }
}
