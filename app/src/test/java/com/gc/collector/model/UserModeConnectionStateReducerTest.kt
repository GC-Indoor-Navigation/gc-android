package com.gc.collector.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserModeConnectionStateReducerTest {
    @Test
    fun startEnablesUserModeAndMovesToConnecting() {
        val next = UserModeConnectionStateReducer.start(UserModeConnectionState())

        assertEquals(true, next.enabled)
        assertEquals(UserModeConnectionStatus.Connecting, next.status)
        assertEquals("User mode connecting", next.message)
        assertEquals(1L, next.connectAttempt)
        assertEquals(0L, next.reconnectCount)
        assertNull(next.lastError)
    }

    @Test
    fun connectedUpdatesTimestampWhenEnabled() {
        val next = UserModeConnectionStateReducer.connected(
            state = UserModeConnectionStateReducer.start(UserModeConnectionState()),
            nowMs = 1234L,
        )

        assertEquals(true, next.enabled)
        assertEquals(UserModeConnectionStatus.Connected, next.status)
        assertEquals("User mode connected", next.message)
        assertEquals(1234L, next.lastConnectedAtMs)
        assertNull(next.lastError)
    }

    @Test
    fun connectedIsIgnoredWhenDisabled() {
        val state = UserModeConnectionState(
            enabled = false,
            status = UserModeConnectionStatus.Stopped,
            message = "User mode stopped",
        )

        val next = UserModeConnectionStateReducer.connected(
            state = state,
            nowMs = 1234L,
        )

        assertEquals(state, next)
    }

    @Test
    fun failedWhileEnabledSchedulesReconnect() {
        val next = UserModeConnectionStateReducer.failed(
            state = UserModeConnectionStateReducer.start(UserModeConnectionState()),
            message = "network closed",
        )

        assertEquals(true, next.enabled)
        assertEquals(UserModeConnectionStatus.Reconnecting, next.status)
        assertEquals("User mode reconnecting: network closed", next.message)
        assertEquals(1L, next.reconnectCount)
        assertEquals("network closed", next.lastError)
    }

    @Test
    fun failedWhileDisabledMovesToErrorWithoutReconnect() {
        val next = UserModeConnectionStateReducer.failed(
            state = UserModeConnectionState(),
            message = "invalid URL",
        )

        assertEquals(false, next.enabled)
        assertEquals(UserModeConnectionStatus.Error, next.status)
        assertEquals("User mode error: invalid URL", next.message)
        assertEquals(0L, next.reconnectCount)
        assertEquals("invalid URL", next.lastError)
    }

    @Test
    fun reconnectingIncrementsCountOnlyWhenEnabled() {
        val enabled = UserModeConnectionStateReducer.reconnecting(
            UserModeConnectionState(
                enabled = true,
                status = UserModeConnectionStatus.Connected,
                reconnectCount = 2L,
            ),
        )
        val disabled = UserModeConnectionStateReducer.reconnecting(
            UserModeConnectionState(
                enabled = false,
                status = UserModeConnectionStatus.Stopped,
                reconnectCount = 2L,
            ),
        )

        assertEquals(UserModeConnectionStatus.Reconnecting, enabled.status)
        assertEquals(3L, enabled.reconnectCount)
        assertEquals(UserModeConnectionStatus.Stopped, disabled.status)
        assertEquals(2L, disabled.reconnectCount)
    }

    @Test
    fun stoppedDisablesUserModeAndKeepsCounters() {
        val next = UserModeConnectionStateReducer.stopped(
            UserModeConnectionState(
                enabled = true,
                status = UserModeConnectionStatus.Connected,
                connectAttempt = 4L,
                reconnectCount = 2L,
                lastConnectedAtMs = 1234L,
            ),
        )

        assertEquals(false, next.enabled)
        assertEquals(UserModeConnectionStatus.Stopped, next.status)
        assertEquals("User mode stopped", next.message)
        assertEquals(4L, next.connectAttempt)
        assertEquals(2L, next.reconnectCount)
        assertEquals(1234L, next.lastConnectedAtMs)
    }

    @Test
    fun completedWhileEnabledSchedulesReconnect() {
        val next = UserModeConnectionStateReducer.completed(
            UserModeConnectionState(
                enabled = true,
                status = UserModeConnectionStatus.Connected,
                reconnectCount = 2L,
            ),
        )

        assertEquals(UserModeConnectionStatus.Reconnecting, next.status)
        assertEquals("User mode stream closed", next.message)
        assertEquals(3L, next.reconnectCount)
    }

    @Test
    fun completedIsIgnoredWhenDisabled() {
        val state = UserModeConnectionState(
            enabled = false,
            status = UserModeConnectionStatus.Stopped,
            message = "User mode stopped",
        )

        assertEquals(state, UserModeConnectionStateReducer.completed(state))
    }
}
