package com.example.data.ai

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

class OpenAiCompatibleClientTest {

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
    fun `sends trimmed bearer auth and respects reasoning model parameters`() = runTest {
        var capturedAuth = ""
        var capturedBody = ""

        server.createContext("/v1/chat/completions") { exchange ->
            capturedAuth = exchange.requestHeaders.getFirst("Authorization").orEmpty()
            capturedBody = exchange.requestBody.bufferedReader(StandardCharsets.UTF_8).readText()

            val sse = "data: {\"choices\":[{\"delta\":{\"content\":\"Affirmative.\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n"
            val bytes = sse.toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "text/event-stream")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }

        val config = ProviderRequestConfig(
            provider = AiProviderId.OPENAI,
            baseUrl = "$baseUrl/chat/completions/",
            modelId = "o3-mini",
            apiKey = "  sk-test-token-123  ",
            maxTokens = 1024,
            temperature = 0.7f
        )

        val deltas = client.streamChatCompletion(config, "Be brief.", emptyList(), "Status?").toList()
        assertEquals("Affirmative.", deltas.joinToString("") { it.textDelta })
        assertEquals("Bearer sk-test-token-123", capturedAuth)

        val json = JsonCodec.parseObjectOrNull(capturedBody)!!
        assertTrue("Reasoning model must use max_completion_tokens", json.containsKey("max_completion_tokens"))
        assertFalse("Reasoning model must not send max_tokens", json.containsKey("max_tokens"))
        assertFalse("Reasoning model must omit temperature", json.containsKey("temperature"))
    }

    @Test
    fun `retries 429 with retry-after and succeeds`() = runTest {
        val attempts429 = AtomicInteger(0)
        server.createContext("/v1/chat/completions") { exchange ->
            val count = attempts429.incrementAndGet()
            if (count == 1) {
                val err = """{"error":{"message":"Rate limit hit"}}""".toByteArray()
                exchange.responseHeaders.add("Retry-After", "1")
                exchange.sendResponseHeaders(429, err.size.toLong())
                exchange.responseBody.use { it.write(err) }
            } else {
                val ok = """{"choices":[{"finish_reason":"stop","message":{"content":"Recovered"}}]}""".toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, ok.size.toLong())
                exchange.responseBody.use { it.write(ok) }
            }
        }

        val config = ProviderRequestConfig(
            provider = AiProviderId.OPENAI,
            baseUrl = baseUrl,
            modelId = "gpt-4o-mini",
            apiKey = "sk-test",
            maxTokens = 512,
            temperature = 0.5f
        )

        val res = client.streamChatCompletion(config, "", emptyList(), "ping").toList()
        assertEquals(2, attempts429.get())
        assertEquals("Recovered", res.first().textDelta)
    }

    @Test
    fun `does not auto retry 401 unauthorized client and surfaces diagnostics in testConnection`() = runTest {
        val attempts401 = AtomicInteger(0)
        server.createContext("/v1/models") { exchange ->
            val ok = """{"data":[{"id":"deepseek-v3"}]}""".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, ok.size.toLong())
            exchange.responseBody.use { it.write(ok) }
        }
        server.createContext("/v1/chat/completions") { exchange ->
            attempts401.incrementAndGet()
            val err = """{"error":{"message":"unauthorized client detected"}}""".toByteArray()
            exchange.sendResponseHeaders(401, err.size.toLong())
            exchange.responseBody.use { it.write(err) }
        }

        val config = ProviderRequestConfig(
            provider = AiProviderId.AGENTROUTER,
            baseUrl = baseUrl,
            modelId = "deepseek-v3",
            apiKey = "sk-agentrouter-test",
            maxTokens = 512,
            temperature = 0.7f
        )

        val testResult = client.testConnection(config)
        assertFalse(testResult.success)
        assertEquals(401, testResult.httpStatusCode)
        assertEquals(HttpErrorCategory.AGENTROUTER_CLIENT_POLICY, testResult.category)
        assertEquals(listOf("deepseek-v3"), testResult.discoveredModels)
        assertEquals(1, attempts401.get())
    }
}
