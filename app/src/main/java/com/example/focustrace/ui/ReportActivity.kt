package com.example.focustrace.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.focustrace.R
import com.example.focustrace.data.model.PeriodReportData
import com.example.focustrace.data.model.PeriodType
import com.example.focustrace.data.repo.ReportRepository
import com.example.focustrace.data.repo.SampleReportRepository
import com.example.focustrace.domain.ReportCalculator
import com.example.focustrace.sample.SampleData
import com.example.focustrace.ui.report.FocusBarChartBinder
import com.github.mikephil.charting.charts.BarChart
import com.google.android.material.tabs.TabLayout
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class ReportActivity : AppCompatActivity() {

    private val zoneId: ZoneId = SampleData.TEST_ZONE_ID
    private val repository: ReportRepository = SampleReportRepository(ReportCalculator(zoneId))

    private var selectedPeriodType: PeriodType = PeriodType.WEEK
    private var selectedDate: LocalDate = SampleData.BASE_START_DATE

    private lateinit var tabLayoutPeriod: TabLayout
    private lateinit var tvReportSubtitle: TextView
    private lateinit var btnPrevPeriod: Button
    private lateinit var btnNextPeriod: Button

    private lateinit var tvTotalFocusValue: TextView
    private lateinit var tvChecksPerHourValue: TextView
    private lateinit var tvCompletionRateValue: TextView
    private lateinit var tvLongestFocusValue: TextView

    private lateinit var cardDayTimeline: View
    private lateinit var tvDayTimelineContent: TextView

    private lateinit var cardChartContainer: View
    private lateinit var tvChartTitle: TextView
    private lateinit var tvChartSubtitle: TextView
    private lateinit var barChartFocusByDay: BarChart

    private lateinit var tvEarlyStopReasonsSummary: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)

        initViews()
        setupListeners()

        // Set initial tab selection to Week (index 1)
        tabLayoutPeriod.getTabAt(1)?.select()
        updateReport()
    }

    private fun initViews() {
        tabLayoutPeriod = findViewById(R.id.tabLayoutPeriod)
        tvReportSubtitle = findViewById(R.id.tvReportSubtitle)
        btnPrevPeriod = findViewById(R.id.btnPrevPeriod)
        btnNextPeriod = findViewById(R.id.btnNextPeriod)

        tvTotalFocusValue = findViewById(R.id.tvTotalFocusValue)
        tvChecksPerHourValue = findViewById(R.id.tvChecksPerHourValue)
        tvCompletionRateValue = findViewById(R.id.tvCompletionRateValue)
        tvLongestFocusValue = findViewById(R.id.tvLongestFocusValue)

        cardDayTimeline = findViewById(R.id.cardDayTimeline)
        tvDayTimelineContent = findViewById(R.id.tvDayTimelineContent)

        cardChartContainer = findViewById(R.id.cardChartContainer)
        tvChartTitle = findViewById(R.id.tvChartTitle)
        tvChartSubtitle = findViewById(R.id.tvChartSubtitle)
        barChartFocusByDay = findViewById(R.id.barChartFocusByDay)

        tvEarlyStopReasonsSummary = findViewById(R.id.tvEarlyStopReasonsSummary)
    }

    private fun setupListeners() {
        tabLayoutPeriod.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                selectedPeriodType = when (tab?.position) {
                    0 -> PeriodType.DAY
                    1 -> PeriodType.WEEK
                    2 -> PeriodType.MONTH
                    else -> PeriodType.WEEK
                }
                updateReport()
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        btnPrevPeriod.setOnClickListener {
            selectedDate = when (selectedPeriodType) {
                PeriodType.DAY -> selectedDate.minusDays(1)
                PeriodType.WEEK -> selectedDate.minusWeeks(1)
                PeriodType.MONTH -> selectedDate.minusMonths(1)
            }
            updateReport()
        }

        btnNextPeriod.setOnClickListener {
            selectedDate = when (selectedPeriodType) {
                PeriodType.DAY -> selectedDate.plusDays(1)
                PeriodType.WEEK -> selectedDate.plusWeeks(1)
                PeriodType.MONTH -> selectedDate.plusMonths(1)
            }
            updateReport()
        }
    }

    private fun updateReport() {
        val (startMs, endMs) = when (selectedPeriodType) {
            PeriodType.DAY -> ReportCalculator.calculateDayBounds(selectedDate, zoneId)
            PeriodType.WEEK -> ReportCalculator.calculateWeekBounds(selectedDate, zoneId)
            PeriodType.MONTH -> ReportCalculator.calculateMonthBounds(selectedDate, zoneId)
        }

        val reportData = repository.getReport(selectedPeriodType, startMs, endMs, zoneId)

        renderDateHeader(startMs, endMs)
        renderSummaryCards(reportData)
        renderMainContent(reportData)
        renderEarlyStopReasons(reportData)
    }

    private fun renderDateHeader(startMs: Long, endMs: Long) {
        val startDate = ZonedDateTime.ofInstant(Instant.ofEpochMilli(startMs), zoneId).toLocalDate()
        val endDateInclusive = ZonedDateTime.ofInstant(Instant.ofEpochMilli(endMs - 1), zoneId).toLocalDate()

        val text = when (selectedPeriodType) {
            PeriodType.DAY -> {
                val dayFormatter = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.ENGLISH)
                startDate.format(dayFormatter)
            }
            PeriodType.WEEK -> {
                val weekFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
                "${startDate.format(weekFormatter)} – ${endDateInclusive.format(weekFormatter)}"
            }
            PeriodType.MONTH -> {
                val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
                startDate.format(monthFormatter)
            }
        }
        tvReportSubtitle.text = text
    }

    private fun renderSummaryCards(report: PeriodReportData) {
        val summary = report.summary

        val focusMinutes = summary.totalFocusMs / (60 * 1000)
        val hours = focusMinutes / 60
        val mins = focusMinutes % 60
        tvTotalFocusValue.text = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"

        tvChecksPerHourValue.text = String.format(Locale.US, "%.2f / hr", summary.checksPerFocusHour)
        tvCompletionRateValue.text = String.format(Locale.US, "%.1f%%", summary.goalCompletionRate)

        val longestMins = summary.longestFocusMs / (60 * 1000)
        tvLongestFocusValue.text = "${longestMins} min"
    }

    private fun renderMainContent(report: PeriodReportData) {
        when (selectedPeriodType) {
            PeriodType.DAY -> {
                cardDayTimeline.visibility = View.VISIBLE
                cardChartContainer.visibility = View.GONE
                renderDayTimeline(report)
            }
            PeriodType.WEEK -> {
                cardDayTimeline.visibility = View.GONE
                cardChartContainer.visibility = View.VISIBLE
                tvChartTitle.text = "Focus time by day"
                tvChartSubtitle.text = "Daily total focus minutes"
                FocusBarChartBinder.bindChart(barChartFocusByDay, report.dailyStats)
            }
            PeriodType.MONTH -> {
                cardDayTimeline.visibility = View.GONE
                cardChartContainer.visibility = View.VISIBLE
                tvChartTitle.text = "Focus time by week"
                tvChartSubtitle.text = "Weekly total focus minutes"
                FocusBarChartBinder.bindWeeklyChart(barChartFocusByDay, report.weeklyStats)
            }
        }
    }

    private fun renderDayTimeline(report: PeriodReportData) {
        val sessions = report.sessions
        if (sessions.isEmpty()) {
            tvDayTimelineContent.text = "No focus sessions on this day."
            return
        }

        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
        val sb = StringBuilder()

        sessions.forEachIndexed { sIndex, session ->
            val startTimeStr = ZonedDateTime.ofInstant(Instant.ofEpochMilli(session.startAt), zoneId).format(timeFormatter)
            val endTimeStr = if (session.endAt != null) {
                ZonedDateTime.ofInstant(Instant.ofEpochMilli(session.endAt), zoneId).format(timeFormatter)
            } else {
                "Ongoing"
            }
            val focusMins = session.focusMs / (60 * 1000)

            sb.append("• $startTimeStr - $endTimeStr | ${session.subject} (${focusMins}m focus, ${session.status})\n")

            val sessionEvents = report.phoneEvents.filter { it.sessionId == session.id }
            sessionEvents.forEach { event ->
                val eStart = ZonedDateTime.ofInstant(Instant.ofEpochMilli(event.startAt), zoneId).format(timeFormatter)
                val eEnd = ZonedDateTime.ofInstant(Instant.ofEpochMilli(event.endAt), zoneId).format(timeFormatter)
                sb.append("    └ $eStart - $eEnd Phone Check (${event.kind})\n")
            }

            if (sIndex < sessions.size - 1) {
                sb.append("\n")
            }
        }

        tvDayTimelineContent.text = sb.toString().trimEnd()
    }

    private fun renderEarlyStopReasons(report: PeriodReportData) {
        val reasons = report.earlyStopReasons
        tvEarlyStopReasonsSummary.text = if (reasons.isNotEmpty()) {
            reasons.joinToString(", ") { "${it.reasonCode}: ${it.count}" }
        } else {
            "None"
        }
    }
}
