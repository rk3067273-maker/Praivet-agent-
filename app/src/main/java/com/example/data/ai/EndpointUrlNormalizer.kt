package com.example.data.ai

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.InetAddress

sealed interface NormalizedEndpointResult {
    data class Valid(
        val normalizedBaseUrl: String,
        val chatCompletionsUrl: String,
        val modelsUrl: String,
        val isLocalDevelopmentHost: Boolean
    ) : NormalizedEndpointResult

    data class Invalid(val reason: String) : NormalizedEndpointResult
}

object EndpointUrlNormalizer {

    fun normalizeOpenAiCompatible(
        rawUrl: String,
        allowLocalHttp: Boolean = true
    ): NormalizedEndpointResult {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) {
            return NormalizedEndpointResult.Invalid("Base URL cannot be empty.")
        }
        if (trimmed.contains(" ") || trimmed.contains("\n") || trimmed.contains("\t")) {
            return NormalizedEndpointResult.Invalid("Base URL must not contain whitespace.")
        }

        val parsed = trimmed.toHttpUrlOrNull()
            ?: return NormalizedEndpointResult.Invalid("Malformed URL. Include https:// (or http:// for local dev).")

        if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) {
            return NormalizedEndpointResult.Invalid("URL must not contain embedded credentials.")
        }

        val host = parsed.host.lowercase()
        val isLocal = isLocalDevHost(host)

        if (parsed.scheme.equals("http", ignoreCase = true)) {
            if (!allowLocalHttp || !isLocal) {
                return NormalizedEndpointResult.Invalid(
                    "HTTPS is required for remote AI providers. HTTP is permitted only for local loopback/LAN dev hosts."
                )
            }
        } else if (!parsed.scheme.equals("https", ignoreCase = true)) {
            return NormalizedEndpointResult.Invalid("Unsupported URL scheme '${parsed.scheme}'. Use https://.")
        }

        // Collapse empty path segments and deduplicate repeated /v1 or /chat/completions suffixes
        val rawSegments = parsed.pathSegments.filter { it.isNotEmpty() }.toMutableList()

        // Strip trailing /chat/completions (even if duplicated)
        while (rawSegments.size >= 2 &&
            rawSegments[rawSegments.size - 2].equals("chat", ignoreCase = true) &&
            rawSegments[rawSegments.size - 1].equals("completions", ignoreCase = true)
        ) {
            rawSegments.removeAt(rawSegments.size - 1)
            rawSegments.removeAt(rawSegments.size - 1)
        }

        // Strip trailing /models if user pasted the models URL
        if (rawSegments.isNotEmpty() && rawSegments.last().equals("models", ignoreCase = true)) {
            rawSegments.removeAt(rawSegments.size - 1)
        }

        // Deduplicate repeated trailing /v1 segments (e.g. /v1/v1 -> /v1)
        while (rawSegments.size >= 2 &&
            rawSegments[rawSegments.size - 1].equals("v1", ignoreCase = true) &&
            rawSegments[rawSegments.size - 2].equals("v1", ignoreCase = true)
        ) {
            rawSegments.removeAt(rawSegments.size - 1)
        }

        // If user entered a bare host without /v1 (and not /api/v1 etc.), append /v1 for standard OpenAI-compatible base
        val hasVersionOrApiPath = rawSegments.any {
            it.equals("v1", ignoreCase = true) ||
                it.matches(Regex("""v\d+""")) ||
                it.equals("openai", ignoreCase = true)
        }
        if (rawSegments.isEmpty() || !hasVersionOrApiPath) {
            rawSegments.add("v1")
        }

        val baseBuilder = parsed.newBuilder()
            .encodedPath("/")
            .query(null)
            .fragment(null)
        rawSegments.forEach { seg -> baseBuilder.addPathSegment(seg) }
        val normalizedBase = baseBuilder.build().toString().trimEnd('/')

        val chatUrl = "$normalizedBase/chat/completions"
        val modelsUrl = "$normalizedBase/models"

        return NormalizedEndpointResult.Valid(
            normalizedBaseUrl = normalizedBase,
            chatCompletionsUrl = chatUrl,
            modelsUrl = modelsUrl,
            isLocalDevelopmentHost = isLocal
        )
    }

    fun normalizeOllamaBaseUrl(rawUrl: String): NormalizedEndpointResult {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) {
            return NormalizedEndpointResult.Invalid("Ollama URL cannot be empty.")
        }
        val parsed = trimmed.toHttpUrlOrNull()
            ?: return NormalizedEndpointResult.Invalid("Malformed Ollama URL.")

        val host = parsed.host.lowercase()
        val isLocal = isLocalDevHost(host)
        if (parsed.scheme.equals("http", ignoreCase = true) && !isLocal) {
            return NormalizedEndpointResult.Invalid(
                "Remote Ollama endpoints must use HTTPS. HTTP is restricted to local loopback/LAN hosts."
            )
        }

        val base = parsed.newBuilder()
            .encodedPath("/")
            .query(null)
            .fragment(null)
            .build()
            .toString()
            .trimEnd('/')

        return NormalizedEndpointResult.Valid(
            normalizedBaseUrl = base,
            chatCompletionsUrl = "$base/api/chat",
            modelsUrl = "$base/api/tags",
            isLocalDevelopmentHost = isLocal
        )
    }

    fun isLocalDevHost(host: String): Boolean {
        val lower = host.lowercase()
        if (lower == "localhost" || lower == "127.0.0.1" || lower == "::1" || lower == "10.0.2.2") {
            return true
        }
        if (lower.endsWith(".local")) return true
        val ipv4 = lower.split(".")
        if (ipv4.size == 4 && ipv4.all { it.toIntOrNull() in 0..255 }) {
            val a = ipv4[0].toInt()
            val b = ipv4[1].toInt()
            if (a == 10 || a == 127) return true
            if (a == 192 && b == 168) return true
            if (a == 172 && b in 16..31) return true
        }
        return try {
            val addr = InetAddress.getByName(lower)
            addr.isLoopbackAddress || addr.isSiteLocalAddress
        } catch (_: Exception) {
            false
        }
    }
}
