package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.ai.AiProviderId
import com.example.data.ai.EndpointUrlNormalizer
import com.example.data.ai.JsonCodec
import com.example.data.ai.ModelCapabilityRegistry
import com.example.data.ai.ModelValidationResult
import com.example.data.ai.NormalizedEndpointResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.jarvisDataStore: DataStore<Preferences> by preferencesDataStore(name = "jarvis_preferences")

data class ProviderConfig(
    val providerId: AiProviderId,
    val baseUrl: String = providerId.defaultBaseUrl,
    val modelId: String = providerId.defaultModel,
    val maxTokens: Int = 2048,
    val temperature: Float = 0.7f
)

data class JarvisSettings(
    val selectedProvider: AiProviderId = AiProviderId.GEMINI,
    val providerConfigs: Map<AiProviderId, ProviderConfig> = defaultProviderConfigs(),
    val ttsEnabled: Boolean = true,
    val wakeWordEnabled: Boolean = false,
    val airGesturesEnabled: Boolean = false,
    val sentrySensitivity: Float = 3.2f,
    val webhookUrl: String = "",
    val maxAgentSteps: Int = 5,
    val usageWarningTokenLimit: Int = 100_000,
    val wasAiInFlightOnLastExit: Boolean = false
) {
    val activeConfig: ProviderConfig
        get() = providerConfigs[selectedProvider] ?: ProviderConfig(selectedProvider)

    companion object {
        fun defaultProviderConfigs(): Map<AiProviderId, ProviderConfig> {
            return AiProviderId.entries.associateWith { ProviderConfig(it) }
        }
    }
}

sealed interface ConfigSaveResult {
    data object Saved : ConfigSaveResult
    data class ValidationError(val reason: String) : ConfigSaveResult
}

class JarvisPreferences(
    private val dataStore: DataStore<Preferences>
) {
    constructor(context: Context) : this(context.jarvisDataStore)

    private object Keys {
        val SELECTED_PROVIDER = stringPreferencesKey("selected_provider")
        val PROVIDER_CONFIGS_JSON = stringPreferencesKey("provider_configs_json")
        val TTS_ENABLED = booleanPreferencesKey("tts_enabled")
        val WAKE_WORD_ENABLED = booleanPreferencesKey("wake_word_enabled")
        val AIR_GESTURES_ENABLED = booleanPreferencesKey("air_gestures_enabled")
        val SENTRY_SENSITIVITY = floatPreferencesKey("sentry_sensitivity")
        val WEBHOOK_URL = stringPreferencesKey("webhook_url")
        val MAX_AGENT_STEPS = intPreferencesKey("max_agent_steps")
        val USAGE_WARNING_TOKEN_LIMIT = intPreferencesKey("usage_warning_token_limit")
        val AI_IN_FLIGHT = booleanPreferencesKey("ai_in_flight")
    }

    val settings: Flow<JarvisSettings> = dataStore.data.map { prefs ->
        val selected = AiProviderId.fromName(prefs[Keys.SELECTED_PROVIDER])
        val configs = parseProviderConfigs(prefs[Keys.PROVIDER_CONFIGS_JSON])
        JarvisSettings(
            selectedProvider = selected,
            providerConfigs = configs,
            ttsEnabled = prefs[Keys.TTS_ENABLED] ?: true,
            wakeWordEnabled = prefs[Keys.WAKE_WORD_ENABLED] ?: false,
            airGesturesEnabled = prefs[Keys.AIR_GESTURES_ENABLED] ?: false,
            sentrySensitivity = prefs[Keys.SENTRY_SENSITIVITY] ?: 3.2f,
            webhookUrl = prefs[Keys.WEBHOOK_URL] ?: "",
            maxAgentSteps = (prefs[Keys.MAX_AGENT_STEPS] ?: 5).coerceIn(1, 10),
            usageWarningTokenLimit = (prefs[Keys.USAGE_WARNING_TOKEN_LIMIT] ?: 100_000)
                .coerceIn(5_000, 2_000_000),
            wasAiInFlightOnLastExit = prefs[Keys.AI_IN_FLIGHT] ?: false
        )
    }

    suspend fun setProvider(provider: AiProviderId) {
        dataStore.edit { prefs ->
            val current = AiProviderId.fromName(prefs[Keys.SELECTED_PROVIDER])
            if (current == provider) {
                return@edit
            }
            prefs[Keys.SELECTED_PROVIDER] = provider.name
        }
    }

    suspend fun saveProviderConfig(
        provider: AiProviderId,
        baseUrl: String,
        modelId: String,
        maxTokens: Int,
        temperature: Float
    ): ConfigSaveResult {
        val cleanUrl = baseUrl.trim()
        if (provider != AiProviderId.GEMINI) {
            val urlCheck = if (provider == AiProviderId.OLLAMA) {
                EndpointUrlNormalizer.normalizeOllamaBaseUrl(cleanUrl)
            } else {
                EndpointUrlNormalizer.normalizeOpenAiCompatible(cleanUrl)
            }
            if (urlCheck is NormalizedEndpointResult.Invalid) {
                return ConfigSaveResult.ValidationError(urlCheck.reason)
            }
        }

        val modelCheck = ModelCapabilityRegistry.validateModelId(provider, modelId)
        if (modelCheck is ModelValidationResult.Invalid) {
            return ConfigSaveResult.ValidationError(modelCheck.reason)
        }

        val capability = (modelCheck as ModelValidationResult.Valid).capability
        val clampedTokens = maxTokens.coerceIn(64, capability.maxOutputTokens)
        val clampedTemp = temperature.coerceIn(0.0f, 2.0f)

        dataStore.edit { prefs ->
            val map = parseProviderConfigs(prefs[Keys.PROVIDER_CONFIGS_JSON]).toMutableMap()
            val finalUrl = if (provider == AiProviderId.GEMINI) {
                ""
            } else if (provider == AiProviderId.OLLAMA) {
                (EndpointUrlNormalizer.normalizeOllamaBaseUrl(cleanUrl) as NormalizedEndpointResult.Valid)
                    .normalizedBaseUrl
            } else {
                (EndpointUrlNormalizer.normalizeOpenAiCompatible(cleanUrl) as NormalizedEndpointResult.Valid)
                    .normalizedBaseUrl
            }
            map[provider] = ProviderConfig(
                providerId = provider,
                baseUrl = finalUrl,
                modelId = modelCheck.modelId,
                maxTokens = clampedTokens,
                temperature = clampedTemp
            )
            prefs[Keys.PROVIDER_CONFIGS_JSON] = serializeProviderConfigs(map)
        }
        return ConfigSaveResult.Saved
    }

    suspend fun setTtsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.TTS_ENABLED] = enabled }
    }

    suspend fun setWakeWordEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.WAKE_WORD_ENABLED] = enabled }
    }

    suspend fun setAirGesturesEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.AIR_GESTURES_ENABLED] = enabled }
    }

    suspend fun setSentrySensitivity(value: Float) {
        dataStore.edit { it[Keys.SENTRY_SENSITIVITY] = value.coerceIn(1.0f, 10.0f) }
    }

    suspend fun setWebhookUrl(url: String): ConfigSaveResult {
        val trimmed = url.trim()
        if (trimmed.isNotEmpty() && !trimmed.startsWith("https://", ignoreCase = true)) {
            return ConfigSaveResult.ValidationError("Remote alert webhook must use HTTPS.")
        }
        dataStore.edit { it[Keys.WEBHOOK_URL] = trimmed }
        return ConfigSaveResult.Saved
    }

    suspend fun setMaxAgentSteps(steps: Int) {
        dataStore.edit { it[Keys.MAX_AGENT_STEPS] = steps.coerceIn(1, 10) }
    }

    suspend fun setUsageWarningTokenLimit(limit: Int) {
        dataStore.edit { it[Keys.USAGE_WARNING_TOKEN_LIMIT] = limit.coerceIn(5_000, 2_000_000) }
    }

    suspend fun setAiInFlight(inFlight: Boolean) {
        dataStore.edit { it[Keys.AI_IN_FLIGHT] = inFlight }
    }

    suspend fun clearAllPreferences() {
        dataStore.edit { it.clear() }
    }

    internal fun parseProviderConfigs(rawJson: String?): Map<AiProviderId, ProviderConfig> {
        val defaults = JarvisSettings.defaultProviderConfigs().toMutableMap()
        val root = JsonCodec.parseObjectOrNull(rawJson) ?: return defaults
        AiProviderId.entries.forEach { provider ->
            val obj = root[provider.name] as? Map<*, *>
            if (obj != null) {
                val baseUrl = (obj["baseUrl"] as? String) ?: provider.defaultBaseUrl
                val modelId = ((obj["modelId"] as? String) ?: provider.defaultModel)
                    .ifBlank { provider.defaultModel }
                val maxTokens = ((obj["maxTokens"] as? Number)?.toInt() ?: 2048)
                    .coerceIn(64, provider.maxCompletionTokensCap)
                val temperature = ((obj["temperature"] as? Number)?.toFloat() ?: 0.7f)
                    .coerceIn(0.0f, 2.0f)
                defaults[provider] = ProviderConfig(
                    providerId = provider,
                    baseUrl = baseUrl,
                    modelId = modelId,
                    maxTokens = maxTokens,
                    temperature = temperature
                )
            }
        }
        return defaults
    }

    private fun serializeProviderConfigs(configs: Map<AiProviderId, ProviderConfig>): String {
        val root = linkedMapOf<String, Any?>()
        configs.forEach { (provider, cfg) ->
            root[provider.name] = mapOf(
                "baseUrl" to cfg.baseUrl,
                "modelId" to cfg.modelId,
                "maxTokens" to cfg.maxTokens,
                "temperature" to cfg.temperature.toDouble()
            )
        }
        return JsonCodec.toJson(root)
    }
}
