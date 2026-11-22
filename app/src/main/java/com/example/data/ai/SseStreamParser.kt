package com.example.data.ai

import okio.BufferedSource

data class TokenUsageSnapshot(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0,
    val isExact: Boolean = false
)

data class ToolCallDeltaChunk(
    val index: Int,
    val id: String? = null,
    val name: String? = null,
    val argumentsDelta: String = ""
)

data class StreamChunkDelta(
    val textDelta: String = "",
    val reasoningDelta: String = "",
    val toolCallDeltas: List<ToolCallDeltaChunk> = emptyList(),
    val usage: TokenUsageSnapshot? = null,
    val finishReason: String? = null,
    val isDone: Boolean = false
)

data class ParsedCompletionResult(
    val text: String,
    val reasoningText: String,
    val finishReason: String?,
    val toolCalls: List<ParsedToolCall> = emptyList(),
    val usage: TokenUsageSnapshot? = null
)

object SseStreamParser {

    private class ToolCallAccumulator {
        var id: String = ""
        val name = StringBuilder()
        val args = StringBuilder()
    }

    suspend fun consumeSseStream(
        source: BufferedSource,
        onDelta: suspend (StreamChunkDelta) -> Unit
    ): ParsedCompletionResult {
        val textBuilder = StringBuilder()
        val reasoningBuilder = StringBuilder()
        var lastFinishReason: String? = null
        var lastUsage: TokenUsageSnapshot? = null
        val toolAccumulators = linkedMapOf<Int, ToolCallAccumulator>()
        val dataBuffer = StringBuilder()

        suspend fun processPayload(payload: String): Boolean {
            val delta = parseEventPayload(payload)
            if (delta.isDone) {
                onDelta(delta)
                return true
            }
            if (delta.finishReason != null) {
                lastFinishReason = delta.finishReason
            }
            if (delta.usage != null) {
                lastUsage = delta.usage
            }
            if (delta.textDelta.isNotEmpty()) {
                textBuilder.append(delta.textDelta)
            }
            if (delta.reasoningDelta.isNotEmpty()) {
                reasoningBuilder.append(delta.reasoningDelta)
            }
            delta.toolCallDeltas.forEach { tc ->
                val acc = toolAccumulators.getOrPut(tc.index) { ToolCallAccumulator() }
                if (!tc.id.isNullOrBlank()) acc.id = tc.id
                if (!tc.name.isNullOrBlank()) acc.name.append(tc.name)
                if (tc.argumentsDelta.isNotEmpty()) acc.args.append(tc.argumentsDelta)
            }
            if (delta.textDelta.isNotEmpty() ||
                delta.reasoningDelta.isNotEmpty() ||
                delta.toolCallDeltas.isNotEmpty() ||
                delta.usage != null ||
                delta.finishReason != null
            ) {
                onDelta(delta)
            }
            return false
        }

        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            if (line.isEmpty()) {
                if (dataBuffer.isNotEmpty()) {
                    val payload = dataBuffer.toString().trim()
                    dataBuffer.clear()
                    if (processPayload(payload)) break
                }
                continue
            }

            if (line.startsWith(":")) {
                continue
            }

            if (line.startsWith("data:")) {
                val content = line.removePrefix("data:").trimStart()
                if (dataBuffer.isNotEmpty()) dataBuffer.append('\n')
                dataBuffer.append(content)
            }
        }

        if (dataBuffer.isNotEmpty()) {
            processPayload(dataBuffer.toString().trim())
        }

        val assembledToolCalls = toolAccumulators.entries
            .sortedBy { it.key }
            .mapIndexed { idx, (_, acc) ->
                ParsedToolCall(
                    id = acc.id.ifBlank { "call_$idx" },
                    name = acc.name.toString().trim(),
                    rawArgumentsJson = acc.args.toString().ifBlank { "{}" }
                )
            }
            .filter { it.name.isNotBlank() }

        return ParsedCompletionResult(
            text = textBuilder.toString(),
            reasoningText = reasoningBuilder.toString(),
            finishReason = lastFinishReason,
            toolCalls = assembledToolCalls,
            usage = lastUsage
        )
    }

    fun parseEventPayload(payload: String): StreamChunkDelta {
        if (payload == "[DONE]") {
            return StreamChunkDelta(isDone = true)
        }
        if (payload.isBlank()) {
            return StreamChunkDelta()
        }

        val root = JsonCodec.parseObjectOrNull(payload) ?: return StreamChunkDelta()
        if (root.containsKey("error")) {
            val errObj = root["error"]
            val errMsg = when (errObj) {
                is Map<*, *> -> errObj["message"] as? String
                is String -> errObj
                else -> null
            }
            throw IllegalStateException(errMsg?.takeIf { it.isNotBlank() } ?: "Upstream stream error event.")
        }

        val usage = parseUsage(root["usage"])
        val choices = root["choices"] as? List<*> ?: return StreamChunkDelta(usage = usage)
        if (choices.isEmpty()) return StreamChunkDelta(usage = usage)
        val choice = choices.firstOrNull() as? Map<*, *> ?: return StreamChunkDelta(usage = usage)

        val finishReason = (choice["finish_reason"] as? String)?.takeIf {
            it.isNotEmpty() && !it.equals("null", ignoreCase = true)
        }

        val deltaObj = (choice["delta"] as? Map<*, *>) ?: (choice["message"] as? Map<*, *>)
        val textDelta = extractContentText(deltaObj?.get("content"))
        val reasoningDelta = (deltaObj?.get("reasoning_content") as? String).orEmpty()
            .ifEmpty { (deltaObj?.get("reasoning") as? String).orEmpty() }
        val toolDeltas = parseToolCallDeltas(deltaObj?.get("tool_calls"))

        return StreamChunkDelta(
            textDelta = textDelta,
            reasoningDelta = reasoningDelta,
            toolCallDeltas = toolDeltas,
            usage = usage,
            finishReason = finishReason,
            isDone = false
        )
    }

    fun parseNonStreamingJson(rawJson: String): ParsedCompletionResult {
        val root = JsonCodec.parseObjectOrNull(rawJson)
            ?: throw IllegalStateException("Provider returned invalid JSON.")
        val usage = parseUsage(root["usage"])
        val choices = root["choices"] as? List<*>
            ?: throw IllegalStateException("Response JSON missing 'choices' array.")
        if (choices.isEmpty()) {
            throw IllegalStateException("Response contained no completion choices.")
        }
        val choice = choices.firstOrNull() as? Map<*, *>
            ?: throw IllegalStateException("Malformed choice object.")
        val finishReason = (choice["finish_reason"] as? String)?.takeIf {
            it.isNotEmpty() && !it.equals("null", ignoreCase = true)
        }
        val message = choice["message"] as? Map<*, *>
            ?: throw IllegalStateException("Response choice missing 'message' object.")
        val content = extractContentText(message["content"])
        val reasoning = (message["reasoning_content"] as? String).orEmpty()
            .ifEmpty { (message["reasoning"] as? String).orEmpty() }
        val toolCalls = parseNonStreamingToolCalls(message["tool_calls"])

        return ParsedCompletionResult(
            text = content,
            reasoningText = reasoning,
            finishReason = finishReason,
            toolCalls = toolCalls,
            usage = usage
        )
    }

    private fun parseUsage(rawUsage: Any?): TokenUsageSnapshot? {
        val map = rawUsage as? Map<*, *> ?: return null
        val prompt = (map["prompt_tokens"] as? Number)?.toInt()
            ?: (map["input_tokens"] as? Number)?.toInt()
        val completion = (map["completion_tokens"] as? Number)?.toInt()
            ?: (map["output_tokens"] as? Number)?.toInt()
        val total = (map["total_tokens"] as? Number)?.toInt()
            ?: if (prompt != null || completion != null) (prompt ?: 0) + (completion ?: 0) else null
        if (prompt == null && completion == null && total == null) return null
        return TokenUsageSnapshot(
            promptTokens = prompt ?: 0,
            completionTokens = completion ?: 0,
            totalTokens = total ?: 0,
            isExact = true
        )
    }

    private fun parseToolCallDeltas(rawToolCalls: Any?): List<ToolCallDeltaChunk> {
        val list = rawToolCalls as? List<*> ?: return emptyList()
        return list.mapIndexedNotNull { pos, item ->
            val map = item as? Map<*, *> ?: return@mapIndexedNotNull null
            val idx = (map["index"] as? Number)?.toInt() ?: pos
            val id = map["id"] as? String
            val fn = map["function"] as? Map<*, *>
            val name = fn?.get("name") as? String
            val args = (fn?.get("arguments") as? String).orEmpty()
            ToolCallDeltaChunk(index = idx, id = id, name = name, argumentsDelta = args)
        }
    }

    private fun parseNonStreamingToolCalls(rawToolCalls: Any?): List<ParsedToolCall> {
        val list = rawToolCalls as? List<*> ?: return emptyList()
        return list.mapIndexedNotNull { idx, item ->
            val map = item as? Map<*, *> ?: return@mapIndexedNotNull null
            val id = (map["id"] as? String).orEmpty().ifBlank { "call_$idx" }
            val fn = map["function"] as? Map<*, *> ?: return@mapIndexedNotNull null
            val name = (fn["name"] as? String)?.trim().orEmpty()
            if (name.isBlank()) return@mapIndexedNotNull null
            val args = (fn["arguments"] as? String).orEmpty().ifBlank { "{}" }
            ParsedToolCall(id = id, name = name, rawArgumentsJson = args)
        }
    }

    internal fun extractContentText(rawContent: Any?): String {
        return when (rawContent) {
            null -> ""
            is String -> rawContent
            is List<*> -> {
                val sb = StringBuilder()
                for (item in rawContent) {
                    when (item) {
                        is String -> sb.append(item)
                        is Map<*, *> -> {
                            val type = (item["type"] as? String).orEmpty()
                            if (type.isEmpty() || type == "text" || type == "output_text") {
                                val part = (item["text"] as? String).orEmpty()
                                    .ifEmpty { (item["content"] as? String).orEmpty() }
                                sb.append(part)
                            }
                        }
                    }
                }
                sb.toString()
            }
            else -> ""
        }
    }
}
