package com.example.focustrace.ui.report

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.focustrace.data.db.AppDatabase
import com.example.focustrace.data.model.PeriodType
import com.example.focustrace.data.repo.ReportRepository
import com.example.focustrace.data.repo.RoomReportRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ReportViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ReportRepository = RoomReportRepository(AppDatabase.get(application))
    private val zoneId: ZoneId = ZoneId.systemDefault()

    private val _uiState = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()

    init {
        val today = LocalDate.now(zoneId)
        _uiState.update {
            it.copy(
                dayAnchorDate = today,
                weekAnchorDate = today,
                monthAnchorDate = today,
                selectedCalendarDate = today,
                calendarMonth = YearMonth.from(today)
            )
        }
        observeReports()
    }

    private fun observeReports() {
        // Day Report Flow
        viewModelScope.launch {
            _uiState.map { it.dayAnchorDate }
                .distinctUntilChanged()
                .flatMapLatest { date ->
                    val (start, end) = ReportPeriodUtils.calculatePeriodBounds(PeriodType.DAY, date, zoneId)
                    repository.observeReport(PeriodType.DAY, start, end, zoneId)
                }
                .collect { report ->
                    _uiState.update { it.copy(dayReport = report) }
                }
        }

        // Week Report Flow
        viewModelScope.launch {
            _uiState.map { it.weekAnchorDate }
                .distinctUntilChanged()
                .flatMapLatest { date ->
                    val (start, end) = ReportPeriodUtils.calculatePeriodBounds(PeriodType.WEEK, date, zoneId)
                    repository.observeReport(PeriodType.WEEK, start, end, zoneId)
                }
                .collect { report ->
                    _uiState.update { it.copy(weekReport = report) }
                }
        }

        // Month Report Flow
        viewModelScope.launch {
            _uiState.map { it.monthAnchorDate }
                .distinctUntilChanged()
                .flatMapLatest { date ->
                    val (start, end) = ReportPeriodUtils.calculatePeriodBounds(PeriodType.MONTH, date, zoneId)
                    repository.observeReport(PeriodType.MONTH, start, end, zoneId)
                }
                .collect { report ->
                    _uiState.update { it.copy(monthReport = report) }
                }
        }

        // Calendar Selected Day Report Flow
        viewModelScope.launch {
            _uiState.map { it.selectedCalendarDate }
                .distinctUntilChanged()
                .flatMapLatest { date ->
                    val start = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
                    val end = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
                    repository.observeReport(PeriodType.DAY, start, end, zoneId)
                }
                .collect { report ->
                    _uiState.update {
                        it.copy(
                            calendarDayReport = report,
                            calendarSessions = report.sessions
                        )
                    }
                }
        }

        // Calendar Month Dots Flow
        viewModelScope.launch {
            _uiState.map { it.calendarMonth }
                .distinctUntilChanged()
                .flatMapLatest { ym ->
                    val start = ym.atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
                    val end = ym.atEndOfMonth().plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
                    repository.observeReport(PeriodType.MONTH, start, end, zoneId)
                }
                .collect { report ->
                    val dates = report.sessions.map {
                        Instant.ofEpochMilli(it.startAt).atZone(zoneId).toLocalDate()
                    }.toSet()
                    _uiState.update { it.copy(calendarMonthReport = report, sessionDates = dates) }
                }
        }
    }

    fun setTabPosition(position: Int) {
        _uiState.update { it.copy(currentTabPosition = position) }
    }

    fun setViewMode(isOverview: Boolean) {
        _uiState.update { it.copy(isOverviewMode = isOverview) }
    }

    fun navigatePeriod(periodType: PeriodType, forward: Boolean) {
        _uiState.update { state ->
            when (periodType) {
                PeriodType.DAY -> {
                    val newDate = if (forward) state.dayAnchorDate.plusDays(1) else state.dayAnchorDate.minusDays(1)
                    state.copy(dayAnchorDate = newDate)
                }
                PeriodType.WEEK -> {
                    val newDate = if (forward) state.weekAnchorDate.plusWeeks(1) else state.weekAnchorDate.minusWeeks(1)
                    state.copy(weekAnchorDate = newDate)
                }
                PeriodType.MONTH -> {
                    val newDate = if (forward) state.monthAnchorDate.plusMonths(1) else state.monthAnchorDate.minusMonths(1)
                    state.copy(monthAnchorDate = newDate)
                }
            }
        }
    }

    fun selectCalendarDate(date: LocalDate) {
        _uiState.update { state ->
            state.copy(
                selectedCalendarDate = date,
                calendarMonth = YearMonth.from(date)
            )
        }
    }

    fun changeCalendarMonth(ym: YearMonth) {
        _uiState.update { it.copy(calendarMonth = ym) }
    }

    fun deleteSession(sessionId: Long, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.deleteSession(sessionId)
            onResult(success)
        }
    }
}
