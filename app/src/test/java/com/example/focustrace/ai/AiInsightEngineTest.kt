package com.example.focustrace.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiInsightEngineTest {

    @Test
    fun `validateInput returns null when focus time is 30 mins or more`() {
        val input = AiFocusInput(
            weeklyStats = listOf(
                WeeklyFocusStats("W1", 1, 1, 0, 30, 0, 0, 30)
            ),
            earlyStopMemos = emptyList()
        )
        val result = AiInsightEngine.validateInput(input)
        assertEquals(null, result)
    }

    @Test
    fun `validateInput returns fallback when focus time is under 30 mins`() {
        val input = AiFocusInput(
            weeklyStats = listOf(
                WeeklyFocusStats("W1", 1, 0, 1, 15, 5, 2, 15)
            ),
            earlyStopMemos = emptyList()
        )
        val result = AiInsightEngine.validateInput(input)
        assertNotNull(result)
        assertEquals("RULE_FALLBACK", result?.source)
    }

    @Test
    fun `parseAndValidateResponse correctly parses JSON output`() {
        val json = """
            {
              "recommendedDurationMinutes": 40,
              "insights": [
                "Take 5-minute stretch breaks between study intervals.",
                "Use noise-canceling headphones to minimize construction noise interruptions."
              ]
            }
        """.trimIndent()

        val result = AiInsightEngine.parseAndValidateResponse(json, "gemini-3.5-flash-lite")
        assertEquals(40, result.recommendedDurationMinutes)
        assertEquals(2, result.insights.size)
        assertEquals("AI", result.source)
    }
}
