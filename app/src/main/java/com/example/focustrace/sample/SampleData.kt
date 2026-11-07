package com.example.focustrace.sample

import com.example.focustrace.data.model.ReportAppUsage
import com.example.focustrace.data.model.ReportPhoneEvent
import com.example.focustrace.data.model.ReportSession
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Deterministic sample dataset for verifying Day, Week, and Month report calculations.
 */
object SampleData {

    val TEST_ZONE_ID: ZoneId = ZoneId.of("Asia/Seoul")

    // Reference start time: 2026-03-01 00:00:00 KST
    val BASE_START_DATE: LocalDate = LocalDate.of(2026, 3, 1)
    val BASE_START_MS: Long = ZonedDateTime.of(2026, 3, 1, 0, 0, 0, 0, TEST_ZONE_ID)
        .toInstant()
        .toEpochMilli()

    val BASE_END_MS: Long = ZonedDateTime.of(2026, 3, 29, 0, 0, 0, 0, TEST_ZONE_ID)
        .toInstant()
        .toEpochMilli()

    /**
     * Helper to compute epoch ms for a specific day offset from 2026-03-01 and hour/minute.
     */
    fun getEpochMs(dayOffset: Int, hour: Int, minute: Int = 0): Long {
        return ZonedDateTime.of(2026, 3, 1, hour, minute, 0, 0, TEST_ZONE_ID)
            .plusDays(dayOffset.toLong())
            .toInstant()
            .toEpochMilli()
    }

    /**
     * Deterministic list of sample sessions.
     */
    val SESSIONS: List<ReportSession> = listOf(
        // --- Day 1 (2026-03-01) ---
        // Session 1: Completed, 60 min goal, 50 min focus, 10 min phone use, 2 checks
        ReportSession(
            id = 1L,
            subject = "Algorithm Study",
            plan = "Solve 2 LeetCode problems",
            goalMinutes = 60,
            startAt = getEpochMs(0, 9, 0),
            endAt = getEpochMs(0, 10, 0),
            status = "COMPLETED",
            focusMs = 50 * 60 * 1000L, // 3,000,000 ms
            phoneUseMs = 10 * 60 * 1000L, // 600,000 ms
            checkCount = 2,
            unlockOnlyCount = 1,
            longestFocusMs = 30 * 60 * 1000L // 1,800,000 ms
        ),
        // Session 2: Early Stop, 90 min goal, 40 min focus, 5 min phone use, 1 check
        ReportSession(
            id = 2L,
            subject = "Android Architecture",
            plan = "Read Room database guide",
            goalMinutes = 90,
            startAt = getEpochMs(0, 14, 0),
            endAt = getEpochMs(0, 14, 45),
            status = "EARLY_STOP",
            stopReason = "TIRED",
            stopMemo = "Felt exhausted after lunch",
            focusMs = 40 * 60 * 1000L, // 2,400,000 ms
            phoneUseMs = 5 * 60 * 1000L, // 300,000 ms
            checkCount = 1,
            unlockOnlyCount = 0,
            longestFocusMs = 25 * 60 * 1000L // 1,500,000 ms
        ),

        // --- Day 2 (2026-03-02) ---
        // Session 3: Completed, 120 min goal, 110 min focus, 10 min phone use, 3 checks
        ReportSession(
            id = 3L,
            subject = "Mobile Programming",
            plan = "Prepare F-05 report architecture",
            goalMinutes = 120,
            startAt = getEpochMs(1, 10, 0),
            endAt = getEpochMs(1, 12, 0),
            status = "COMPLETED",
            focusMs = 110 * 60 * 1000L, // 6,600,000 ms
            phoneUseMs = 10 * 60 * 1000L, // 600,000 ms
            checkCount = 3,
            unlockOnlyCount = 2,
            longestFocusMs = 60 * 60 * 1000L // 3,600,000 ms
        ),

        // --- Day 3 (2026-03-03) ---
        // Session 4: Early Stop, 60 min goal, 20 min focus, 10 min phone use, 2 checks
        ReportSession(
            id = 4L,
            subject = "English Reading",
            plan = "Read 1 chapter",
            goalMinutes = 60,
            startAt = getEpochMs(2, 16, 0),
            endAt = getEpochMs(2, 16, 30),
            status = "EARLY_STOP",
            stopReason = "INTERRUPTED",
            stopMemo = "Received urgent call",
            focusMs = 20 * 60 * 1000L, // 1,200,000 ms
            phoneUseMs = 10 * 60 * 1000L,
            checkCount = 2,
            unlockOnlyCount = 0,
            longestFocusMs = 15 * 60 * 1000L
        ),

        // --- Day 4 (2026-03-04) - Cross-Midnight Session ---
        // Session 5: Starts at 23:30 (Day 4) and ends at 00:30 (Day 5)
        ReportSession(
            id = 5L,
            subject = "Night Study",
            plan = "Review Kotlin Coroutines",
            goalMinutes = 60,
            startAt = getEpochMs(3, 23, 30),
            endAt = getEpochMs(4, 0, 30),
            status = "COMPLETED",
            focusMs = 55 * 60 * 1000L, // 3,300,000 ms
            phoneUseMs = 5 * 60 * 1000L,
            checkCount = 1,
            unlockOnlyCount = 0,
            longestFocusMs = 40 * 60 * 1000L
        ),

        // --- Day 7 (2026-03-07) - Running Session (Should be excluded) ---
        ReportSession(
            id = 6L,
            subject = "Running Session",
            plan = "In progress...",
            goalMinutes = 60,
            startAt = getEpochMs(6, 18, 0),
            endAt = null,
            status = "RUNNING",
            focusMs = 0L,
            phoneUseMs = 0L,
            checkCount = 0,
            unlockOnlyCount = 0,
            longestFocusMs = 0L
        ),

        // --- Day 8 (2026-03-08, Week 2) ---
        ReportSession(
            id = 7L,
            subject = "Database Schema",
            plan = "Design Room entities",
            goalMinutes = 90,
            startAt = getEpochMs(7, 10, 0),
            endAt = getEpochMs(7, 11, 30),
            status = "COMPLETED",
            focusMs = 85 * 60 * 1000L,
            phoneUseMs = 5 * 60 * 1000L,
            checkCount = 1,
            unlockOnlyCount = 0,
            longestFocusMs = 70 * 60 * 1000L
        ),

        // --- Day 15 (2026-03-15, Week 3) ---
        ReportSession(
            id = 8L,
            subject = "Early Stop Test",
            plan = "Short session",
            goalMinutes = 30,
            startAt = getEpochMs(14, 15, 0),
            endAt = getEpochMs(14, 15, 15),
            status = "EARLY_STOP",
            stopReason = "DISTRACTED",
            stopMemo = "Opened Instagram accidentally",
            focusMs = 10 * 60 * 1000L,
            phoneUseMs = 5 * 60 * 1000L,
            checkCount = 2,
            unlockOnlyCount = 0,
            longestFocusMs = 8 * 60 * 1000L
        )
    )

    /**
     * Deterministic sample phone events matching the sessions above.
     */
    val PHONE_EVENTS: List<ReportPhoneEvent> = listOf(
        ReportPhoneEvent(1L, 1L, getEpochMs(0, 9, 20), getEpochMs(0, 9, 25), "CHECK"),
        ReportPhoneEvent(2L, 1L, getEpochMs(0, 9, 45), getEpochMs(0, 9, 50), "CHECK"),
        ReportPhoneEvent(3L, 2L, getEpochMs(0, 14, 20), getEpochMs(0, 14, 25), "CHECK"),
        ReportPhoneEvent(4L, 3L, getEpochMs(1, 10, 30), getEpochMs(1, 10, 35), "CHECK")
    )

    /**
     * Deterministic sample app usages matching phone events.
     */
    val APP_USAGES: List<ReportAppUsage> = listOf(
        // Non-allowed usage in Session 1
        ReportAppUsage(
            id = 1L,
            eventId = 1L,
            sessionId = 1L,
            packageName = "com.instagram.android",
            appLabel = "Instagram",
            startAt = getEpochMs(0, 9, 20),
            durationMs = 5 * 60 * 1000L,
            isAllowed = false,
            isAuto = false,
            isCounted = true
        ),
        // Allowed dictionary app usage in Session 1
        ReportAppUsage(
            id = 2L,
            eventId = 2L,
            sessionId = 1L,
            packageName = "com.naver.android.dict",
            appLabel = "Naver Dictionary",
            startAt = getEpochMs(0, 9, 45),
            durationMs = 5 * 60 * 1000L,
            isAllowed = true,
            isAuto = false,
            isCounted = false
        ),
        // Non-allowed YouTube usage in Session 2
        ReportAppUsage(
            id = 3L,
            eventId = 3L,
            sessionId = 2L,
            packageName = "com.google.android.youtube",
            appLabel = "YouTube",
            startAt = getEpochMs(0, 14, 20),
            durationMs = 5 * 60 * 1000L,
            isAllowed = false,
            isAuto = false,
            isCounted = true
        )
    )
}
