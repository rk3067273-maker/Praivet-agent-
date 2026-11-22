package com.example.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpErrorHandlerTest {

    @Test
    fun `distinguishes agentrouter 401 unauthorized client from generic 401 and 403`() {
        val agentRouter401 = HttpErrorHandler.mapHttpError(
            statusCode = 401,
            rawBody = """{"error":{"message":"unauthorized client detected","type":"unauthorized_client_error"}}""",
            provider = AiProviderId.AGENTROUTER,
            retryAfterHeader = null
        )
        assertEquals(401, agentRouter401.statusCode)
        assertEquals(HttpErrorCategory.AGENTROUTER_CLIENT_POLICY, agentRouter401.category)
        assertTrue(agentRouter401.userMessage.contains("approved CLI clients", ignoreCase = true))

        val generic401 = HttpErrorHandler.mapHttpError(
            statusCode = 401,
            rawBody = """{"error":{"message":"Incorrect API key provided: sk-secret123456"}}""",
            provider = AiProviderId.OPENAI,
            retryAfterHeader = null,
            knownSecrets = listOf("sk-secret123456")
        )
        assertEquals(HttpErrorCategory.AUTHENTICATION, generic401.category)
        assertFalse(generic401.userMessage.contains("sk-secret123456"))

        val forbidden403 = HttpErrorHandler.mapHttpError(
            statusCode = 403,
            rawBody = """{"error":{"message":"Region blocked"}}""",
            provider = AiProviderId.OPENAI,
            retryAfterHeader = null
        )
        assertEquals(HttpErrorCategory.FORBIDDEN, forbidden403.category)
    }

    @Test
    fun `maps all http error statuses and enforces non-retryable auth codes`() {
        val nonRetryable = listOf(400, 401, 403, 404, 413)
        nonRetryable.forEach { code ->
            assertFalse("HTTP $code must not be retried", HttpErrorHandler.isRetryableStatus(code))
            val mapped = HttpErrorHandler.mapHttpError(code, null, AiProviderId.CUSTOM, null)
            assertEquals(code, mapped.statusCode)
        }

        val retryable = listOf(408, 429, 500, 502, 503, 504)
        retryable.forEach { code ->
            assertTrue("HTTP $code should be retryable", HttpErrorHandler.isRetryableStatus(code))
            val mapped = HttpErrorHandler.mapHttpError(code, null, AiProviderId.CUSTOM, "3")
            assertEquals(code, mapped.statusCode)
        }

        assertEquals(3000L, HttpErrorHandler.computeRetryDelayMs(0, 3L))
    }
}
