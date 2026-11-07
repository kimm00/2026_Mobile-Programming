package com.example.focustrace.data.model

import java.time.LocalDate
import java.time.ZoneId

/**
 * Report period types supported by FocusTrace.
 */
enum class PeriodType {
    DAY,
    WEEK,
    MONTH
}

/**
 * Pure domain model representing a Focus Session matching schema.md.
 * Designed to be mapped from Room entity later.
 */
data class ReportSession(
    val id: Long,
    val subject: String,
    val plan: String?,
    val goalMinutes: Int,
    val startAt: Long,
    val endAt: Long?,
    val status: String, // RUNNING, COMPLETED, EARLY_STOP, INTERRUPTED
    val stopReason: String? = null,
    val stopMemo: String? = null,
    val reflection: String? = null,
    val photoUri: String? = null,
    val focusMs: Long = 0L,
    val phoneUseMs: Long = 0L,
    val checkCount: Int = 0,
    val unlockOnlyCount: Int = 0,
    val longestFocusMs: Long = 0L
)

/**
 * Pure domain model representing a Phone Event episode (unlock -> screen off).
 */
data class ReportPhoneEvent(
    val id: Long,
    val sessionId: Long,
    val startAt: Long,
    val endAt: Long,
    val kind: String // CHECK, ALLOWED_ONLY, UNLOCK_ONLY
)

/**
 * Pure domain model representing an App Usage instance.
 */
data class ReportAppUsage(
    val id: Long,
    val eventId: Long,
    val sessionId: Long,
    val packageName: String,
    val appLabel: String,
    val startAt: Long,
    val durationMs: Long,
    val isAllowed: Boolean,
    val isAuto: Boolean,
    val isCounted: Boolean
)

/**
 * Aggregated metric summary for a given report period.
 */
data class FocusSummary(
    val totalFocusMs: Long,
    val totalPhoneUseMs: Long,
    val totalCheckCount: Int,
    val checksPerFocusHour: Double,
    val goalCompletionRate: Double, // 0.0 to 100.0
    val completedSessionCount: Int,
    val totalFinishedSessionCount: Int,
    val longestFocusMs: Long
)

/**
 * Focus statistics grouped by local date.
 */
data class DailyFocusStat(
    val date: LocalDate,
    val focusMs: Long,
    val checkCount: Int
)

/**
 * Focus statistics grouped by week.
 */
data class WeeklyFocusStat(
    val weekLabel: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val focusMs: Long
)

/**
 * Phone usage aggregated by hour of the day (0..23).
 */
data class HourlyPhoneUsageStat(
    val hourOfDay: Int,
    val usageMs: Long
)

/**
 * Aggregated app usage stats for top used apps.
 */
data class AppUsageStat(
    val packageName: String,
    val appLabel: String,
    val totalDurationMs: Long,
    val isAllowed: Boolean
)

/**
 * Count breakdown of Early Stop reasons.
 */
data class EarlyStopReasonStat(
    val reasonCode: String,
    val count: Int
)

/**
 * Complete aggregated report for a given time period.
 */
data class PeriodReportData(
    val periodType: PeriodType,
    val periodStart: Long,
    val periodEnd: Long,
    val zoneId: ZoneId,
    val summary: FocusSummary,
    val dailyStats: List<DailyFocusStat>,
    val weeklyStats: List<WeeklyFocusStat>,
    val hourlyPhoneUsage: List<HourlyPhoneUsageStat>,
    val topAppUsages: List<AppUsageStat>,
    val earlyStopReasons: List<EarlyStopReasonStat>
)
