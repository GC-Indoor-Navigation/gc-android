package com.gc.collector.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import okio.Buffer

class PhoneAlertSseClientTest {
    @Test
    fun buildsPhoneAlertSseRequest() {
        val request = buildPhoneAlertSseRequest(
            baseUrl = HttpBaseUrl("http://192.168.0.10:8000"),
            deviceId = "android_device_001",
        )

        assertEquals("GET", request.method)
        assertEquals("http://192.168.0.10:8000/phone/alerts/events?device_id=android_device_001", request.url.toString())
        assertEquals("text/event-stream", request.header("Accept"))
    }

    @Test
    fun encodesDeviceIdQueryParameter() {
        val request = buildPhoneAlertSseRequest(
            baseUrl = HttpBaseUrl("http://stream-server.local:8000"),
            deviceId = "android device/001",
        )

        assertEquals("android device/001", request.url.queryParameter("device_id"))
        assertTrue(request.url.toString().contains("device_id=android%20device%2F001"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBlankDeviceId() {
        buildPhoneAlertSseRequest(
            baseUrl = HttpBaseUrl("http://192.168.0.10:8000"),
            deviceId = " ",
        )
    }

    @Test
    fun readsOnlyProcessingAlertEvents() {
        val source = Buffer().writeUtf8(
            """
            : keep-alive

            event: processing_alert
            data: {"event_id":"alert-1"}

            event: unrelated
            data: {"event_id":"ignored"}

            event: processing_alert
            data: {"event_id":"alert-2"}

            """.trimIndent(),
        )
        val events = mutableListOf<SseEvent>()

        val count = readPhoneAlertSseEvents(source, events::add)

        assertEquals(2, count)
        assertEquals(listOf("alert-1", "alert-2"), events.map { eventIdFromData(it.data) })
    }

    @Test
    fun flushesTailProcessingAlertEvent() {
        val source = Buffer().writeUtf8(
            """
            event: processing_alert
            data: {"event_id":"tail-alert"}
            """.trimIndent(),
        )
        val events = mutableListOf<SseEvent>()

        val count = readPhoneAlertSseEvents(source, events::add)

        assertEquals(1, count)
        assertEquals("tail-alert", eventIdFromData(events.single().data))
    }

    private fun eventIdFromData(data: String): String {
        return data
            .substringAfter("\"event_id\":\"")
            .substringBefore("\"")
    }
}
