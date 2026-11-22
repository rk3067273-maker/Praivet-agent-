package com.example.data.ai

import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.Pipe
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class SseStreamParserTest {

    @Test
    fun `parses multiple events, array content parts, reasoning_content, and finish reason`() = runTest {
        val ssePayload = buildString {
            append("data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"Plan: \"}}]}\n\n")
            append("data: {\"choices\":[{\"delta\":{\"content\":\"Hello \"}}]}\n\n")
            append("data: {\"choices\":[{\"delta\":{\"content\":[{\"type\":\"text\",\"text\":\"JARVIS\"}]}}]}\n\n")
            append("data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"length\"}]}\n\n")
            append("data: [DONE]\n\n")
        }

        val buffer = Buffer().writeUtf8(ssePayload)
        val chunks = mutableListOf<StreamChunkDelta>()
        val result = SseStreamParser.consumeSseStream(buffer) { chunks.add(it) }

        assertEquals("Plan: ", result.reasoningText)
        assertEquals("Hello JARVIS", result.text)
        assertEquals("length", result.finishReason)
        assertTrue(chunks.any { it.isDone })
    }

    @Test
    fun `handles multi-byte UTF-8 sequence split across chunk boundaries`() = runTest {
        val pipe = Pipe(1024L)
        val rawEvent = "data: {\"choices\":[{\"delta\":{\"content\":\"হ্যালো 🚀\"}}]}\n\ndata: [DONE]\n\n"
        val rawBytes = rawEvent.toByteArray(StandardCharsets.UTF_8)

        val emojiLeadByte = "🚀".toByteArray(StandardCharsets.UTF_8)[0]
        val splitIndex = rawBytes.indexOf(emojiLeadByte) + 1
        pipe.sink.write(Buffer().write(rawBytes, 0, splitIndex), splitIndex.toLong())
        pipe.sink.flush()
        val remaining = rawBytes.size - splitIndex
        pipe.sink.write(Buffer().write(rawBytes, splitIndex, remaining), remaining.toLong())
        pipe.sink.close()

        val result = SseStreamParser.consumeSseStream(pipe.source.buffer()) {}
        assertEquals("হ্যালো 🚀", result.text)
    }

    @Test
    fun `parses non-streaming completion json safely`() {
        val json = """
            {
              "choices": [
                {
                  "finish_reason": "stop",
                  "message": {
                    "role": "assistant",
                    "content": [{"type": "text", "text": "Systems nominal."}]
                  }
                }
              ]
            }
        """.trimIndent()
        val parsed = SseStreamParser.parseNonStreamingJson(json)
        assertEquals("Systems nominal.", parsed.text)
        assertEquals("stop", parsed.finishReason)
    }
}
