package com.example.focustrace.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiInsightServiceTest {

    private class FakeFirebaseAiClient(
        private val responseSupplier: (prompt: String, modelName: String) -> String
    ) : FirebaseAiClient {
        override suspend fun generateContent(prompt: String, modelName: String): String {
            return responseSupplier(prompt, modelName)
        }

        override suspend fun testMinimalCall(modelName: String, prompt: String): String {
            return responseSupplier(prompt, modelName)
        }
    }

    @Test
    fun `generateInsights returns AI result when client succeeds`() = runBlocking {
        val fakeClient = FakeFirebaseAiClient { _, model ->
            assertEquals(AiInsightService.DEFAULT_MODEL, model)
            """
                {
                  "recommendedDurationMinutes": 45,
                  "insights": [
                    "Maintain structured 45-minute focus blocks.",
                    "Take a brief break between study intervals."
                  ]
                }
            """.trimIndent()
        }

        val service = AiInsightService(client = fakeClient)
        val result = service.generateInsights(AiInsightSampleData.SAMPLE_SUFFICIENT)

        assertEquals("AI", result.source)
        assertEquals(45, result.recommendedDurationMinutes)
        assertEquals(2, result.insights.size)
        assertEquals(AiInsightService.DEFAULT_MODEL, result.modelName)
    }

    @Test
    fun `generateInsights falls back to rule engine when primary model throws exception`() = runBlocking {
        val fakeClient = FakeFirebaseAiClient { _, model ->
            throw RuntimeException("API key invalid or quota exceeded for $model")
        }

        val service = AiInsightService(client = fakeClient)
        val result = service.generateInsights(AiInsightSampleData.SAMPLE_SUFFICIENT)

        assertEquals("RULE_FALLBACK", result.source)
        assertTrue(result.statusMessage?.contains(AiInsightService.DEFAULT_MODEL) == true)
        assertTrue(result.statusMessage?.contains("API key invalid or quota exceeded") == true)
    }
}
