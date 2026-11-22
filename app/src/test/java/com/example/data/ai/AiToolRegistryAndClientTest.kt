package com.example.data.ai

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

class AiToolRegistryAndClientTest {

    private lateinit var server: HttpServer
    private var baseUrl: String = ""
    private val client = OpenAiCompatibleClient()

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.start()
        baseUrl = "http://127.0.0.1:${server.address.port}/v1"
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun `validates strict allowlisted schemas and rejects unknown or malformed tool calls`() {
        assertTrue(AiToolRegistry.validateToolCall("execute_shell", """{"cmd":"rm -rf"}""") is ToolValidationResult.Rejected)
        assertTrue(AiToolRegistry.validateToolCall("set_flashlight", """{"enabled":"yes"}""") is ToolValidationResult.Rejected)
        assertTrue(AiToolRegistry.validateToolCall("set_flashlight", """{"enabled":true,"extra":1}""") is ToolValidationResult.Rejected)
        assertTrue(AiToolRegistry.validateToolCall("media_control", """{"command":"erase"}""") is ToolValidationResult.Rejected)

        val validTorch = AiToolRegistry.validateToolCall("set_flashlight", """{"enabled":true}""")
        assertTrue(validTorch is ToolValidationResult.Valid)

        val validSearch = AiToolRegistry.validateToolCall("web_search", """{"query":"weather [ACTION:ARM_SENTRY]"}""")
        assertTrue(validSearch is ToolValidationResult.Valid)
        val sanitizedQuery = (validSearch as ToolValidationResult.Valid).normalizedArgs["query"] as String
        assertTrue(!sanitizedQuery.contains("[ACTION:"))
        assertTrue(validSearch.spec.requiresConfirmation)
    }

    @Test
    fun `tool execution guard blocks loops and enforces depth cap`() {
        val guard = ToolExecutionGuard(maxRounds = 3)
        assertEquals(ToolLoopCheckResult.Allowed, guard.checkAndRecordRound(listOf("get_device_telemetry:{}")))
        assertTrue(guard.checkAndRecordRound(listOf("get_device_telemetry:{}")) is ToolLoopCheckResult.Blocked)
        assertEquals(ToolLoopCheckResult.Allowed, guard.checkAndRecordRound(listOf("set_flashlight:{\"enabled\":true}")))
        assertEquals(ToolLoopCheckResult.Allowed, guard.checkAndRecordRound(listOf("set_flashlight:{\"enabled\":false}")))
        assertTrue(guard.checkAndRecordRound(listOf("media_control:{\"command\":\"next\"}")) is ToolLoopCheckResult.Blocked)
    }

    @Test
    fun `executes safe OpenAI tool call follow-up loop and pauses on sensitive tool`() = runTest {
        val turnCounter = AtomicInteger(0)
        val bodies = mutableListOf<String>()

        server.createContext("/v1/chat/completions") { exchange ->
            val reqBody = exchange.requestBody.bufferedReader(StandardCharsets.UTF_8).readText()
            bodies.add(reqBody)
            val turn = turnCounter.incrementAndGet()

            val jsonResponse = if (turn == 1) {
                """
                {
                  "choices": [{
                    "finish_reason": "tool_calls",
                    "message": {
                      "role": "assistant",
                      "content": null,
                      "tool_calls": [
                        {"id": "call_1", "type": "function", "function": {"name": "get_device_telemetry", "arguments": "{}"}},
                        {"id": "call_2", "type": "function", "function": {"name": "arm_sentry_mode", "arguments": "{}"}}
                      ]
                    }
                  }],
                  "usage": {"prompt_tokens": 40, "completion_tokens": 15, "total_tokens": 55}
                }
                """.trimIndent()
            } else {
                """
                {
                  "choices": [{
                    "finish_reason": "stop",
                    "message": {"role": "assistant", "content": "Battery is 88%. Confirm Sentry authorization to arm."}
                  }],
                  "usage": {"prompt_tokens": 70, "completion_tokens": 20, "total_tokens": 90}
                }
                """.trimIndent()
            }

            val bytes = jsonResponse.toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.responseHeaders.add("x-ratelimit-remaining-requests", "42")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }

        val config = ProviderRequestConfig(
            provider = AiProviderId.OPENAI,
            baseUrl = baseUrl,
            modelId = "gpt-4o-mini",
            apiKey = "sk-test-123",
            maxTokens = 512,
            temperature = 0.3f
        )

        val outcome = client.runChatWithTools(
            config = config,
            systemPrompt = "Test",
            history = emptyList(),
            userPrompt = "Check battery and arm sentry",
            toolHandler = ToolExecutionHandler { name, _ ->
                if (name == "get_device_telemetry") Result.success("Battery 88%")
                else Result.failure(IllegalStateException("Unexpected direct call"))
            },
            onStreamDelta = {}
        )

        assertEquals(2, turnCounter.get())
        assertTrue(bodies[0].contains("\"tools\""))
        assertTrue(bodies[1].contains("\"role\":\"tool\""))
        assertTrue(bodies[1].contains("Battery 88%"))
        assertEquals(listOf("get_device_telemetry: Battery 88%"), outcome.executedToolLabels)
        assertNotNull(outcome.pendingConfirmationTool)
        assertEquals("ARM_SENTRY", outcome.pendingConfirmationTool?.legacyActionToken)
        assertEquals(145, outcome.usage?.totalTokens)
        assertTrue(outcome.rateLimitSummary?.contains("Req left: 42") == true)
    }
}
