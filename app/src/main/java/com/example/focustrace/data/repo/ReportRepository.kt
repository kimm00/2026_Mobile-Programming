package com.example.focustrace.data.repo

import com.example.focustrace.data.model.PeriodReportData
import com.example.focustrace.data.model.PeriodType
import com.example.focustrace.domain.ReportCalculator
import com.example.focustrace.sample.SampleData
import java.time.ZoneId

/**
 * Repository interface for obtaining Focus Report data.
 * Isolates data sourcing so SampleData can later be seamlessly replaced by Room Database.
 */
interface ReportRepository {
    fun getReport(
        periodType: PeriodType,
        periodStart: Long,
        periodEnd: Long,
        zoneId: ZoneId
    ): PeriodReportData
}

/**
 * Sample implementation of ReportRepository backed by SampleData.
 */
class SampleReportRepository(
    private val calculator: ReportCalculator = ReportCalculator()
) : ReportRepository {

    override fun getReport(
        periodType: PeriodType,
        periodStart: Long,
        periodEnd: Long,
        zoneId: ZoneId
    ): PeriodReportData {
        val calc = if (calculator.let { true }) ReportCalculator(zoneId) else calculator
        return calc.calculateReport(
            periodType = periodType,
            periodStart = periodStart,
            periodEnd = periodEnd,
            sessions = SampleData.SESSIONS,
            phoneEvents = SampleData.PHONE_EVENTS,
            appUsages = SampleData.APP_USAGES
        )
    }
}