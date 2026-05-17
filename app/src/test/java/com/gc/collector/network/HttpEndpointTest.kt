package com.gc.collector.network

import org.junit.Assert.assertEquals
import org.junit.Test

class HttpEndpointTest {
    @Test
    fun parsesHttpBaseUrl() {
        val endpoint = parseHttpBaseUrl("http://192.168.0.10:8080").getOrThrow()

        assertEquals("http://192.168.0.10:8080", endpoint.value)
        assertEquals(
            "http://192.168.0.10:8080/capture/internal-calibration",
            endpoint.resolve("/capture/internal-calibration"),
        )
    }

    @Test
    fun defaultsMissingSchemeToHttp() {
        val endpoint = parseHttpBaseUrl("192.168.0.10:8080").getOrThrow()

        assertEquals("http://192.168.0.10:8080", endpoint.value)
    }

    @Test
    fun trimsTrailingSlash() {
        val endpoint = parseHttpBaseUrl("http://collector.local:8080/").getOrThrow()

        assertEquals("http://collector.local:8080", endpoint.value)
    }
}
