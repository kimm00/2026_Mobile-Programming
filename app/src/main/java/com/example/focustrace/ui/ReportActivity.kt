
package com.example.focustrace.ui

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.focustrace.R
import com.example.focustrace.data.db.AppDatabase
import com.example.focustrace.data.model.PeriodReportData
import com.example.focustrace.data.model.PeriodType
import com.example.focustrace.data.repo.ReportRepository
import com.example.focustrace.data.repo.RoomReportRepository
import com.example.focustrace.domain.ReportCalculator
import com.example.focustrace.ui.report.FocusBarChartBinder
import com.github.mikephil.charting.charts.BarChart
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class ReportActivity : AppCompatActivity() {

    private val zoneId: ZoneId = ZoneId.systemDefault()

    private val repository: ReportRepository by lazy {
        RoomReportRepository(
            AppDatabase.get(applicationContext)
        )
    }

    private var selectedPeriodType = PeriodType.WEEK
    private var selectedDate = LocalDate.now(zoneId)
    private var reportJob: Job? = null

    // Period navigation
    private lateinit var tabLayoutPeriod: TabLayout
    private lateinit var tvReportSubtitle: TextView
    private lateinit var btnPrevPeriod: Button
    private lateinit var btnNextPeriod: Button

    // Existing summary cards
    private lateinit var tvTotalFocusValue: TextView
    private lateinit var tvChecksPerHourValue: TextView
    private lateinit var tvCompletionRateValue: TextView
    private lateinit var tvLongestFocusValue: TextView

    // New phone usage cards
    private lateinit var tvPhoneChecksValue: TextView
    private lateinit var tvPhoneUsageValue: TextView
    private lateinit var tvMostUsedAppsContent: TextView

    // Day timeline
    private lateinit var cardDayTimeline: View
    private lateinit var tvDayTimelineContent: TextView

    // Week / Month charts
    private lateinit var cardChartContainer: View
    private lateinit var tvChartTitle: TextView
    private lateinit var tvChartSubtitle: TextView
    private lateinit var barChartFocusByDay: BarChart

    // Early stop
    private lateinit var tvEarlyStopReasonsSummary: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)

        initViews()
        setupListeners()

        // Default period: Week
        tabLayoutPeriod.getTabAt(1)?.select()
    }

    override fun onResume() {
        super.onResume()

        if (::tabLayoutPeriod.isInitialized) {
            updateReport()
        }
    }

    private fun initViews() {

        tabLayoutPeriod = findViewById(R.id.tabLayoutPeriod)
        tvReportSubtitle = findViewById(R.id.tvReportSubtitle)
        btnPrevPeriod = findViewById(R.id.btnPrevPeriod)
        btnNextPeriod = findViewById(R.id.btnNextPeriod)

        tvTotalFocusValue =
            findViewById(R.id.tvTotalFocusValue)

        tvChecksPerHourValue =
            findViewById(R.id.tvChecksPerHourValue)

        tvCompletionRateValue =
            findViewById(R.id.tvCompletionRateValue)

        tvLongestFocusValue =
            findViewById(R.id.tvLongestFocusValue)

        // New metrics
        tvPhoneChecksValue =
            findViewById(R.id.tvPhoneChecksValue)

        tvPhoneUsageValue =
            findViewById(R.id.tvPhoneUsageValue)

        tvMostUsedAppsContent =
            findViewById(R.id.tvMostUsedAppsContent)

        cardDayTimeline =
            findViewById(R.id.cardDayTimeline)

        tvDayTimelineContent =
            findViewById(R.id.tvDayTimelineContent)

        cardChartContainer =
            findViewById(R.id.cardChartContainer)

        tvChartTitle =
            findViewById(R.id.tvChartTitle)

        tvChartSubtitle =
            findViewById(R.id.tvChartSubtitle)

        barChartFocusByDay =
            findViewById(R.id.barChartFocusByDay)

        tvEarlyStopReasonsSummary =
            findViewById(R.id.tvEarlyStopReasonsSummary)
    }

    private fun setupListeners() {

        tabLayoutPeriod.addOnTabSelectedListener(
            object : TabLayout.OnTabSelectedListener {

                override fun onTabSelected(
                    tab: TabLayout.Tab?
                ) {
                    selectedPeriodType = when (tab?.position) {
                        0 -> PeriodType.DAY
                        1 -> PeriodType.WEEK
                        2 -> PeriodType.MONTH
                        else -> PeriodType.WEEK
                    }

                    updateReport()
                }

                override fun onTabUnselected(
                    tab: TabLayout.Tab?
                ) {}

                override fun onTabReselected(
                    tab: TabLayout.Tab?
                ) {}
            }
        )

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

        val requestedPeriodType = selectedPeriodType
        val requestedDate = selectedDate

        val (startMs, endMs) = when (requestedPeriodType) {

            PeriodType.DAY ->
                ReportCalculator.calculateDayBounds(
                    requestedDate, zoneId
                )

            PeriodType.WEEK ->
                ReportCalculator.calculateWeekBounds(
                    requestedDate, zoneId
                )

            PeriodType.MONTH ->
                ReportCalculator.calculateMonthBounds(
                    requestedDate, zoneId
                )
        }

        // Cancel previous request
        reportJob?.cancel()

        renderDateHeader(
            requestedPeriodType,
            startMs,
            endMs
        )

        reportJob = lifecycleScope.launch {

            try {

                val reportData = withContext(Dispatchers.IO) {
                    repository.getReport(
                        periodType = requestedPeriodType,
                        periodStart = startMs,
                        periodEnd = endMs,
                        zoneId = zoneId
                    )
                }

                // Existing summary
                renderSummaryCards(reportData)

                // New app usage list
                renderAppUsages(reportData)

                // Existing charts / timeline
                renderMainContent(reportData)

                // Existing early stop reasons
                renderEarlyStopReasons(reportData)

            } catch (e: CancellationException) {
                throw e

            } catch (e: Exception) {

                Log.e(
                    "ReportActivity",
                    "Failed to load Room report",
                    e
                )

                Toast.makeText(
                    this@ReportActivity,
                    "Failed to load report",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun renderDateHeader(
        periodType: PeriodType,
        startMs: Long,
        endMs: Long
    ) {

        val startDate = ZonedDateTime.ofInstant(
            Instant.ofEpochMilli(startMs),
            zoneId
        ).toLocalDate()

        val endDateInclusive = ZonedDateTime.ofInstant(
            Instant.ofEpochMilli(endMs - 1),
            zoneId
        ).toLocalDate()

        val text = when (periodType) {

            PeriodType.DAY -> {

                val formatter = DateTimeFormatter.ofPattern(
                    "EEE, MMM d, yyyy",
                    Locale.ENGLISH
                )

                startDate.format(formatter)
            }

            PeriodType.WEEK -> {

                val formatter = DateTimeFormatter.ofPattern(
                    "MMM d, yyyy",
                    Locale.ENGLISH
                )

                "${startDate.format(formatter)} – " +
                        endDateInclusive.format(formatter)
            }

            PeriodType.MONTH -> {

                val formatter = DateTimeFormatter.ofPattern(
                    "MMMM yyyy",
                    Locale.ENGLISH
                )

                startDate.format(formatter)
            }
        }

        tvReportSubtitle.text = text
    }

    private fun renderSummaryCards(
        report: PeriodReportData
    ) {

        val summary = report.summary

        // Total Focus
        val focusMinutes =
            summary.totalFocusMs / 60_000L

        val hours = focusMinutes / 60L
        val minutes = focusMinutes % 60L

        tvTotalFocusValue.text = if (hours > 0L) {
            "${hours}h ${minutes}m"
        } else {
            "${minutes}m"
        }

        // Checks per Hour
        tvChecksPerHourValue.text = String.format(
            Locale.US,
            "%.2f / hr",
            summary.checksPerFocusHour
        )

        // Goal Completion
        tvCompletionRateValue.text = String.format(
            Locale.US,
            "%.1f%%",
            summary.goalCompletionRate
        )

        // Longest Focus
        val longestMinutes =
            summary.longestFocusMs / 60_000L

        tvLongestFocusValue.text =
            "$longestMinutes min"

        // NEW: Total Phone Checks
        tvPhoneChecksValue.text =
            "${summary.totalCheckCount} times"

        // NEW: Total Phone Usage
        val phoneSeconds =
            summary.totalPhoneUseMs / 1_000L

        val phoneHours = phoneSeconds / 3_600L
        val phoneMinutes = (phoneSeconds % 3_600L) / 60L
        val remainingSeconds = phoneSeconds % 60L

        tvPhoneUsageValue.text = when {
            phoneHours > 0L ->
                "${phoneHours}h ${phoneMinutes}m"

            phoneMinutes > 0L ->
                "${phoneMinutes}m ${remainingSeconds}s"

            else ->
                "${remainingSeconds}s"
        }
    }

    // NEW: Display most used apps
    private fun renderAppUsages(
        report: PeriodReportData
    ) {

        val apps = report.topAppUsages
            .filter { it.totalDurationMs > 0L }
            .take(5)

        if (apps.isEmpty()) {

            tvMostUsedAppsContent.text =
                "No app usage recorded"

            return
        }

        val content = apps.mapIndexed { index, app ->

            val totalSeconds =
                app.totalDurationMs / 1_000L

            val minutes = totalSeconds / 60L
            val seconds = totalSeconds % 60L

            val durationText = if (minutes > 0L) {
                "${minutes}m ${seconds}s"
            } else {
                "${seconds}s"
            }

            val appName = app.appLabel
                .takeIf { it.isNotBlank() }
                ?: app.packageName

            "${index + 1}. $appName — $durationText"

        }.joinToString("\n")

        tvMostUsedAppsContent.text = content
    }

    private fun renderMainContent(
        report: PeriodReportData
    ) {

        when (report.periodType) {

            PeriodType.DAY -> {

                cardDayTimeline.visibility = View.VISIBLE
                cardChartContainer.visibility = View.GONE

                renderDayTimeline(report)
            }

            PeriodType.WEEK -> {

                cardDayTimeline.visibility = View.GONE
                cardChartContainer.visibility = View.VISIBLE

                tvChartTitle.text =
                    "Focus time by day"

                tvChartSubtitle.text =
                    "Daily total focus minutes"

                FocusBarChartBinder.bindChart(
                    barChartFocusByDay,
                    report.dailyStats
                )
            }

            PeriodType.MONTH -> {

                cardDayTimeline.visibility = View.GONE
                cardChartContainer.visibility = View.VISIBLE

                tvChartTitle.text =
                    "Focus time by week"

                tvChartSubtitle.text =
                    "Weekly total focus minutes"

                FocusBarChartBinder.bindWeeklyChart(
                    barChartFocusByDay,
                    report.weeklyStats
                )
            }
        }
    }

    private fun renderDayTimeline(
        report: PeriodReportData
    ) {

        val sessions = report.sessions

        if (sessions.isEmpty()) {

            tvDayTimelineContent.text =
                "No focus sessions on this day."

            return
        }

        val timeFormatter = DateTimeFormatter.ofPattern(
            "HH:mm",
            Locale.ENGLISH
        )

        val sb = StringBuilder()

        sessions.forEachIndexed { index, session ->

            val startTime = ZonedDateTime.ofInstant(
                Instant.ofEpochMilli(session.startAt),
                zoneId
            ).format(timeFormatter)

            val endTime = if (session.endAt != null) {

                ZonedDateTime.ofInstant(
                    Instant.ofEpochMilli(session.endAt),
                    zoneId
                ).format(timeFormatter)

            } else {
                "Ongoing"
            }

            val focusMinutes =
                session.focusMs / 60_000L

            sb.append(
                "$startTime - $endTime | " +
                        "${session.subject} " +
                        "(${focusMinutes}m focus, " +
                        "${session.status})\n"
            )

            val sessionEvents = report.phoneEvents.filter {
                it.sessionId == session.id
            }

            sessionEvents.forEach { event ->

                val eventStart = ZonedDateTime.ofInstant(
                    Instant.ofEpochMilli(event.startAt),
                    zoneId
                ).format(timeFormatter)

                val eventEnd = ZonedDateTime.ofInstant(
                    Instant.ofEpochMilli(event.endAt),
                    zoneId
                ).format(timeFormatter)

                sb.append(
                    "    $eventStart - $eventEnd " +
                            "Phone Check (${event.kind})\n"
                )
            }

            if (index < sessions.size - 1) {
                sb.append("\n")
            }
        }

        tvDayTimelineContent.text =
            sb.toString().trimEnd()
    }

    private fun renderEarlyStopReasons(
        report: PeriodReportData
    ) {

        val reasons = report.earlyStopReasons

        tvEarlyStopReasonsSummary.text =
            if (reasons.isNotEmpty()) {

                reasons.joinToString(", ") {
                    "${it.reasonCode}: ${it.count}"
                }

            } else {
                "None"
            }
    }
}
