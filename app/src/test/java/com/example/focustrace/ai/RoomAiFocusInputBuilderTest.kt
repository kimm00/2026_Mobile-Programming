package com.example.focustrace.ai

import com.example.focustrace.data.db.entity.SessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomAiFocusInputBuilderTest {

    @Test
    fun `buildFromSessions aggregates weekly stats and preserves early stop reasons and memos`() {
        val nowMs = System.currentTimeMillis()
        val oneHourMs = 60 * 60 * 1000L

        val mockSessions = listOf(
            SessionEntity(
                id = 1,
                subject = "Math",
                goalMinutes = 60,
                startAt = nowMs - (2 * oneHourMs),
                endAt = nowMs - oneHourMs,
                status = "EARLY_STOP",
                stopReason = "FATIGUE",
                stopMemo = "Felt extremely tired after work",
                focusMs = 30 * 60 * 1000L,
                phoneUseMs = 5 * 60 * 1000L,
                checkCount = 3,
                longestFocusMs = 25 * 60 * 1000L
            ),
            SessionEntity(
                id = 2,
                subject = "Coding",
                goalMinutes = 45,
                startAt = nowMs - (24 * oneHourMs),
                endAt = nowMs - (23 * oneHourMs),
                status = "EARLY_STOP",
                stopReason = "OTHER",
                stopMemo = "Loud construction noise outside",
                focusMs = 20 * 60 * 1000L,
                phoneUseMs = 2 * 60 * 1000L,
                checkCount = 1,
                longestFocusMs = 18 * 60 * 1000L
            ),
            SessionEntity(
                id = 3,
                subject = "English",
                goalMinutes = 50,
                startAt = nowMs - (48 * oneHourMs),
                endAt = nowMs - (47 * oneHourMs),
                status = "COMPLETED",
                focusMs = 50 * 60 * 1000L,
                phoneUseMs = 0L,
                checkCount = 0,
                longestFocusMs = 50 * 60 * 1000L
            )
        )

        val input = RoomAiFocusInputBuilder.buildFromSessions(mockSessions, nowMs = nowMs)

        assertEquals(4, input.weeklyStats.size)
        assertEquals(2, input.earlyStopMemos.size)

        val fatigueMemo = input.earlyStopMemos.find { it.reason == "FATIGUE" }
        assertTrue(fatigueMemo != null)
        assertEquals("Felt extremely tired after work", fatigueMemo?.memo)

        val otherMemo = input.earlyStopMemos.find { it.reason == "OTHER" }
        assertTrue(otherMemo != null)
        assertEquals("Loud construction noise outside", otherMemo?.memo)

        val prompt = AiInsightEngine.buildPrompt(input)
        assertTrue(prompt.contains("FATIGUE"))
        assertTrue(prompt.contains("Felt extremely tired after work"))
        assertTrue(prompt.contains("OTHER"))
        assertTrue(prompt.contains("Loud construction noise outside"))
        assertTrue(!prompt.contains("Math")) // Ensures subject/package names are NOT in prompt
        assertTrue(!prompt.contains("Coding"))
    }
}
