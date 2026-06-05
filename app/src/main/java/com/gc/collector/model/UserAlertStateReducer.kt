package com.gc.collector.model

data class UserAlertState(
    val status: String = "User mode idle",
    val latestAlert: ProcessingAlert? = null,
    val receivedCount: Long = 0L,
    val expiredCount: Long = 0L,
    val duplicateCount: Long = 0L,
    val parseFailureCount: Long = 0L,
    val handledEventIds: Set<String> = emptySet(),
)

sealed interface UserAlertEventOutcome {
    data class Accepted(val alert: ProcessingAlert) : UserAlertEventOutcome
    data class Expired(val alert: ProcessingAlert) : UserAlertEventOutcome
    data class Duplicate(val alert: ProcessingAlert) : UserAlertEventOutcome
    data class ParseFailed(val message: String) : UserAlertEventOutcome
}

object UserAlertStateReducer {
    fun reduceSseData(
        state: UserAlertState,
        data: String,
        nowMs: Long,
    ): Pair<UserAlertState, UserAlertEventOutcome> {
        val alert = ProcessingAlertJsonParser.parse(data).getOrElse { error ->
            val next = state.copy(
                status = "Alert parse failed: ${error.message ?: error::class.java.simpleName}",
                parseFailureCount = state.parseFailureCount + 1L,
            )
            return next to UserAlertEventOutcome.ParseFailed(
                message = error.message ?: error::class.java.simpleName,
            )
        }

        if (alert.isExpired(nowMs)) {
            val next = state.copy(
                status = "Alert expired: ${alert.eventId}",
                expiredCount = state.expiredCount + 1L,
            )
            return next to UserAlertEventOutcome.Expired(alert)
        }

        if (alert.eventId in state.handledEventIds) {
            val next = state.copy(
                status = "Alert duplicate: ${alert.eventId}",
                duplicateCount = state.duplicateCount + 1L,
            )
            return next to UserAlertEventOutcome.Duplicate(alert)
        }

        val next = state.copy(
            status = "Alert ${alert.severity.name.lowercase()}: ${alert.eventId}",
            latestAlert = alert,
            receivedCount = state.receivedCount + 1L,
            handledEventIds = state.handledEventIds + alert.eventId,
        )
        return next to UserAlertEventOutcome.Accepted(alert)
    }
}
