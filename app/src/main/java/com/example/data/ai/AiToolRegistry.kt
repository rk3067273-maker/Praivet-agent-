package com.example.data.ai

import com.example.data.security.PromptSecurityGuard

data class JarvisToolSpec(
    val name: String,
    val description: String,
    val parametersSchema: Map<String, Any?>,
    val allowedKeys: Set<String>,
    val requiresConfirmation: Boolean,
    val isReadOnly: Boolean
)

data class ParsedToolCall(
    val id: String,
    val name: String,
    val rawArgumentsJson: String
)

sealed interface ToolValidationResult {
    data class Valid(
        val spec: JarvisToolSpec,
        val normalizedArgs: Map<String, Any?>,
        val canonicalSignature: String
    ) : ToolValidationResult

    data class Rejected(val reason: String) : ToolValidationResult
}

sealed interface ToolLoopCheckResult {
    data object Allowed : ToolLoopCheckResult
    data class Blocked(val reason: String) : ToolLoopCheckResult
}

object AiToolRegistry {

    const val MAX_TOOL_ROUNDS = 3
    const val TOOL_TIMEOUT_MS = 10_000L

    val specs: List<JarvisToolSpec> = listOf(
        JarvisToolSpec(
            name = "get_device_telemetry",
            description = "Read current Android battery, network transport, ambient light, proximity, and motion telemetry.",
            parametersSchema = mapOf(
                "type" to "object",
                "properties" to emptyMap<String, Any?>(),
                "additionalProperties" to false
            ),
            allowedKeys = emptySet(),
            requiresConfirmation = false,
            isReadOnly = true
        ),
        JarvisToolSpec(
            name = "set_flashlight",
            description = "Turn the device camera torch flashlight on or off.",
            parametersSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "enabled" to mapOf(
                        "type" to "boolean",
                        "description" to "True to turn torch on, false to turn it off."
                    )
                ),
                "required" to listOf("enabled"),
                "additionalProperties" to false
            ),
            allowedKeys = setOf("enabled"),
            requiresConfirmation = false,
            isReadOnly = false
        ),
        JarvisToolSpec(
            name = "media_control",
            description = "Control active media playback on the device.",
            parametersSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "command" to mapOf(
                        "type" to "string",
                        "enum" to listOf("next", "play_pause")
                    )
                ),
                "required" to listOf("command"),
                "additionalProperties" to false
            ),
            allowedKeys = setOf("command"),
            requiresConfirmation = false,
            isReadOnly = false
        ),
        JarvisToolSpec(
            name = "enable_air_gestures",
            description = "Arm the optical proximity zero-touch air gesture engine.",
            parametersSchema = mapOf(
                "type" to "object",
                "properties" to emptyMap<String, Any?>(),
                "additionalProperties" to false
            ),
            allowedKeys = emptySet(),
            requiresConfirmation = false,
            isReadOnly = false
        ),
        JarvisToolSpec(
            name = "arm_sentry_mode",
            description = "Arm Sentry anti-theft motion watchdog (requires operator confirmation).",
            parametersSchema = mapOf(
                "type" to "object",
                "properties" to emptyMap<String, Any?>(),
                "additionalProperties" to false
            ),
            allowedKeys = emptySet(),
            requiresConfirmation = true,
            isReadOnly = false
        ),
        JarvisToolSpec(
            name = "open_system_settings",
            description = "Open an Android system settings screen (requires operator confirmation).",
            parametersSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "panel" to mapOf(
                        "type" to "string",
                        "enum" to listOf("wifi", "bluetooth", "general")
                    )
                ),
                "required" to listOf("panel"),
                "additionalProperties" to false
            ),
            allowedKeys = setOf("panel"),
            requiresConfirmation = true,
            isReadOnly = false
        ),
        JarvisToolSpec(
            name = "web_search",
            description = "Launch an external browser/system search query (requires operator confirmation).",
            parametersSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "query" to mapOf(
                        "type" to "string",
                        "description" to "Plain-text search query (1 to 120 characters)."
                    )
                ),
                "required" to listOf("query"),
                "additionalProperties" to false
            ),
            allowedKeys = setOf("query"),
            requiresConfirmation = true,
            isReadOnly = false
        )
    )

    private val specByName = specs.associateBy { it.name }

    fun toOpenAiToolsPayload(): List<Map<String, Any?>> {
        return specs.map { spec ->
            mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to spec.name,
                    "description" to spec.description,
                    "parameters" to spec.parametersSchema
                )
            )
        }
    }

    fun validateToolCall(toolName: String, rawArgumentsJson: String): ToolValidationResult {
        val cleanName = toolName.trim()
        val spec = specByName[cleanName]
            ?: return ToolValidationResult.Rejected("Rejected unknown or disallowed tool '$cleanName'.")

        val trimmedArgs = rawArgumentsJson.trim().ifEmpty { "{}" }
        val argsMap = JsonCodec.parseObjectOrNull(trimmedArgs)
            ?: return ToolValidationResult.Rejected("Invalid JSON arguments object for tool '$cleanName'.")

        val unexpectedKeys = argsMap.keys - spec.allowedKeys
        if (unexpectedKeys.isNotEmpty()) {
            return ToolValidationResult.Rejected(
                "Unexpected argument key(s) ${unexpectedKeys.joinToString()} for tool '$cleanName'."
            )
        }

        val normalized = linkedMapOf<String, Any?>()
        when (cleanName) {
            "get_device_telemetry", "enable_air_gestures", "arm_sentry_mode" -> Unit
            "set_flashlight" -> {
                val enabled = argsMap["enabled"] as? Boolean
                    ?: return ToolValidationResult.Rejected("Tool 'set_flashlight' requires boolean 'enabled'.")
                normalized["enabled"] = enabled
            }
            "media_control" -> {
                val cmd = (argsMap["command"] as? String)?.trim()?.lowercase()
                if (cmd != "next" && cmd != "play_pause") {
                    return ToolValidationResult.Rejected("Tool 'media_control' requires command 'next' or 'play_pause'.")
                }
                normalized["command"] = cmd
            }
            "open_system_settings" -> {
                val panel = (argsMap["panel"] as? String)?.trim()?.lowercase()
                if (panel != "wifi" && panel != "bluetooth" && panel != "general") {
                    return ToolValidationResult.Rejected("Tool 'open_system_settings' requires panel 'wifi', 'bluetooth', or 'general'.")
                }
                normalized["panel"] = panel
            }
            "web_search" -> {
                val rawQuery = argsMap["query"] as? String
                    ?: return ToolValidationResult.Rejected("Tool 'web_search' requires string 'query'.")
                val sanitized = PromptSecurityGuard.sanitizeUntrustedText(rawQuery, 120)
                    .replace(Regex("[\r\n\t]+"), " ")
                    .trim()
                if (sanitized.isBlank()) {
                    return ToolValidationResult.Rejected("Tool 'web_search' query cannot be blank.")
                }
                normalized["query"] = sanitized
            }
        }

        val canonical = "$cleanName:${JsonCodec.toJson(normalized)}"
        return ToolValidationResult.Valid(spec, normalized, canonical)
    }

    fun toLegacyActionToken(toolName: String, args: Map<String, Any?>): String? {
        return when (toolName) {
            "set_flashlight" -> if (args["enabled"] == true) "TORCH_ON" else "TORCH_OFF"
            "media_control" -> if (args["command"] == "next") "MEDIA_NEXT" else "MEDIA_PLAY_PAUSE"
            "enable_air_gestures" -> "ENABLE_GESTURES"
            "arm_sentry_mode" -> "ARM_SENTRY"
            "open_system_settings" -> when (args["panel"]) {
                "wifi" -> "OPEN_WIFI"
                "bluetooth" -> "OPEN_BLUETOOTH"
                else -> "OPEN_SETTINGS"
            }
            "web_search" -> "WEB_SEARCH:${args["query"]}"
            else -> null
        }
    }
}

class ToolExecutionGuard(
    private val maxRounds: Int = AiToolRegistry.MAX_TOOL_ROUNDS
) {
    private var roundsExecuted = 0
    private val seenSignatures = mutableSetOf<String>()

    fun checkAndRecordRound(canonicalSignatures: List<String>): ToolLoopCheckResult {
        if (roundsExecuted >= maxRounds) {
            return ToolLoopCheckResult.Blocked(
                "Tool execution depth limit ($maxRounds rounds) reached for this turn."
            )
        }
        val duplicate = canonicalSignatures.firstOrNull { it in seenSignatures }
        if (duplicate != null) {
            return ToolLoopCheckResult.Blocked(
                "Repeated tool-call loop detected ($duplicate); halting execution."
            )
        }
        roundsExecuted++
        seenSignatures.addAll(canonicalSignatures)
        return ToolLoopCheckResult.Allowed
    }
}
