package com.gc.collector.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GrpcEndpointTest {
    @Test
    fun parsesHostAndPortEndpoint() {
        val endpoint = parseGrpcEndpoint("192.168.0.10:50051").getOrThrow()

        assertEquals("192.168.0.10", endpoint.host)
        assertEquals(50051, endpoint.port)
        assertTrue(endpoint.usePlaintext)
    }

    @Test
    fun defaultsPlainHostToGrpcPort() {
        val endpoint = parseGrpcEndpoint("collector.local").getOrThrow()

        assertEquals("collector.local", endpoint.host)
        assertEquals(50051, endpoint.port)
        assertTrue(endpoint.usePlaintext)
    }

    @Test
    fun parsesHttpsEndpointAsTls() {
        val endpoint = parseGrpcEndpoint("https://collector.example.com").getOrThrow()

        assertEquals("collector.example.com", endpoint.host)
        assertEquals(443, endpoint.port)
        assertFalse(endpoint.usePlaintext)
    }
}
