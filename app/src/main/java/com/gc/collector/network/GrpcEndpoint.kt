package com.gc.collector.network

import java.net.URI

data class GrpcEndpoint(
    val host: String,
    val port: Int,
    val usePlaintext: Boolean,
)

fun parseGrpcEndpoint(value: String): Result<GrpcEndpoint> {
    val raw = value.trim()
    if (raw.isEmpty()) {
        return Result.failure(IllegalArgumentException("Server address is empty"))
    }

    return runCatching {
        if (raw.contains("://")) {
            parseUriEndpoint(raw)
        } else {
            parseHostPortEndpoint(raw)
        }
    }
}

private fun parseUriEndpoint(raw: String): GrpcEndpoint {
    val uri = URI(raw)
    val scheme = uri.scheme?.lowercase()
    val host = uri.host ?: throw IllegalArgumentException("Server host is missing")
    val port = when {
        uri.port > 0 -> uri.port
        scheme == "https" -> 443
        else -> 50051
    }

    return GrpcEndpoint(
        host = host,
        port = port,
        usePlaintext = scheme != "https",
    )
}

private fun parseHostPortEndpoint(raw: String): GrpcEndpoint {
    val hostAndPort = raw.substringBefore("/")
    val separatorIndex = hostAndPort.lastIndexOf(":")
    val host = if (separatorIndex > 0) {
        hostAndPort.substring(0, separatorIndex)
    } else {
        hostAndPort
    }
    val port = if (separatorIndex > 0) {
        hostAndPort.substring(separatorIndex + 1).toIntOrNull()
            ?: throw IllegalArgumentException("Server port is invalid")
    } else {
        50051
    }

    if (host.isBlank()) {
        throw IllegalArgumentException("Server host is missing")
    }

    return GrpcEndpoint(
        host = host,
        port = port,
        usePlaintext = true,
    )
}
