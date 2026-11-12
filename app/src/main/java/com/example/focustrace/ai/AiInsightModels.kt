package com.example.focustrace.ai

/**
 * Aggregated focus statistics for a single week.
 *
 * Strict Privacy Policy:
 * Contains ONLY aggregate session counts, duration totals, and check counts.
 * Never includes app names, package names, detailed app usage timelines, or phone event logs.
 */
data class WeeklyFocusStats(
    val weekLabel: String,
    val totalSessions: Int,
    val completedSessions: Int,
    val earlyStopSessions: Int,
    val totalFocusMinutes: Long,
    val totalPhoneUseMinutes: Long,
    val checkCount: Int,
    val longestFocusMinutes: Long
)

/**
 * Early Stop reason and user-entered memo.
 */
data class EarlyStopMemoInput(
    val reason: String,
    val memo: String
)

/**
 * Complete input dataset for generating focus insights, aggregating up to 4 weeks.
 */
data class AiFocusInput(
    val weeklyStats: List<WeeklyFocusStats>,
    val earlyStopMemos: List<EarlyStopMemoInput>
)

/**
 * Final output containing recommended session target and actionable insights.
 */
data class AiInsightResult(
    val recommendedDurationMinutes: Int,
    val insights: List<String>,
    val source: String, // "AI" or "RULE_FALLBACK"
    val modelName: String? = null,
    val statusMessage: String? = null
)
