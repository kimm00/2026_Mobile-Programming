package com.example.focustrace.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiInsightEngineTest {

    @Test
    fun `validateInput returns fallback result when total focus minutes are below 30`() {
        val input = AiInsightSampleData.SAMPLE_INSUFFICIENT
        val fallbackResult = AiInsightEngine.validateInput(input)

        assertNotNull(fallbackResult)
        assertEquals("RULE_FALLBACK", fallbackResult?.source)
        assertEquals(25, fallbackResult?.recommendedDurationMinutes)
        assertTrue(fallbackResult?.statusMessage?.contains("Insufficient") == true)
    }

    @Test
    fun `validateInput returns null when focus minutes are sufficient`() {
        val input = AiInsightSampleData.SAMPLE_SUFFICIENT
        val fallbackResult = AiInsightEngine.validateInput(input)

        assertNull(fallbackResult)
    }

    @Test
    fun `buildPrompt formats 4-week stats and early stop memos without app names`() {
        val input = AiInsightSampleData.SAMPLE_SUFFICIENT
        val prompt = AiInsightEngine.buildPrompt(input)

        assertTrue(prompt.contains("Aggregated 4-Week Focus Statistics:"))
        assertTrue(prompt.contains("Last week (Synthetic)"))
        assertTrue(prompt.contains("Got distracted by social media notifications"))

        // Privacy assertions: verify no package names or detailed app labels are present
        assertTrue(!prompt.contains("com.instagram.android"))
        assertTrue(!prompt.contains("com.android.chrome"))
    }

    @Test
    fun `parseAndValidateResponse correctly parses valid Gemini JSON`() {
        val validJson = """
            {
              "recommendedDurationMinutes": 45,
              "insights": [
                "Schedule your study sessions in 45-minute blocks for optimum focus.",
                "Take a 5-minute movement break when you finish each block."
              ]
            }
        """.trimIndent()

        val result = AiInsightEngine.parseAndValidateResponse(validJson, "gemini-2.5-flash")

        assertEquals(45, result.recommendedDurationMinutes)
        assertEquals(2, result.insights.size)
        assertEquals("AI", result.source)
        assertEquals("gemini-2.5-flash", result.modelName)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parseAndValidateResponse throws exception when duration is out of range`() {
        val invalidJson = """
            {
              "recommendedDurationMinutes": 200,
              "insights": ["Insight 1", "Insight 2"]
            }
        """.trimIndent()

        AiInsightEngine.parseAndValidateResponse(invalidJson)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parseAndValidateResponse throws exception when insights count is less than 2`() {
        val invalidJson = """
            {
              "recommendedDurationMinutes": 30,
              "insights": ["Only one insight"]
            }
        """.trimIndent()

        AiInsightEngine.parseAndValidateResponse(invalidJson)
    }

    @Test
    fun `generateRuleFallback produces appropriate recommendation for high interruption dataset`() {
        val input = AiInsightSampleData.SAMPLE_HIGH_INTERRUPTION
        val result = AiInsightEngine.generateRuleFallback(input, reason = "Test reason")

        assertEquals(25, result.recommendedDurationMinutes)
        assertEquals(3, result.insights.size)
        assertEquals("RULE_FALLBACK", result.source)
        assertTrue(result.statusMessage?.contains("Test reason") == true)
    }
}
