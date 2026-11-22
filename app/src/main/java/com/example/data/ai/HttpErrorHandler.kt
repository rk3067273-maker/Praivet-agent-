package com.example.data.ai

import com.example.data.security.SecretRedactor
import kotlin.math.min
import kotlin.random.Random

enum class HttpErrorCategory {
    INVALID_REQUEST,
    AUTHENTICATION,
    AGENTROUTER_CLIENT_POLICY,
    QUOTA_EXHAUSTED,
    FORBIDDEN,
    NOT_FOUND_OR_MODEL,
    TIMEOUT,
    PAYLOAD_TOO_LARGE,
    RATE_LIMIT,
    SERVER_ERROR,
    GATEWAY_OR_UNAVAILABLE,
    UNKNOWN
}

class AiHttpException(
    val statusCode: Int,
    val category: HttpErrorCategory,
    val userMessage: String,
    val retryAfterSeconds: Long? = null
) : Exception("HTTP $statusCode ($category): $userMessage")

object HttpErrorHandler {

    fun isRetryableStatus(statusCode: Int): Boolean {
        return statusCode == 408 || statusCode == 429 || statusCode in listOf(500, 502, 503, 504)
    }

    fun computeRetryDelayMs(
        attempt: Int,
        retryAfterSeconds: Long?,
        baseDelayMs: Long = 700L,
        maxDelayMs: Long = 10_000L
    ): Long {
        if (retryAfterSeconds != null && retryAfterSeconds > 0) {
            return min(retryAfterSeconds * 1000L, 15_000L)
        }
        val exponential = baseDelayMs * (1L shl attempt.coerceIn(0, 4))
        val jitter = Random.nextLong(50L, 350L)
        return min(exponential + jitter, maxDelayMs)
    }

    fun parseRetryAfterSeconds(headerValue: String?): Long? {
        return headerValue?.trim()?.toLongOrNull()?.takeIf { it > 0 }
    }

    fun formatRateLimitSummary(
        remainingRequests: String?,
        remainingTokens: String?,
        retryAfterHeader: String? = null
    ): String? {
        val parts = mutableListOf<String>()
        remainingRequests?.trim()?.takeIf { it.isNotEmpty() }?.let { parts.add("Req left: $it") }
        remainingTokens?.trim()?.takeIf { it.isNotEmpty() }?.let { parts.add("Tok left: $it") }
        parseRetryAfterSeconds(retryAfterHeader)?.let { parts.add("Retry-After: ${it}s") }
        return if (parts.isEmpty()) null else parts.joinToString(" • ")
    }

    fun mapHttpError(
        statusCode: Int,
        rawBody: String?,
        provider: AiProviderId,
        retryAfterHeader: String?,
        knownSecrets: List<String> = emptyList()
    ): AiHttpException {
        val extractedDetail = extractSafeErrorMessage(rawBody, knownSecrets)
        val detailLower = extractedDetail.lowercase()
        val retryAfter = parseRetryAfterSeconds(retryAfterHeader)

        return when (statusCode) {
            400 -> AiHttpException(
                statusCode = 400,
                category = HttpErrorCategory.INVALID_REQUEST,
                userMessage = buildMessage(
                    "Invalid request parameters (HTTP 400). Check model parameters or token budget.",
                    extractedDetail
                )
            )
            401 -> {
                if (detailLower.contains("unauthorized client") ||
                    (provider == AiProviderId.AGENTROUTER && detailLower.contains("client"))
                ) {
                    AiHttpException(
                        statusCode = 401,
                        category = HttpErrorCategory.AGENTROUTER_CLIENT_POLICY,
                        userMessage = "HTTP 401: Unauthorized client detected. AgentRouter restricts direct /v1 access to approved CLI clients (such as Claude Code or Codex CLI). Use your own authorized local/private gateway or select OpenRouter, DeepSeek, OpenAI, or Gemini."
                    )
                } else {
                    AiHttpException(
                        statusCode = 401,
                        category = HttpErrorCategory.AUTHENTICATION,
                        userMessage = "Authentication failed (HTTP 401). Verify your ${provider.displayName} API key in Agent Settings."
                    )
                }
            }
            402 -> AiHttpException(
                statusCode = 402,
                category = HttpErrorCategory.QUOTA_EXHAUSTED,
                userMessage = buildMessage(
                    "${provider.displayName} prepaid credit/balance exhausted (HTTP 402). Stopped cleanly; automatic recharge and paid fallbacks are disabled.",
                    extractedDetail
                )
            )
            403 -> AiHttpException(
                statusCode = 403,
                category = HttpErrorCategory.FORBIDDEN,
                userMessage = buildMessage(
                    "Access forbidden (HTTP 403). Your account, region, or client lacks permission for this endpoint or model.",
                    extractedDetail
                )
            )
            404 -> AiHttpException(
                statusCode = 404,
                category = HttpErrorCategory.NOT_FOUND_OR_MODEL,
                userMessage = buildMessage(
                    "Endpoint or model not found (HTTP 404). Verify the Base URL and Model ID.",
                    extractedDetail
                )
            )
            408 -> AiHttpException(
                statusCode = 408,
                category = HttpErrorCategory.TIMEOUT,
                userMessage = "Request timed out at provider (HTTP 408). Retry shortly."
            )
            413 -> AiHttpException(
                statusCode = 413,
                category = HttpErrorCategory.PAYLOAD_TOO_LARGE,
                userMessage = "Payload too large (HTTP 413). Reduce conversation history or image attachment size."
            )
            429 -> {
                val isInsufficientQuota = detailLower.contains("insufficient_quota") ||
                    detailLower.contains("exceeded your current quota")
                if (isInsufficientQuota) {
                    AiHttpException(
                        statusCode = 429,
                        category = HttpErrorCategory.QUOTA_EXHAUSTED,
                        userMessage = buildMessage(
                            "${provider.displayName} billing quota exhausted (HTTP 429). Stopped cleanly; automatic recharge or paid provider fallback is disabled.",
                            extractedDetail
                        ),
                        retryAfterSeconds = retryAfter
                    )
                } else {
                    AiHttpException(
                        statusCode = 429,
                        category = HttpErrorCategory.RATE_LIMIT,
                        userMessage = buildMessage(
                            "Rate limit exceeded (HTTP 429)." +
                                if (retryAfter != null) " Retry after ${retryAfter}s." else "",
                            extractedDetail
                        ),
                        retryAfterSeconds = retryAfter
                    )
                }
            }
            500 -> AiHttpException(
                statusCode = 500,
                category = HttpErrorCategory.SERVER_ERROR,
                userMessage = "Provider internal server error (HTTP 500). Please retry."
            )
            502 -> AiHttpException(
                statusCode = 502,
                category = HttpErrorCategory.GATEWAY_OR_UNAVAILABLE,
                userMessage = "Upstream gateway error (HTTP 502) from ${provider.displayName}."
            )
            503 -> AiHttpException(
                statusCode = 503,
                category = HttpErrorCategory.GATEWAY_OR_UNAVAILABLE,
                userMessage = "Model temporarily unavailable or provider overloaded (HTTP 503).",
                retryAfterSeconds = retryAfter
            )
            504 -> AiHttpException(
                statusCode = 504,
                category = HttpErrorCategory.TIMEOUT,
                userMessage = "Upstream gateway timeout (HTTP 504)."
            )
            else -> AiHttpException(
                statusCode = statusCode,
                category = HttpErrorCategory.UNKNOWN,
                userMessage = buildMessage("Provider returned HTTP $statusCode.", extractedDetail)
            )
        }
    }

    internal fun extractSafeErrorMessage(rawBody: String?, knownSecrets: List<String>): String {
        val json = JsonCodec.parseObjectOrNull(rawBody) ?: return ""
        val extracted = when (val err = json["error"]) {
            is Map<*, *> -> (err["message"] as? String)?.ifBlank { err["type"] as? String } ?: ""
            is String -> err
            else -> (json["message"] as? String) ?: (json["detail"] as? String) ?: ""
        }
        val clipped = extracted.trim().take(220)
        return SecretRedactor.redact(clipped, knownSecrets)
    }

    private fun buildMessage(prefix: String, detail: String): String {
        return if (detail.isBlank()) prefix else "$prefix Detail: $detail"
    }
}
