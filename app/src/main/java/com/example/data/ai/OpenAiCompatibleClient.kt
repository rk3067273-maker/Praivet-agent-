package com.example.data.ai

import android.graphics.Bitmap
import android.util.Base64
import com.example.data.security.PromptSecurityGuard
import com.example.data.security.SecretRedactor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ProviderRequestConfig(
    val provider: AiProviderId,
    val baseUrl: String,
    val modelId: String,
    val apiKey: String?,
    val maxTokens: Int,
    val temperature: Float
)

data class ConnectionTestResult(
    val success: Boolean,
    val httpStatusCode: Int?,
    val latencyMs: Long,
    val normalizedChatUrl: String,
    val discoveredModels: List<String>,
    val category: HttpErrorCategory? = null,
    val message: String
)

data class PendingConfirmationTool(
    val legacyActionToken: String,
    val toolName: String,
    val summary: String
)

data class ChatTurnOutcome(
    val text: String,
    val reasoningText: String = "",
    val finishReason: String? = null,
    val usage: TokenUsageSnapshot? = null,
    val rateLimitSummary: String? = null,
    val executedToolLabels: List<String> = emptyList(),
    val pendingConfirmationTool: PendingConfirmationTool? = null,
    val toolLimitationNotice: String? = null
)

fun interface ToolExecutionHandler {
    suspend fun executeValidatedTool(
        toolName: String,
        normalizedArgs: Map<String, Any?>
    ): Result<String>
}

class OpenAiCompatibleClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()
) {

    fun streamChatCompletion(
        config: ProviderRequestConfig,
        systemPrompt: String,
        history: List<Pair<String, String>>,
        userPrompt: String,
        bitmap: Bitmap? = null
    ): Flow<StreamChunkDelta> = flow {
        val (endpoint, cleanKey, capability) = validateAndResolve(config, bitmap)
        val effectiveMaxTokens = config.maxTokens.coerceIn(64, capability.maxOutputTokens)
        val messages = buildInitialMessages(systemPrompt, history, userPrompt, bitmap, capability)
        val requestBodyJson = buildRequestBodyJson(
            provider = config.provider,
            capability = capability,
            messages = messages,
            maxTokens = effectiveMaxTokens,
            temperature = config.temperature,
            stream = capability.supportsStreaming,
            includeTools = false
        )
        executeStreamingRequest(endpoint, cleanKey, config.provider, requestBodyJson, effectiveMaxTokens) { delta ->
            emit(delta)
        }
    }.flowOn(Dispatchers.IO)

    suspend fun runChatWithTools(
        config: ProviderRequestConfig,
        systemPrompt: String,
        history: List<Pair<String, String>>,
        userPrompt: String,
        bitmap: Bitmap? = null,
        toolHandler: ToolExecutionHandler,
        onStreamDelta: suspend (String) -> Unit
    ): ChatTurnOutcome = withContext(Dispatchers.IO) {
        val (endpoint, cleanKey, capability) = validateAndResolve(config, bitmap)
        val effectiveMaxTokens = config.maxTokens.coerceIn(64, capability.maxOutputTokens)
        val messages = buildInitialMessages(systemPrompt, history, userPrompt, bitmap, capability).toMutableList()
        val guard = ToolExecutionGuard(AiToolRegistry.MAX_TOOL_ROUNDS)

        val includeTools = capability.supportsToolCalling
        val toolNote = if (!includeTools) {
            ModelCapabilityRegistry.toolCallingLimitationReason(config.provider, capability)
        } else {
            null
        }

        val executedLabels = mutableListOf<String>()
        var pendingConfirm: PendingConfirmationTool? = null
        var lastRateLimit: String? = null
        var promptTokensSum = 0
        var completionTokensSum = 0
        var anyExactUsage = false

        while (true) {
            val bodyJson = buildRequestBodyJson(
                provider = config.provider,
                capability = capability,
                messages = messages,
                maxTokens = effectiveMaxTokens,
                temperature = config.temperature,
                stream = capability.supportsStreaming,
                includeTools = includeTools
            )

            val streamedResult = executeStreamingRequest(
                endpoint = endpoint,
                cleanKey = cleanKey,
                provider = config.provider,
                requestBodyJson = bodyJson,
                effectiveMaxTokens = effectiveMaxTokens,
                allowEmptyIfToolCalls = true,
                onRateLimitHeader = { rl -> if (rl != null) lastRateLimit = rl },
                onEmitDelta = { delta ->
                    val visible = if (delta.textDelta.isNotEmpty()) delta.textDelta else delta.reasoningDelta
                    if (visible.isNotEmpty()) {
                        onStreamDelta(visible)
                    }
                }
            )

            streamedResult.usage?.let { u ->
                promptTokensSum += u.promptTokens
                completionTokensSum += u.completionTokens
                if (u.isExact) anyExactUsage = true
            }

            if (streamedResult.toolCalls.isEmpty()) {
                val finalText = streamedResult.text.ifBlank { streamedResult.reasoningText }.trim()
                if (finalText.isBlank() && executedLabels.isEmpty() && pendingConfirm == null) {
                    throw IllegalStateException(
                        if (streamedResult.finishReason == "length") {
                            "Token budget ($effectiveMaxTokens) exhausted before final output."
                        } else {
                            "Provider returned an empty response."
                        }
                    )
                }
                return@withContext ChatTurnOutcome(
                    text = finalText,
                    reasoningText = streamedResult.reasoningText,
                    finishReason = streamedResult.finishReason,
                    usage = if (anyExactUsage) {
                        TokenUsageSnapshot(
                            promptTokens = promptTokensSum,
                            completionTokens = completionTokensSum,
                            totalTokens = promptTokensSum + completionTokensSum,
                            isExact = true
                        )
                    } else {
                        null
                    },
                    rateLimitSummary = lastRateLimit,
                    executedToolLabels = executedLabels,
                    pendingConfirmationTool = pendingConfirm,
                    toolLimitationNotice = toolNote
                )
            }

            // Record assistant tool_calls turn in message list
            messages.add(
                mapOf(
                    "role" to "assistant",
                    "content" to streamedResult.text.ifBlank { null },
                    "tool_calls" to streamedResult.toolCalls.map { tc ->
                        mapOf(
                            "id" to tc.id,
                            "type" to "function",
                            "function" to mapOf(
                                "name" to tc.name,
                                "arguments" to tc.rawArgumentsJson
                            )
                        )
                    }
                )
            )

            val roundSignatures = mutableListOf<String>()
            val validatedCalls = mutableListOf<Pair<ParsedToolCall, ToolValidationResult>>()

            for (tc in streamedResult.toolCalls) {
                val validation = AiToolRegistry.validateToolCall(tc.name, tc.rawArgumentsJson)
                validatedCalls.add(tc to validation)
                if (validation is ToolValidationResult.Valid) {
                    roundSignatures.add(validation.canonicalSignature)
                } else {
                    roundSignatures.add("rejected:${tc.name}:${tc.rawArgumentsJson.take(64)}")
                }
            }

            when (val loopCheck = guard.checkAndRecordRound(roundSignatures)) {
                is ToolLoopCheckResult.Blocked -> {
                    val safeSummary = if (executedLabels.isNotEmpty()) {
                        "Completed actions (${executedLabels.joinToString()}), then halted: ${loopCheck.reason}"
                    } else {
                        loopCheck.reason
                    }
                    return@withContext ChatTurnOutcome(
                        text = safeSummary,
                        finishReason = "tool_loop_guard",
                        usage = if (anyExactUsage) {
                            TokenUsageSnapshot(promptTokensSum, completionTokensSum, promptTokensSum + completionTokensSum, true)
                        } else null,
                        rateLimitSummary = lastRateLimit,
                        executedToolLabels = executedLabels,
                        pendingConfirmationTool = pendingConfirm,
                        toolLimitationNotice = toolNote
                    )
                }
                ToolLoopCheckResult.Allowed -> Unit
            }

            for ((tc, validation) in validatedCalls) {
                val toolOutputPayload = when (validation) {
                    is ToolValidationResult.Rejected -> {
                        JsonCodec.toJson(mapOf("ok" to false, "error" to validation.reason))
                    }
                    is ToolValidationResult.Valid -> {
                        if (validation.spec.requiresConfirmation) {
                            val token = AiToolRegistry.toLegacyActionToken(
                                validation.spec.name,
                                validation.normalizedArgs
                            )
                            if (token != null && pendingConfirm == null) {
                                pendingConfirm = PendingConfirmationTool(
                                    legacyActionToken = token,
                                    toolName = validation.spec.name,
                                    summary = "${validation.spec.name}(${JsonCodec.toJson(validation.normalizedArgs)})"
                                )
                            }
                            JsonCodec.toJson(
                                mapOf(
                                    "ok" to false,
                                    "status" to "awaiting_operator_confirmation",
                                    "message" to "Sensitive action '${validation.spec.name}' paused for user authorization dialog."
                                )
                            )
                        } else {
                            val execResult = try {
                                withTimeout(AiToolRegistry.TOOL_TIMEOUT_MS) {
                                    toolHandler.executeValidatedTool(
                                        validation.spec.name,
                                        validation.normalizedArgs
                                    )
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Result.failure(e)
                            }
                            execResult.fold(
                                onSuccess = { msg ->
                                    val safeMsg = PromptSecurityGuard.sanitizeUntrustedText(msg, 400)
                                    executedLabels.add("${validation.spec.name}: $safeMsg")
                                    JsonCodec.toJson(mapOf("ok" to true, "result" to safeMsg))
                                },
                                onFailure = { err ->
                                    val safeErr = PromptSecurityGuard.sanitizeUntrustedText(
                                        err.localizedMessage ?: "Tool execution failed.",
                                        250
                                    )
                                    JsonCodec.toJson(mapOf("ok" to false, "error" to safeErr))
                                }
                            )
                        }
                    }
                }

                messages.add(
                    mapOf(
                        "role" to "tool",
                        "tool_call_id" to tc.id,
                        "content" to PromptSecurityGuard.wrapUntrustedData("tool_result_${tc.name}", toolOutputPayload, 800)
                    )
                )
            }
        }
        @Suppress("UNREACHABLE_CODE")
        throw IllegalStateException("Unreachable tool loop exit")
    }

    private suspend fun executeStreamingRequest(
        endpoint: NormalizedEndpointResult.Valid,
        cleanKey: String,
        provider: AiProviderId,
        requestBodyJson: String,
        effectiveMaxTokens: Int,
        allowEmptyIfToolCalls: Boolean = false,
        onRateLimitHeader: (String?) -> Unit = {},
        onEmitDelta: suspend (StreamChunkDelta) -> Unit
    ): ParsedCompletionResult {
        val knownSecrets = if (cleanKey.isNotEmpty()) listOf(cleanKey) else emptyList()

        return withTimeout(60_000L) {
            var attempt = 0
            val maxRetries = 2
            var emittedAnyToken = false

            while (true) {
                val requestBuilder = Request.Builder()
                    .url(endpoint.chatCompletionsUrl)
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .post(requestBodyJson.toRequestBody("application/json".toMediaType()))

                if (cleanKey.isNotEmpty()) {
                    requestBuilder.header("Authorization", "Bearer $cleanKey")
                }

                try {
                    var retryNeeded = false
                    var retryDelayMs = 0L
                    var completedResult: ParsedCompletionResult? = null

                    httpClient.newCall(requestBuilder.build()).execute().use { response ->
                        onRateLimitHeader(extractRateLimit(response))
                        if (!response.isSuccessful) {
                            val errBody = response.body?.string()
                            val mapped = HttpErrorHandler.mapHttpError(
                                statusCode = response.code,
                                rawBody = errBody,
                                provider = provider,
                                retryAfterHeader = response.header("Retry-After"),
                                knownSecrets = knownSecrets
                            )
                            if (!emittedAnyToken && attempt < maxRetries && HttpErrorHandler.isRetryableStatus(response.code)) {
                                retryDelayMs = HttpErrorHandler.computeRetryDelayMs(attempt, mapped.retryAfterSeconds)
                                attempt++
                                retryNeeded = true
                                return@use
                            }
                            throw mapped
                        }

                        val body = response.body
                            ?: throw IllegalStateException("Provider returned an empty HTTP body.")

                        val contentType = response.header("Content-Type").orEmpty().lowercase()
                        if (contentType.contains("application/json")) {
                            val raw = body.string()
                            val parsed = SseStreamParser.parseNonStreamingJson(raw)
                            val hasContent = parsed.text.isNotBlank() || parsed.reasoningText.isNotBlank()
                            if (!hasContent && !(allowEmptyIfToolCalls && parsed.toolCalls.isNotEmpty())) {
                                throw IllegalStateException(
                                    if (parsed.finishReason == "length") {
                                        "Completion budget exhausted before output was produced. Increase Max Tokens."
                                    } else {
                                        "Provider returned an empty completion."
                                    }
                                )
                            }
                            emittedAnyToken = true
                            onEmitDelta(
                                StreamChunkDelta(
                                    textDelta = parsed.text,
                                    reasoningDelta = parsed.reasoningText,
                                    usage = parsed.usage,
                                    finishReason = parsed.finishReason,
                                    isDone = true
                                )
                            )
                            completedResult = parsed
                        } else {
                            val result = SseStreamParser.consumeSseStream(body.source()) { delta ->
                                if (delta.textDelta.isNotEmpty() || delta.reasoningDelta.isNotEmpty()) {
                                    emittedAnyToken = true
                                }
                                onEmitDelta(delta)
                            }
                            val hasContent = result.text.isNotBlank() || result.reasoningText.isNotBlank()
                            if (!hasContent && !(allowEmptyIfToolCalls && result.toolCalls.isNotEmpty())) {
                                throw IllegalStateException(
                                    if (result.finishReason == "length") {
                                        "Token budget ($effectiveMaxTokens) exhausted during reasoning before output. Increase Max Tokens."
                                    } else {
                                        "Stream closed with an empty response."
                                    }
                                )
                            }
                            completedResult = result
                        }
                    }

                    if (retryNeeded) {
                        delay(retryDelayMs)
                        continue
                    }
                    return@withTimeout completedResult!!
                } catch (e: CancellationException) {
                    throw e
                } catch (e: AiHttpException) {
                    throw e
                } catch (e: IOException) {
                    if (!emittedAnyToken && attempt < maxRetries) {
                        val delayMs = HttpErrorHandler.computeRetryDelayMs(attempt, null)
                        attempt++
                        delay(delayMs)
                        continue
                    }
                    throw IOException(
                        SecretRedactor.redact(
                            e.localizedMessage ?: "Network communication failed.",
                            knownSecrets
                        )
                    )
                }
            }
            @Suppress("UNREACHABLE_CODE")
            throw IllegalStateException("Unreachable")
        }
    }

    suspend fun fetchAvailableModels(
        baseUrl: String,
        apiKey: String?
    ): List<String> = withContext(Dispatchers.IO) {
        val norm = EndpointUrlNormalizer.normalizeOpenAiCompatible(baseUrl)
        if (norm !is NormalizedEndpointResult.Valid) return@withContext emptyList()
        val reqBuilder = Request.Builder()
            .url(norm.modelsUrl)
            .get()
        val trimmedKey = apiKey?.trim().orEmpty()
        if (trimmedKey.isNotEmpty()) {
            reqBuilder.header("Authorization", "Bearer $trimmedKey")
        }
        try {
            httpClient.newCall(reqBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val raw = response.body?.string().orEmpty()
                val root = JsonCodec.parseObjectOrNull(raw) ?: return@withContext emptyList()
                val data = root["data"] as? List<*> ?: return@withContext emptyList()
                data.mapNotNull { entry ->
                    (entry as? Map<*, *>)?.get("id") as? String
                }.filter { it.isNotBlank() }.sorted()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun testConnection(config: ProviderRequestConfig): ConnectionTestResult = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        val endpoint = when (val norm = EndpointUrlNormalizer.normalizeOpenAiCompatible(config.baseUrl)) {
            is NormalizedEndpointResult.Valid -> norm
            is NormalizedEndpointResult.Invalid -> {
                return@withContext ConnectionTestResult(
                    success = false,
                    httpStatusCode = null,
                    latencyMs = 0L,
                    normalizedChatUrl = config.baseUrl,
                    discoveredModels = emptyList(),
                    message = norm.reason
                )
            }
        }

        val cleanKey = config.apiKey?.trim().orEmpty()
        if (config.provider.requiresApiKey && cleanKey.isEmpty()) {
            return@withContext ConnectionTestResult(
                success = false,
                httpStatusCode = 401,
                latencyMs = 0L,
                normalizedChatUrl = endpoint.chatCompletionsUrl,
                discoveredModels = emptyList(),
                category = HttpErrorCategory.AUTHENTICATION,
                message = "API key is missing for ${config.provider.displayName}."
            )
        }

        val validation = ModelCapabilityRegistry.validateModelId(config.provider, config.modelId)
        val capability = when (validation) {
            is ModelValidationResult.Valid -> validation.capability
            is ModelValidationResult.Invalid -> {
                return@withContext ConnectionTestResult(
                    success = false,
                    httpStatusCode = null,
                    latencyMs = 0L,
                    normalizedChatUrl = endpoint.chatCompletionsUrl,
                    discoveredModels = emptyList(),
                    message = validation.reason
                )
            }
        }

        val discoveredModels = if (config.provider.supportsModelsEndpoint) {
            fetchAvailableModels(config.baseUrl, cleanKey)
        } else {
            emptyList()
        }

        val testBodyJson = buildRequestBodyJson(
            provider = config.provider,
            capability = capability,
            messages = buildInitialMessages("Respond with OK.", emptyList(), "ping", null, capability),
            maxTokens = 16,
            temperature = config.temperature,
            stream = false,
            includeTools = false
        )

        val requestBuilder = Request.Builder()
            .url(endpoint.chatCompletionsUrl)
            .header("Content-Type", "application/json")
            .post(testBodyJson.toRequestBody("application/json".toMediaType()))
        if (cleanKey.isNotEmpty()) {
            requestBuilder.header("Authorization", "Bearer $cleanKey")
        }

        val knownSecrets = if (cleanKey.isNotEmpty()) listOf(cleanKey) else emptyList()

        try {
            withTimeout(25_000L) {
                httpClient.newCall(requestBuilder.build()).execute().use { response ->
                    val latency = System.currentTimeMillis() - startMs
                    val rawBody = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val mapped = HttpErrorHandler.mapHttpError(
                            statusCode = response.code,
                            rawBody = rawBody,
                            provider = config.provider,
                            retryAfterHeader = response.header("Retry-After"),
                            knownSecrets = knownSecrets
                        )
                        return@withTimeout ConnectionTestResult(
                            success = false,
                            httpStatusCode = response.code,
                            latencyMs = latency,
                            normalizedChatUrl = endpoint.chatCompletionsUrl,
                            discoveredModels = discoveredModels,
                            category = mapped.category,
                            message = mapped.userMessage
                        )
                    }

                    val parsed = SseStreamParser.parseNonStreamingJson(rawBody)
                    val modelNotice = if (discoveredModels.isNotEmpty() &&
                        discoveredModels.none { it.equals(capability.modelId, ignoreCase = true) }
                    ) {
                        " (Note: '${capability.modelId}' not in /models list)"
                    } else {
                        ""
                    }

                    ConnectionTestResult(
                        success = true,
                        httpStatusCode = response.code,
                        latencyMs = latency,
                        normalizedChatUrl = endpoint.chatCompletionsUrl,
                        discoveredModels = discoveredModels,
                        message = "Connected (${response.code}) in ${latency}ms • Reply: \"${parsed.text.trim().take(32)}\"$modelNotice"
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startMs
            ConnectionTestResult(
                success = false,
                httpStatusCode = null,
                latencyMs = latency,
                normalizedChatUrl = endpoint.chatCompletionsUrl,
                discoveredModels = discoveredModels,
                message = SecretRedactor.redact(
                    e.localizedMessage ?: "Connection test failed.",
                    knownSecrets
                )
            )
        }
    }

    private fun validateAndResolve(
        config: ProviderRequestConfig,
        bitmap: Bitmap?
    ): Triple<NormalizedEndpointResult.Valid, String, ModelCapability> {
        val endpoint = when (val norm = EndpointUrlNormalizer.normalizeOpenAiCompatible(config.baseUrl)) {
            is NormalizedEndpointResult.Valid -> norm
            is NormalizedEndpointResult.Invalid -> throw IllegalArgumentException(norm.reason)
        }

        val cleanKey = config.apiKey?.trim().orEmpty()
        if (config.provider.requiresApiKey && cleanKey.isEmpty()) {
            throw AiHttpException(
                statusCode = 401,
                category = HttpErrorCategory.AUTHENTICATION,
                userMessage = "${config.provider.displayName} API key is missing. Add it in Agent Settings."
            )
        }

        val validation = ModelCapabilityRegistry.validateModelId(config.provider, config.modelId)
        val capability = when (validation) {
            is ModelValidationResult.Valid -> validation.capability
            is ModelValidationResult.Invalid -> throw IllegalArgumentException(validation.reason)
        }

        if (bitmap != null && !capability.supportsVision) {
            throw IllegalArgumentException(
                "Model '${capability.modelId}' does not advertise vision/image input support."
            )
        }
        return Triple(endpoint, cleanKey, capability)
    }

    private fun extractRateLimit(response: Response): String? {
        return HttpErrorHandler.formatRateLimitSummary(
            remainingRequests = response.header("x-ratelimit-remaining-requests"),
            remainingTokens = response.header("x-ratelimit-remaining-tokens"),
            retryAfterHeader = response.header("Retry-After")
        )
    }

    private fun buildInitialMessages(
        systemPrompt: String,
        history: List<Pair<String, String>>,
        userPrompt: String,
        bitmap: Bitmap?,
        capability: ModelCapability
    ): List<Map<String, Any?>> {
        val messages = mutableListOf<Map<String, Any?>>()
        if (systemPrompt.isNotBlank()) {
            messages.add(mapOf("role" to "system", "content" to systemPrompt))
        }
        history.takeLast(8).forEach { (role, text) ->
            val mappedRole = if (role == "user") "user" else "assistant"
            messages.add(mapOf("role" to mappedRole, "content" to text))
        }
        if (bitmap != null && capability.supportsVision) {
            val dataUrl = encodeBitmapDataUrl(bitmap)
            val parts = listOf(
                mapOf("type" to "text", "text" to userPrompt),
                mapOf("type" to "image_url", "image_url" to mapOf("url" to dataUrl))
            )
            messages.add(mapOf("role" to "user", "content" to parts))
        } else {
            messages.add(mapOf("role" to "user", "content" to userPrompt))
        }
        return messages
    }

    internal fun buildRequestBodyJson(
        provider: AiProviderId,
        capability: ModelCapability,
        messages: List<Map<String, Any?>>,
        maxTokens: Int,
        temperature: Float,
        stream: Boolean,
        includeTools: Boolean
    ): String {
        val body = linkedMapOf<String, Any?>(
            "model" to capability.modelId,
            "messages" to messages,
            "stream" to stream
        )
        if (stream && (provider == AiProviderId.OPENAI || provider == AiProviderId.OPENROUTER)) {
            body["stream_options"] = mapOf("include_usage" to true)
        }
        if (capability.usesMaxCompletionTokens) {
            body["max_completion_tokens"] = maxTokens
        } else {
            body["max_tokens"] = maxTokens
        }
        if (capability.supportsTemperature) {
            body["temperature"] = temperature.toDouble()
        }
        if (includeTools && capability.supportsToolCalling) {
            body["tools"] = AiToolRegistry.toOpenAiToolsPayload()
            body["tool_choice"] = "auto"
        }
        return JsonCodec.toJson(body)
    }

    private fun encodeBitmapDataUrl(bitmap: Bitmap): String {
        val maxDim = 1024
        val w = bitmap.width
        val h = bitmap.height
        val scaled = if (w > maxDim || h > maxDim) {
            val ratio = minOf(maxDim.toFloat() / w, maxDim.toFloat() / h)
            Bitmap.createScaledBitmap(
                bitmap,
                (w * ratio).toInt().coerceAtLeast(1),
                (h * ratio).toInt().coerceAtLeast(1),
                true
            )
        } else {
            bitmap
        }
        val stream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 82, stream)
        val b64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        return "data:image/jpeg;base64,$b64"
    }
}
