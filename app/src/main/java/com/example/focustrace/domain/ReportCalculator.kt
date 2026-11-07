package com.example.focustrace.domain

import com.example.focustrace.data.model.AppUsageStat
import com.example.focustrace.data.model.DailyFocusStat
import com.example.focustrace.data.model.EarlyStopReasonStat
import com.example.focustrace.data.model.FocusSummary
import com.example.focustrace.data.model.HourlyPhoneUsageStat
import com.example.focustrace.data.model.PeriodReportData
import com.example.focustrace.data.model.PeriodType
import com.example.focustrace.data.model.ReportAppUsage
import com.example.focustrace.data.model.ReportPhoneEvent
import com.example.focustrace.data.model.ReportSession
import com.example.focustrace.data.model.WeeklyFocusStat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Pure Kotlin calculator for aggregating FocusTrace metrics across Day, Week, and Month periods.
 */
class ReportCalculator(
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {

    companion object {
        const val MILLIS_PER_HOUR = 3_600_000.0
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_EARLY_STOP = "EARLY_STOP"
        const val STATUS_RUNNING = "RUNNING"
    }

    /**
     * Calculates period report data for the given sessions, phone events, app usages, and time boundary.
     */
    fun calculateReport(
        periodType: PeriodType,
        periodStart: Long,
        periodEnd: Long,
        sessions: List<ReportSession>,
        phoneEvents: List<ReportPhoneEvent> = emptyList(),
        appUsages: List<ReportAppUsage> = emptyList()
    ): PeriodReportData {
        // 1. Filter sessions that started within period range and are not currently RUNNING
        val finishedSessions = sessions.filter { session ->
            session.status != STATUS_RUNNING &&
                    session.startAt >= periodStart &&
                    session.startAt < periodEnd
        }

        val sessionIds = finishedSessions.map { it.id }.toSet()
        val periodAppUsages = appUsages.filter { it.sessionId in sessionIds }

        // 2. Summary calculations
        val summary = calculateSummary(finishedSessions)

        // 3. Date range determination
        val startLocalDate = ZonedDateTime.ofInstant(Instant.ofEpochMilli(periodStart), zoneId).toLocalDate()
        val endLocalDate = ZonedDateTime.ofInstant(Instant.ofEpochMilli(periodEnd - 1), zoneId).toLocalDate()

        // 4. Daily breakdown
        val dailyStats = calculateDailyStats(finishedSessions, startLocalDate, endLocalDate)

        // 5. Weekly breakdown
        val weeklyStats = calculateWeeklyStats(dailyStats)

        // 6. Hourly phone usage breakdown (0..23)
        val hourlyPhoneUsage = calculateHourlyPhoneUsage(periodAppUsages)

        // 7. App usage statistics
        val topAppUsages = calculateTopAppUsages(periodAppUsages)

        // 8. Early stop reason counts
        val earlyStopReasons = calculateEarlyStopReasons(finishedSessions)

        return PeriodReportData(
            periodType = periodType,
            periodStart = periodStart,
            periodEnd = periodEnd,
            zoneId = zoneId,
            summary = summary,
            dailyStats = dailyStats,
            weeklyStats = weeklyStats,
            hourlyPhoneUsage = hourlyPhoneUsage,
            topAppUsages = topAppUsages,
            earlyStopReasons = earlyStopReasons
        )
    }

    fun calculateSummary(sessions: List<ReportSession>): FocusSummary {
        if (sessions.isEmpty()) {
            return FocusSummary(
                totalFocusMs = 0L,
                totalPhoneUseMs = 0L,
                totalCheckCount = 0,
                checksPerFocusHour = 0.0,
                goalCompletionRate = 0.0,
                completedSessionCount = 0,
                totalFinishedSessionCount = 0,
                longestFocusMs = 0L
            )
        }

        val totalFocusMs = sessions.sumOf { it.focusMs }
        val totalPhoneUseMs = sessions.sumOf { it.phoneUseMs }
        val totalCheckCount = sessions.sumOf { it.checkCount }

        val checksPerFocusHour = if (totalFocusMs > 0) {
            (totalCheckCount.toDouble() / totalFocusMs) * MILLIS_PER_HOUR
        } else {
            0.0
        }

        val completedCount = sessions.count { it.status == STATUS_COMPLETED }
        val totalFinished = sessions.size

        val goalCompletionRate = if (totalFinished > 0) {
            (completedCount.toDouble() / totalFinished) * 100.0
        } else {
            0.0
        }

        val longestFocusMs = sessions.maxOfOrNull { it.longestFocusMs } ?: 0L

        return FocusSummary(
            totalFocusMs = totalFocusMs,
            totalPhoneUseMs = totalPhoneUseMs,
            totalCheckCount = totalCheckCount,
            checksPerFocusHour = checksPerFocusHour,
            goalCompletionRate = goalCompletionRate,
            completedSessionCount = completedCount,
            totalFinishedSessionCount = totalFinished,
            longestFocusMs = longestFocusMs
        )
    }

    fun calculateDailyStats(
        sessions: List<ReportSession>,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<DailyFocusStat> {
        // Map sessions by their local date of startAt
        val sessionsByDate = sessions.groupBy { session ->
            ZonedDateTime.ofInstant(Instant.ofEpochMilli(session.startAt), zoneId).toLocalDate()
        }

        val result = mutableListOf<DailyFocusStat>()
        var currentDate = startDate
        while (!currentDate.isAfter(endDate)) {
            val daySessions = sessionsByDate[currentDate] ?: emptyList()
            val dayFocusMs = daySessions.sumOf { it.focusMs }
            val dayCheckCount = daySessions.sumOf { it.checkCount }

            result.add(
                DailyFocusStat(
                    date = currentDate,
                    focusMs = dayFocusMs,
                    checkCount = dayCheckCount
                )
            )
            currentDate = currentDate.plusDays(1)
        }
        return result
    }

    fun calculateWeeklyStats(dailyStats: List<DailyFocusStat>): List<WeeklyFocusStat> {
        if (dailyStats.isEmpty()) return emptyList()

        val weekBins = mutableListOf<MutableList<DailyFocusStat>>()
        var currentBin = mutableListOf<DailyFocusStat>()

        for (stat in dailyStats) {
            if (currentBin.isNotEmpty() && stat.date.dayOfWeek == DayOfWeek.MONDAY) {
                weekBins.add(currentBin)
                currentBin = mutableListOf()
            }
            currentBin.add(stat)
        }
        if (currentBin.isNotEmpty()) {
            weekBins.add(currentBin)
        }

        return weekBins.mapIndexed { index, bin ->
            val start = bin.first().date
            val end = bin.last().date
            val totalFocus = bin.sumOf { it.focusMs }
            WeeklyFocusStat(
                weekLabel = "Week ${index + 1}",
                startDate = start,
                endDate = end,
                focusMs = totalFocus
            )
        }
    }

    fun calculateHourlyPhoneUsage(appUsages: List<ReportAppUsage>): List<HourlyPhoneUsageStat> {
        // Filter counted non-allowed usages
        val countedUsages = appUsages.filter { it.isCounted && !it.isAllowed }

        val usageByHour = mutableMapOf<Int, Long>()
        for (i in 0..23) {
            usageByHour[i] = 0L
        }

        for (usage in countedUsages) {
            val hour = ZonedDateTime.ofInstant(Instant.ofEpochMilli(usage.startAt), zoneId).hour
            usageByHour[hour] = (usageByHour[hour] ?: 0L) + usage.durationMs
        }

        return (0..23).map { hour ->
            HourlyPhoneUsageStat(
                hourOfDay = hour,
                usageMs = usageByHour[hour] ?: 0L
            )
        }
    }

    fun calculateTopAppUsages(appUsages: List<ReportAppUsage>): List<AppUsageStat> {
        return appUsages
            .groupBy { it.packageName to it.appLabel }
            .map { (key, usages) ->
                AppUsageStat(
                    packageName = key.first,
                    appLabel = key.second,
                    totalDurationMs = usages.sumOf { it.durationMs },
                    isAllowed = usages.firstOrNull()?.isAllowed ?: false
                )
            }
            .sortedByDescending { it.totalDurationMs }
    }

    fun calculateEarlyStopReasons(sessions: List<ReportSession>): List<EarlyStopReasonStat> {
        return sessions
            .filter { it.status == STATUS_EARLY_STOP && !it.stopReason.isNullOrBlank() }
            .groupBy { it.stopReason!! }
            .map { (reason, list) ->
                EarlyStopReasonStat(
                    reasonCode = reason,
                    count = list.size
                )
            }
            .sortedByDescending { it.count }
    }
}
