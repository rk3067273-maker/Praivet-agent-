package com.example.data.ai

enum class AiProviderId(
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val requiresApiKey: Boolean,
    val supportsModelsEndpoint: Boolean,
    val maxCompletionTokensCap: Int,
    val notes: String
) {
    GEMINI(
        displayName = "Gemini (Firebase AI)",
        defaultBaseUrl = "",
        defaultModel = "gemini-3.8-flash",
        requiresApiKey = false,
        supportsModelsEndpoint = false,
        maxCompletionTokensCap = 8192,
        notes = "Managed via Firebase AI Logic & App Check (no API key required)."
    ),
    OPENAI(
        displayName = "OpenAI",
        defaultBaseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-4o-mini",
        requiresApiKey = true,
        supportsModelsEndpoint = true,
        maxCompletionTokensCap = 16384,
        notes = "Official OpenAI Chat Completions API."
    ),
    DEEPSEEK(
        displayName = "DeepSeek",
        defaultBaseUrl = "https://api.deepseek.com/v1",
        defaultModel = "deepseek-chat",
        requiresApiKey = true,
        supportsModelsEndpoint = true,
        maxCompletionTokensCap = 8192,
        notes = "Supports deepseek-chat and deepseek-reasoner (R1)."
    ),
    OPENROUTER(
        displayName = "OpenRouter",
        defaultBaseUrl = "https://openrouter.ai/api/v1",
        defaultModel = "openai/gpt-4o-mini",
        requiresApiKey = true,
        supportsModelsEndpoint = true,
        maxCompletionTokensCap = 16384,
        notes = "Unified multi-provider OpenAI-compatible router."
    ),
    NVIDIA_NIM(
        displayName = "NVIDIA NIM",
        defaultBaseUrl = "https://integrate.api.nvidia.com/v1",
        defaultModel = "meta/llama-3.1-70b-instruct",
        requiresApiKey = true,
        supportsModelsEndpoint = true,
        maxCompletionTokensCap = 8192,
        notes = "NVIDIA NIM OpenAI-compatible inference gateway."
    ),
    AGENTROUTER(
        displayName = "AgentRouter",
        defaultBaseUrl = "https://agentrouter.org/v1",
        defaultModel = "deepseek-v3",
        requiresApiKey = true,
        supportsModelsEndpoint = true,
        maxCompletionTokensCap = 8192,
        notes = "AgentRouter enforces approved-client allowlisting directly; direct generic mobile calls may receive HTTP 401 unless routed via your own authorized proxy."
    ),
    OLLAMA(
        displayName = "Local Ollama",
        defaultBaseUrl = "http://127.0.0.1:11434",
        defaultModel = "llama3.2",
        requiresApiKey = false,
        supportsModelsEndpoint = true,
        maxCompletionTokensCap = 8192,
        notes = "100% local-first inference on loopback or local LAN."
    ),
    CUSTOM(
        displayName = "Custom OpenAI-Compatible",
        defaultBaseUrl = "https://api.example.com/v1",
        defaultModel = "gpt-4o-mini",
        requiresApiKey = true,
        supportsModelsEndpoint = true,
        maxCompletionTokensCap = 8192,
        notes = "Any HTTPS OpenAI-compatible /v1/chat/completions endpoint."
    );

    companion object {
        fun fromName(raw: String?): AiProviderId {
            return entries.firstOrNull { it.name == raw } ?: GEMINI
        }
    }
}

data class ModelCapability(
    val modelId: String,
    val contextWindowTokens: Int,
    val maxOutputTokens: Int,
    val usesMaxCompletionTokens: Boolean = false,
    val supportsTemperature: Boolean = true,
    val supportsStreaming: Boolean = true,
    val supportsJsonMode: Boolean = true,
    val supportsVision: Boolean = false,
    val supportsToolCalling: Boolean = true,
    val isReasoningModel: Boolean = false
)

sealed interface ModelValidationResult {
    data class Valid(
        val modelId: String,
        val capability: ModelCapability,
        val warning: String? = null
    ) : ModelValidationResult

    data class Invalid(val reason: String) : ModelValidationResult
}

object ModelCapabilityRegistry {

    private val modelIdPattern = Regex("""^[A-Za-z0-9._:/\-@]{2,128}$""")

    fun resolveCapability(provider: AiProviderId, rawModelId: String): ModelCapability {
        val id = rawModelId.trim()
        val lower = id.lowercase()

        val isOpenAiReasoning = lower.startsWith("o1") ||
            lower.startsWith("o3") ||
            lower.startsWith("o4") ||
            lower.startsWith("gpt-5")
        val isDeepSeekReasoner = lower.contains("deepseek-reasoner") || lower.contains("deepseek-r1")

        val usesMaxCompletion = (provider == AiProviderId.OPENAI || provider == AiProviderId.OPENROUTER) &&
            isOpenAiReasoning

        val supportsTemp = !(isOpenAiReasoning || isDeepSeekReasoner)
        val supportsVision = when {
            provider == AiProviderId.GEMINI -> true
            lower.contains("gpt-4o") || lower.contains("gpt-4.1") || lower.contains("gpt-5") -> true
            lower.contains("claude-3") || lower.contains("claude-sonnet-4") || lower.contains("gemini") -> true
            lower.contains("llava") || lower.contains("vision") || lower.contains("qwen2.5-vl") -> true
            else -> false
        }

        val supportsTools = when {
            isDeepSeekReasoner || lower.startsWith("o1-mini") -> false
            provider == AiProviderId.OLLAMA -> {
                lower.contains("llama3.1") || lower.contains("llama3.2") ||
                    lower.contains("llama3.3") || lower.contains("qwen2.5") ||
                    lower.contains("mistral")
            }
            provider == AiProviderId.GEMINI -> false // Uses structured prompt/Firebase channel; OpenAI tool loop applies to OpenAI-compatible
            else -> true
        }

        val contextWindow = when {
            lower.contains("gemini") -> 1_000_000
            lower.contains("claude") -> 200_000
            lower.contains("gpt-4o") || lower.contains("gpt-4.1") || lower.contains("o3") || lower.contains("o4") -> 128_000
            lower.contains("deepseek") -> 64_000
            lower.contains("llama-3.1") || lower.contains("llama-3.3") -> 128_000
            else -> 32_768
        }

        val maxOut = minOf(provider.maxCompletionTokensCap, if (isOpenAiReasoning) 16384 else 8192)

        return ModelCapability(
            modelId = id,
            contextWindowTokens = contextWindow,
            maxOutputTokens = maxOut,
            usesMaxCompletionTokens = usesMaxCompletion,
            supportsTemperature = supportsTemp,
            supportsStreaming = true,
            supportsJsonMode = provider != AiProviderId.OLLAMA,
            supportsVision = supportsVision,
            supportsToolCalling = supportsTools,
            isReasoningModel = isOpenAiReasoning || isDeepSeekReasoner
        )
    }

    fun toolCallingLimitationReason(provider: AiProviderId, capability: ModelCapability): String? {
        return when {
            provider == AiProviderId.GEMINI ->
                "Gemini Firebase channel uses action-tag dispatch rather than OpenAI tool_calls JSON."
            !capability.supportsToolCalling ->
                "${provider.displayName} (${capability.modelId}) does not advertise OpenAI function tool_calls support; structured tools disabled."
            else -> null
        }
    }

    fun validateModelId(
        provider: AiProviderId,
        rawModelId: String,
        discoveredModels: List<String> = emptyList()
    ): ModelValidationResult {
        val trimmed = rawModelId.trim()
        if (trimmed.isEmpty()) {
            return ModelValidationResult.Invalid("Model ID cannot be empty.")
        }
        if (!modelIdPattern.matches(trimmed)) {
            return ModelValidationResult.Invalid(
                "Model ID contains invalid whitespace or unsupported characters."
            )
        }
        if (provider == AiProviderId.GEMINI && !trimmed.startsWith("gemini-")) {
            return ModelValidationResult.Invalid(
                "Firebase AI Logic requires a supported Gemini model ID (e.g. gemini-3.8-flash)."
            )
        }

        val capability = resolveCapability(provider, trimmed)
        val warning = when {
            discoveredModels.isNotEmpty() && discoveredModels.none { it.equals(trimmed, ignoreCase = true) } ->
                "Model '$trimmed' was not listed by ${provider.displayName}'s /models endpoint; the provider may reject it."
            capability.isReasoningModel ->
                "Reasoning model detected: custom temperature is omitted automatically."
            !capability.supportsToolCalling && provider != AiProviderId.GEMINI ->
                "Structured OpenAI tool calling is disabled for '${trimmed}' due to model limitations."
            else -> null
        }

        return ModelValidationResult.Valid(
            modelId = trimmed,
            capability = capability,
            warning = warning
        )
    }
}
