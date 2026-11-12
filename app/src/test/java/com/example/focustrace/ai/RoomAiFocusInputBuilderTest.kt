package com.example.focustrace.ai

import com.example.focustrace.data.db.entity.SessionEntity
import com.example.focustrace.ui.EarlyStopActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomAiFocusInputBuilderTest {

    @Test
    fun `buildFromSessions aggregates demo Room sessions with TIRED and OTHER reasons`() {
        val nowMs = System.currentTimeMillis()
        val oneHourMs = 60 * 60 * 1000L

        val mockSessions = listOf(
            SessionEntity(
                id = 1,
                subject = "알고리즘 문제 풀이",
                plan = "F-06 Demo Session",
                goalMinutes = 30,
                startAt = nowMs - (3 * 24 * oneHourMs),
                endAt = nowMs - (3 * 24 * oneHourMs) + (25 * 60 * 1000L),
                status = "COMPLETED",
                focusMs = 25 * 60 * 1000L,
                phoneUseMs = 0L,
                checkCount = 0,
                longestFocusMs = 25 * 60 * 1000L
            ),
            SessionEntity(
                id = 2,
                subject = "전공 서적 읽기",
                plan = "F-06 Demo Session",
                goalMinutes = 45,
                startAt = nowMs - (2 * 24 * oneHourMs),
                endAt = nowMs - (2 * 24 * oneHourMs) + (15 * 60 * 1000L),
                status = "EARLY_STOP",
                stopReason = EarlyStopActivity.REASON_TIRED, // "TIRED"
                stopMemo = null, // No memo
                focusMs = 12 * 60 * 1000L,
                phoneUseMs = 3 * 60 * 1000L,
                checkCount = 2,
                longestFocusMs = 10 * 60 * 1000L
            ),
            SessionEntity(
                id = 3,
                subject = "영어 단어 암기",
                plan = "F-06 Demo Session",
                goalMinutes = 40,
                startAt = nowMs - (1 * 24 * oneHourMs),
                endAt = nowMs - (1 * 24 * oneHourMs) + (15 * 60 * 1000L),
                status = "EARLY_STOP",
                stopReason = EarlyStopActivity.REASON_OTHER, // "OTHER"
                stopMemo = "공사 소음 때문에 집중하기 어려웠다",
                focusMs = 13 * 60 * 1000L,
                phoneUseMs = 2 * 60 * 1000L,
                checkCount = 1,
                longestFocusMs = 10 * 60 * 1000L
            )
        )

        val input = RoomAiFocusInputBuilder.buildFromSessions(mockSessions, nowMs = nowMs)

        assertEquals(4, input.weeklyStats.size)
        assertEquals(2, input.earlyStopMemos.size)

        val totalFocusMins = input.weeklyStats.sumOf { it.totalFocusMinutes }
        assertEquals(50L, totalFocusMins) // 25 + 12 + 13 = 50 mins (>= 45 mins)

        val tiredMemo = input.earlyStopMemos.find { it.reason == EarlyStopActivity.REASON_TIRED }
        assertTrue(tiredMemo != null)
        assertEquals("", tiredMemo?.memo)

        val otherMemo = input.earlyStopMemos.find { it.reason == EarlyStopActivity.REASON_OTHER }
        assertTrue(otherMemo != null)
        assertEquals("공사 소음 때문에 집중하기 어려웠다", otherMemo?.memo)

        val prompt = AiInsightEngine.buildPrompt(input)
        assertTrue(prompt.contains(EarlyStopActivity.REASON_TIRED))
        assertTrue(prompt.contains(EarlyStopActivity.REASON_OTHER))
        assertTrue(prompt.contains("공사 소음 때문에 집중하기 어려웠다"))

        // Ensures subject names or app package names are strictly excluded
        assertTrue(!prompt.contains("알고리즘"))
        assertTrue(!prompt.contains("전공 서적"))
        assertTrue(!prompt.contains("영어 단어"))
    }
}
