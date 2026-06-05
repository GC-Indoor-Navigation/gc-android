package com.gc.collector.model

enum class UserModeConnectionStatus {
    Idle,
    Connecting,
    Connected,
    Reconnecting,
    Stopped,
    Error,
}

data class UserModeConnectionState(
    val enabled: Boolean = false,
    val status: UserModeConnectionStatus = UserModeConnectionStatus.Idle,
    val message: String = "User mode idle",
    val connectAttempt: Long = 0L,
    val reconnectCount: Long = 0L,
    val lastConnectedAtMs: Long? = null,
    val lastError: String? = null,
)

object UserModeConnectionStateReducer {
    fun start(state: UserModeConnectionState): UserModeConnectionState {
        return state.copy(
            enabled = true,
            status = UserModeConnectionStatus.Connecting,
            message = "User mode connecting",
            connectAttempt = state.connectAttempt + 1L,
            lastError = null,
        )
    }

    fun connected(
        state: UserModeConnectionState,
        nowMs: Long,
    ): UserModeConnectionState {
        if (!state.enabled) {
            return state
        }

        return state.copy(
            status = UserModeConnectionStatus.Connected,
            message = "User mode connected",
            lastConnectedAtMs = nowMs,
            lastError = null,
        )
    }

    fun failed(
        state: UserModeConnectionState,
        message: String,
    ): UserModeConnectionState {
        if (!state.enabled) {
            return state.copy(
                status = UserModeConnectionStatus.Error,
                message = "User mode error: $message",
                lastError = message,
            )
        }

        return state.copy(
            status = UserModeConnectionStatus.Reconnecting,
            message = "User mode reconnecting: $message",
            reconnectCount = state.reconnectCount + 1L,
            lastError = message,
        )
    }

    fun reconnecting(
        state: UserModeConnectionState,
    ): UserModeConnectionState {
        if (!state.enabled) {
            return state
        }

        return state.copy(
            status = UserModeConnectionStatus.Reconnecting,
            message = "User mode reconnecting",
            reconnectCount = state.reconnectCount + 1L,
        )
    }

    fun stopped(state: UserModeConnectionState): UserModeConnectionState {
        return state.copy(
            enabled = false,
            status = UserModeConnectionStatus.Stopped,
            message = "User mode stopped",
        )
    }

    fun completed(state: UserModeConnectionState): UserModeConnectionState {
        if (!state.enabled) {
            return state
        }

        return state.copy(
            status = UserModeConnectionStatus.Reconnecting,
            message = "User mode stream closed",
            reconnectCount = state.reconnectCount + 1L,
        )
    }
}
