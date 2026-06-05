package com.gc.collector.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SseEventParserTest {
    @Test
    fun parsesProcessingAlertEvent() {
        val parser = SseEventParser()

        assertNull(parser.acceptLine("event: processing_alert"))
        assertNull(parser.acceptLine("data: {\"event_id\":\"manual-sse-live-test\"}"))
        val event = parser.acceptLine("")

        assertEquals("processing_alert", event?.event)
        assertEquals("{\"event_id\":\"manual-sse-live-test\"}", event?.data)
    }

    @Test
    fun ignoresKeepAliveComment() {
        val parser = SseEventParser()

        assertNull(parser.acceptLine(": keep-alive"))
        assertNull(parser.acceptLine(""))
    }

    @Test
    fun joinsMultipleDataLinesWithNewline() {
        val parser = SseEventParser()

        parser.acceptLine("event: processing_alert")
        parser.acceptLine("data: {")
        parser.acceptLine("data: \"event_id\":\"manual-sse-live-test\"")
        parser.acceptLine("data: }")
        val event = parser.acceptLine("")

        assertEquals("processing_alert", event?.event)
        assertEquals("{\n\"event_id\":\"manual-sse-live-test\"\n}", event?.data)
    }

    @Test
    fun ignoresEventsWithoutData() {
        val parser = SseEventParser()

        parser.acceptLine("event: processing_alert")

        assertNull(parser.acceptLine(""))
    }

    @Test
    fun flushesPendingEventAtEndOfStream() {
        val parser = SseEventParser()

        parser.acceptLine("event: processing_alert")
        parser.acceptLine("data: {\"event_id\":\"tail-event\"}")
        val event = parser.flush()

        assertEquals("processing_alert", event?.event)
        assertEquals("{\"event_id\":\"tail-event\"}", event?.data)
        assertNull(parser.flush())
    }

    @Test
    fun stripsSingleLeadingSpaceAfterColonOnly() {
        val parser = SseEventParser()

        parser.acceptLine("event: processing_alert")
        parser.acceptLine("data:   padded")
        val event = parser.acceptLine("")

        assertEquals("  padded", event?.data)
    }
}
