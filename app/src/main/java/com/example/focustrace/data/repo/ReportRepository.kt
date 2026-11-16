package com.example.focustrace.data.repo

import com.example.focustrace.data.model.PeriodReportData
import com.example.focustrace.data.model.PeriodType
import com.example.focustrace.domain.ReportCalculator
import com.example.focustrace.sample.SampleData
import java.time.ZoneId

interface ReportRepository {
    suspend fun getReport(
        periodType: PeriodType,
        periodStart: Long,
        periodEnd: Long,
        zoneId: ZoneId
    ): PeriodReportData

    suspend fun deleteSession(sessionId: Long): Boolean
}

class SampleReportRepository(
    private val calculator: ReportCalculator = ReportCalculator()
) : ReportRepository {

    override suspend fun getReport(
        periodType: PeriodType,
        periodStart: Long,
        periodEnd: Long,
        zoneId: ZoneId
    ): PeriodReportData {
        return ReportCalculator(zoneId).calculateReport(
            periodType = periodType,
            periodStart = periodStart,
            periodEnd = periodEnd,
            sessions = SampleData.SESSIONS,
            phoneEvents = SampleData.PHONE_EVENTS,
            appUsages = SampleData.APP_USAGES
        )
    }

    override suspend fun deleteSession(sessionId: Long): Boolean {
        return true
    }
}
