package com.gc.collector.model

data class AlertVoiceMessage(
    val key: String,
    val text: String,
)

object AlertVoiceMessageMapper {
    fun fromAlert(alert: ProcessingAlert): AlertVoiceMessage? {
        val severityText = when (alert.severity) {
            ProcessingAlertSeverity.Info -> return null
            ProcessingAlertSeverity.Warning -> "Warning"
            ProcessingAlertSeverity.Danger -> "Danger"
        }
        val jointText = alert.joint
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.toReadableJoint()

        val key = listOfNotNull(severityText, jointText).joinToString(separator = ":")
        val text = listOfNotNull(severityText, jointText)
            .joinToString(separator = ". ", postfix = ".")
        return AlertVoiceMessage(key = key, text = text)
    }

    private fun String.toReadableJoint(): String {
        return replace('_', ' ')
            .lowercase()
            .replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase() else char.toString()
            }
    }
}
