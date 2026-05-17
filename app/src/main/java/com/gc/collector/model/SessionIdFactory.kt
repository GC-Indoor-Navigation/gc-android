package com.gc.collector.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SessionIdFactory {
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss-SSS", Locale.US)
    private val unsafeCharacters = Regex("[^A-Za-z0-9._-]+")

    fun create(
        deviceId: String,
        timestampMs: Long = System.currentTimeMillis(),
    ): String {
        val safeDeviceId = deviceId
            .trim()
            .replace(unsafeCharacters, "_")
            .trim('_')
            .ifBlank { "android_device" }
        val timestamp = synchronized(timestampFormat) {
            timestampFormat.format(Date(timestampMs))
        }

        return "${safeDeviceId}_$timestamp"
    }
}
