package com.example.focustrace.data.repo

import androidx.room.withTransaction
import com.example.focustrace.data.db.AppDatabase
import com.example.focustrace.data.model.PeriodReportData
import com.example.focustrace.data.model.PeriodType
import com.example.focustrace.data.model.ReportAppUsage
import com.example.focustrace.data.model.ReportPhoneEvent
import com.example.focustrace.data.model.ReportSession
import com.example.focustrace.domain.ReportCalculator
import java.time.ZoneId

class RoomReportRepository(
    private val database: AppDatabase
) : ReportRepository {

    override suspend fun getReport(
        periodType: PeriodType,
        periodStart: Long,
        periodEnd: Long,
        zoneId: ZoneId
    ): PeriodReportData {

        val dao = database.sessionDao()

        // Read related records in one consistent transaction.
        val records = database.withTransaction {
            val sessions = dao.getSessionsByPeriod(
                periodStart,
                periodEnd
            )

            val ids = sessions.map { it.id }

            val events = if (ids.isEmpty()) {
                emptyList()
            } else {
                dao.getEventsBySessionIds(ids)
            }

            val usages = if (ids.isEmpty()) {
                emptyList()
            } else {
                dao.getUsagesBySessionIds(ids)
            }

            Triple(sessions, events, usages)
        }

        val (sessions, events, usages) = records

        val reportSessions = sessions.map { entity ->
            ReportSession(
                id = entity.id,
                subject = entity.subject,
                plan = entity.plan,
                goalMinutes = entity.goalMinutes,
                startAt = entity.startAt,
                endAt = entity.endAt,
                status = entity.status,
                stopReason = entity.stopReason,
                stopMemo = entity.stopMemo,
                reflection = entity.reflection,
                photoUri = entity.photoUri,
                focusMs = entity.focusMs,
                phoneUseMs = entity.phoneUseMs,
                checkCount = entity.checkCount,
                unlockOnlyCount = entity.unlockOnlyCount,
                longestFocusMs = entity.longestFocusMs
            )
        }

        val reportEvents = events.map { entity ->
            ReportPhoneEvent(
                id = entity.id,
                sessionId = entity.sessionId,
                startAt = entity.startAt,
                endAt = entity.endAt,
                kind = entity.kind
            )
        }

        val reportUsages = usages.map { entity ->
            ReportAppUsage(
                id = entity.id,
                eventId = entity.eventId,
                sessionId = entity.sessionId,
                packageName = entity.packageName,
                appLabel = entity.appLabel,
                startAt = entity.startAt,
                durationMs = entity.durationMs,
                isAllowed = entity.isAllowed,
                isAuto = entity.isAuto,
                isCounted = entity.isCounted
            )
        }

        val previousSummary = if (periodType == PeriodType.WEEK || periodType == PeriodType.MONTH) {
            val (prevStart, prevEnd) = if (periodType == PeriodType.WEEK) {
                (periodStart - 7 * 86_400_000L) to periodStart
            } else {
                val zdt = java.time.ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(periodStart), zoneId)
                val prevMonthZdt = zdt.minusMonths(1).withDayOfMonth(1)
                prevMonthZdt.toInstant().toEpochMilli() to periodStart
            }
            val prevSessions = dao.getSessionsByPeriod(prevStart, prevEnd)
            val prevReportSessions = prevSessions.map { entity ->
                ReportSession(
                    id = entity.id,
                    subject = entity.subject,
                    plan = entity.plan,
                    goalMinutes = entity.goalMinutes,
                    startAt = entity.startAt,
                    endAt = entity.endAt,
                    status = entity.status,
                    stopReason = entity.stopReason,
                    stopMemo = entity.stopMemo,
                    reflection = entity.reflection,
                    photoUri = entity.photoUri,
                    focusMs = entity.focusMs,
                    phoneUseMs = entity.phoneUseMs,
                    checkCount = entity.checkCount,
                    unlockOnlyCount = entity.unlockOnlyCount,
                    longestFocusMs = entity.longestFocusMs
                )
            }
            ReportCalculator(zoneId).calculateSummary(prevReportSessions)
        } else {
            null
        }

        return ReportCalculator(zoneId).calculateReport(
            periodType = periodType,
            periodStart = periodStart,
            periodEnd = periodEnd,
            sessions = reportSessions,
            phoneEvents = reportEvents,
            appUsages = reportUsages,
            previousSummary = previousSummary
        )
    }

    override suspend fun deleteSession(sessionId: Long): Boolean {
        return database.sessionDao().deleteSession(sessionId) > 0
    }
}
