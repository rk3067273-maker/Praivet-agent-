package com.example.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EndpointUrlNormalizerTest {

    @Test
    fun `normalizes trailing slash and v1 base urls without duplicating path`() {
        val cases = listOf(
            "https://api.openai.com/v1" to "https://api.openai.com/v1/chat/completions",
            "https://api.openai.com/v1/" to "https://api.openai.com/v1/chat/completions",
            "https://api.openai.com/v1/chat/completions" to "https://api.openai.com/v1/chat/completions",
            "https://api.openai.com/v1/chat/completions/chat/completions" to "https://api.openai.com/v1/chat/completions",
            "https://api.openai.com/v1/v1/" to "https://api.openai.com/v1/chat/completions",
            "https://agentrouter.org/v1/" to "https://agentrouter.org/v1/chat/completions",
            "https://openrouter.ai/api/v1" to "https://openrouter.ai/api/v1/chat/completions",
            "https://api.deepseek.com" to "https://api.deepseek.com/v1/chat/completions"
        )

        cases.forEach { (input, expectedChatUrl) ->
            val res = EndpointUrlNormalizer.normalizeOpenAiCompatible(input)
            assertTrue("Expected Valid for $input but got $res", res is NormalizedEndpointResult.Valid)
            assertEquals(expectedChatUrl, (res as NormalizedEndpointResult.Valid).chatCompletionsUrl)
        }
    }

    @Test
    fun `rejects http for remote hosts and allows http only for local dev hosts`() {
        val remoteHttp = EndpointUrlNormalizer.normalizeOpenAiCompatible("http://api.openai.com/v1")
        assertTrue(remoteHttp is NormalizedEndpointResult.Invalid)

        val agentRouterHttp = EndpointUrlNormalizer.normalizeOpenAiCompatible("http://agentrouter.org/v1")
        assertTrue(agentRouterHttp is NormalizedEndpointResult.Invalid)

        val loopback = EndpointUrlNormalizer.normalizeOpenAiCompatible("http://127.0.0.1:11434/v1")
        assertTrue(loopback is NormalizedEndpointResult.Valid)
        assertTrue((loopback as NormalizedEndpointResult.Valid).isLocalDevelopmentHost)

        val localhost = EndpointUrlNormalizer.normalizeOpenAiCompatible("http://localhost:8080/v1")
        assertTrue(localhost is NormalizedEndpointResult.Valid)
        assertFalse((EndpointUrlNormalizer.normalizeOpenAiCompatible("https://api.openai.com/v1") as NormalizedEndpointResult.Valid).isLocalDevelopmentHost)
    }

    @Test
    fun `rejects malformed urls and embedded credentials`() {
        assertTrue(EndpointUrlNormalizer.normalizeOpenAiCompatible("") is NormalizedEndpointResult.Invalid)
        assertTrue(EndpointUrlNormalizer.normalizeOpenAiCompatible("not a url") is NormalizedEndpointResult.Invalid)
        assertTrue(EndpointUrlNormalizer.normalizeOpenAiCompatible("https://user:pass@api.openai.com/v1") is NormalizedEndpointResult.Invalid)
        assertTrue(EndpointUrlNormalizer.normalizeOpenAiCompatible("ftp://api.openai.com/v1") is NormalizedEndpointResult.Invalid)
    }
}
