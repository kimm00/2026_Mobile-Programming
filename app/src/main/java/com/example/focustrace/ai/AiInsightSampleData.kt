package com.example.focustrace.ai

/**
 * Clearly labeled synthetic test datasets for prototyping F-06 AI Insights
 * without requiring active Room database or F-05 statistics features.
 */
object AiInsightSampleData {

    /**
     * Synthetic Dataset 1: Sufficient focus data with moderate completion rate.
     */
    val SAMPLE_SUFFICIENT = AiFocusInput(
        weeklyStats = listOf(
            WeeklyFocusStats(
                weekLabel = "4 weeks ago (Synthetic)",
                totalSessions = 10,
                completedSessions = 8,
                earlyStopSessions = 2,
                totalFocusMinutes = 450,
                totalPhoneUseMinutes = 60,
                checkCount = 18,
                longestFocusMinutes = 60
            ),
            WeeklyFocusStats(
                weekLabel = "3 weeks ago (Synthetic)",
                totalSessions = 12,
                completedSessions = 9,
                earlyStopSessions = 3,
                totalFocusMinutes = 540,
                totalPhoneUseMinutes = 75,
                checkCount = 22,
                longestFocusMinutes = 50
            ),
            WeeklyFocusStats(
                weekLabel = "2 weeks ago (Synthetic)",
                totalSessions = 14,
                completedSessions = 11,
                earlyStopSessions = 3,
                totalFocusMinutes = 630,
                totalPhoneUseMinutes = 80,
                checkCount = 25,
                longestFocusMinutes = 55
            ),
            WeeklyFocusStats(
                weekLabel = "Last week (Synthetic)",
                totalSessions = 15,
                completedSessions = 12,
                earlyStopSessions = 3,
                totalFocusMinutes = 680,
                totalPhoneUseMinutes = 70,
                checkCount = 20,
                longestFocusMinutes = 75
            )
        ),
        earlyStopMemos = listOf(
            EarlyStopMemoInput(
                reason = "DISTRACTED",
                memo = "Got distracted by social media notifications around 30 minutes in."
            ),
            EarlyStopMemoInput(
                reason = "TIRED",
                memo = "Felt exhausted after an intensive study block without breaks."
            ),
            EarlyStopMemoInput(
                reason = "INTERRUPTED",
                memo = "Had an unexpected phone call."
            )
        )
    )

    /**
     * Synthetic Dataset 2: Insufficient data (< 30 minutes total focus time).
     * Triggers the deterministic fallback for limited data.
     */
    val SAMPLE_INSUFFICIENT = AiFocusInput(
        weeklyStats = listOf(
            WeeklyFocusStats(
                weekLabel = "Last week (Synthetic)",
                totalSessions = 1,
                completedSessions = 0,
                earlyStopSessions = 1,
                totalFocusMinutes = 15,
                totalPhoneUseMinutes = 10,
                checkCount = 4,
                longestFocusMinutes = 15
            )
        ),
        earlyStopMemos = listOf(
            EarlyStopMemoInput(
                reason = "TIRED",
                memo = "Felt too tired to finish."
            )
        )
    )

    /**
     * Synthetic Dataset 3: High interruption rate and frequent phone checks.
     */
    val SAMPLE_HIGH_INTERRUPTION = AiFocusInput(
        weeklyStats = listOf(
            WeeklyFocusStats(
                weekLabel = "4 weeks ago (Synthetic)",
                totalSessions = 8,
                completedSessions = 3,
                earlyStopSessions = 5,
                totalFocusMinutes = 240,
                totalPhoneUseMinutes = 150,
                checkCount = 45,
                longestFocusMinutes = 35
            ),
            WeeklyFocusStats(
                weekLabel = "3 weeks ago (Synthetic)",
                totalSessions = 10,
                completedSessions = 4,
                earlyStopSessions = 6,
                totalFocusMinutes = 300,
                totalPhoneUseMinutes = 180,
                checkCount = 52,
                longestFocusMinutes = 30
            ),
            WeeklyFocusStats(
                weekLabel = "2 weeks ago (Synthetic)",
                totalSessions = 9,
                completedSessions = 3,
                earlyStopSessions = 6,
                totalFocusMinutes = 270,
                totalPhoneUseMinutes = 160,
                checkCount = 48,
                longestFocusMinutes = 28
            ),
            WeeklyFocusStats(
                weekLabel = "Last week (Synthetic)",
                totalSessions = 11,
                completedSessions = 4,
                earlyStopSessions = 7,
                totalFocusMinutes = 330,
                totalPhoneUseMinutes = 200,
                checkCount = 60,
                longestFocusMinutes = 32
            )
        ),
        earlyStopMemos = listOf(
            EarlyStopMemoInput(
                reason = "DISTRACTED",
                memo = "Kept checking news and chat updates during study sessions."
            ),
            EarlyStopMemoInput(
                reason = "DISTRACTED",
                memo = "Lost focus quickly after 20 minutes."
            ),
            EarlyStopMemoInput(
                reason = "INTERRUPTED",
                memo = "Frequent short phone checks broke my concentration flow."
            )
        )
    )
}
