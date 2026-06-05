package com.gc.collector.network

data class SseEvent(
    val event: String?,
    val data: String,
)

class SseEventParser {
    private val eventLines = mutableListOf<String>()

    fun acceptLine(line: String): SseEvent? {
        if (line.isEmpty()) {
            return flush()
        }

        if (line.startsWith(":")) {
            return null
        }

        eventLines += line
        return null
    }

    fun flush(): SseEvent? {
        if (eventLines.isEmpty()) {
            return null
        }

        var eventName: String? = null
        val dataLines = mutableListOf<String>()

        for (line in eventLines) {
            val fieldEnd = line.indexOf(':')
            val field = if (fieldEnd >= 0) line.substring(0, fieldEnd) else line
            val rawValue = if (fieldEnd >= 0) line.substring(fieldEnd + 1) else ""
            val value = rawValue.removePrefix(" ")

            when (field) {
                "event" -> eventName = value
                "data" -> dataLines += value
            }
        }

        eventLines.clear()

        if (dataLines.isEmpty()) {
            return null
        }

        return SseEvent(
            event = eventName,
            data = dataLines.joinToString(separator = "\n"),
        )
    }
}
