package com.gc.collector.model

data class PhoneAlertFeedbackPolicy(
    val vibrate: Boolean,
    val vibrationPatternMs: LongArray = longArrayOf(),
    val playSound: Boolean,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PhoneAlertFeedbackPolicy) return false

        return vibrate == other.vibrate &&
            vibrationPatternMs.contentEquals(other.vibrationPatternMs) &&
            playSound == other.playSound
    }

    override fun hashCode(): Int {
        var result = vibrate.hashCode()
        result = 31 * result + vibrationPatternMs.contentHashCode()
        result = 31 * result + playSound.hashCode()
        return result
    }
}

object PhoneAlertFeedbackPolicyMapper {
    fun fromSeverity(severity: ProcessingAlertSeverity): PhoneAlertFeedbackPolicy {
        return when (severity) {
            ProcessingAlertSeverity.Info -> PhoneAlertFeedbackPolicy(
                vibrate = false,
                playSound = false,
            )

            ProcessingAlertSeverity.Warning -> PhoneAlertFeedbackPolicy(
                vibrate = true,
                vibrationPatternMs = longArrayOf(0L, 450L),
                playSound = false,
            )

            ProcessingAlertSeverity.Danger -> PhoneAlertFeedbackPolicy(
                vibrate = true,
                vibrationPatternMs = longArrayOf(0L, 350L, 120L, 450L, 120L, 650L),
                playSound = true,
            )
        }
    }
}
