package com.example.focustrace.ui.report

import com.example.focustrace.data.model.PeriodReportData
import com.example.focustrace.data.model.ReportSession
import java.time.LocalDate
import java.time.YearMonth

data class ReportUiState(
    val dayAnchorDate: LocalDate = LocalDate.now(),
    val weekAnchorDate: LocalDate = LocalDate.now(),
    val monthAnchorDate: LocalDate = LocalDate.now(),
    val currentTabPosition: Int = 1, // Default to Week (index 1)
    val dayReport: PeriodReportData? = null,
    val weekReport: PeriodReportData? = null,
    val monthReport: PeriodReportData? = null,

    val isOverviewMode: Boolean = true,
    val selectedCalendarDate: LocalDate = LocalDate.now(),
    val calendarMonth: YearMonth = YearMonth.now(),
    val calendarDayReport: PeriodReportData? = null,
    val calendarMonthReport: PeriodReportData? = null,
    val calendarSessions: List<ReportSession> = emptyList(),
    val sessionDates: Set<LocalDate> = emptySet()
)
