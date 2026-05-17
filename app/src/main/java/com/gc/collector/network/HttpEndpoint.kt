package com.gc.collector.network

import java.net.URI

data class HttpBaseUrl(
    val value: String,
) {
    fun resolve(path: String): String {
        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        return value.trimEnd('/') + normalizedPath
    }
}

fun parseHttpBaseUrl(value: String): Result<HttpBaseUrl> {
    val raw = value.trim()
    if (raw.isEmpty()) {
        return Result.failure(IllegalArgumentException("HTTP base URL is empty"))
    }

    return runCatching {
        val withScheme = if (raw.contains("://")) raw else "http://$raw"
        val uri = URI(withScheme)
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            throw IllegalArgumentException("HTTP base URL scheme must be http or https")
        }
        val host = uri.host ?: throw IllegalArgumentException("HTTP base URL host is missing")
        val port = if (uri.port > 0) ":${uri.port}" else ""
        val path = uri.path
            ?.trimEnd('/')
            ?.takeIf { it.isNotBlank() && it != "/" }
            .orEmpty()

        HttpBaseUrl("$scheme://$host$port$path")
    }
}
