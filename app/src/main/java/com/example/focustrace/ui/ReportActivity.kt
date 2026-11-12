package com.example.focustrace.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.CalendarView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.focustrace.MainActivity
import com.example.focustrace.R
import com.example.focustrace.data.db.AppDatabase
import com.example.focustrace.data.model.PeriodReportData
import com.example.focustrace.data.model.PeriodType
import com.example.focustrace.data.repo.ReportRepository
import com.example.focustrace.data.repo.RoomReportRepository
import com.example.focustrace.databinding.ActivityReportBinding
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

    private lateinit var binding: ActivityReportBinding
    private val zoneId: ZoneId = ZoneId.systemDefault()

    private val repository: ReportRepository by lazy {
        RoomReportRepository(
            AppDatabase.get(applicationContext)
        )
    }

    private var selectedPeriodType = PeriodType.WEEK
    private var selectedDate = LocalDate.now(zoneId)
    private var reportJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupAppBarAndNavigation()
        setupViewModeToggle()
        setupPeriodTabs()
        setupCalendarView()

        // Default period: Week
        binding.tabLayoutPeriod.getTabAt(1)?.select()
    }

    override fun onResume() {
        super.onResume()
        updateReport()
    }

    private fun setupAppBarAndNavigation() {
        binding.btnAiInsightReport.setOnClickListener {
            startActivity(Intent(this, AiInsightTestActivity::class.java))
        }

        binding.btnNavHome.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        binding.btnNavReport.setOnClickListener {
            // Already on Report
        }

        binding.btnNavJournal.setOnClickListener {
            startActivity(Intent(this, JournalActivity::class.java))
            finish()
        }
    }

    private fun setupViewModeToggle() {
        binding.tabLayoutViewMode.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                if (tab?.position == 1) {
                    // Calendar view
                    binding.layoutOverviewContainer.visibility = View.GONE
                    binding.layoutCalendarContainer.visibility = View.VISIBLE
                } else {
                    // Overview view
                    binding.layoutOverviewContainer.visibility = View.VISIBLE
                    binding.layoutCalendarContainer.visibility = View.GONE
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupPeriodTabs() {
        binding.tabLayoutPeriod.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        selectedPeriodType = PeriodType.DAY
                        selectedDate = LocalDate.now(zoneId)
                    }
                    1 -> {
                        selectedPeriodType = PeriodType.WEEK
                        selectedDate = LocalDate.now(zoneId)
                    }
                    2 -> {
                        selectedPeriodType = PeriodType.MONTH
                        selectedDate = LocalDate.now(zoneId)
                    }
                }
                updateReport()
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        binding.btnPrevPeriod.setOnClickListener {
            selectedDate = when (selectedPeriodType) {
                PeriodType.DAY -> selectedDate.minusDays(1)
                PeriodType.WEEK -> selectedDate.minusWeeks(1)
                PeriodType.MONTH -> selectedDate.minusMonths(1)
            }
            updateReport()
        }

        binding.btnNextPeriod.setOnClickListener {
            selectedDate = when (selectedPeriodType) {
                PeriodType.DAY -> selectedDate.plusDays(1)
                PeriodType.WEEK -> selectedDate.plusWeeks(1)
                PeriodType.MONTH -> selectedDate.plusMonths(1)
            }
            updateReport()
        }
    }

    private fun setupCalendarView() {
        binding.calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val clickedDate = LocalDate.of(year, month + 1, dayOfMonth)
            loadCalendarDaySessions(clickedDate)
        }

        // Default load today's sessions in calendar mode
        loadCalendarDaySessions(LocalDate.now(zoneId))
    }

    private fun loadCalendarDaySessions(date: LocalDate) {
        lifecycleScope.launch {
            val dateStr = date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH))
            binding.tvSelectedDayTitle.text = dateStr

            val startOfDay = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val endOfDay = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1

            val report = withContext(Dispatchers.IO) {
                repository.getReport(PeriodType.DAY, startOfDay, endOfDay, zoneId)
            }

            if (report.sessions.isEmpty()) {
                binding.tvSelectedDaySessionsSummary.text = "No sessions recorded on $dateStr."
            } else {
                val focusMins = report.summary.totalFocusMs / 60000
                val checkCount = report.summary.totalCheckCount
                val sessionCount = report.sessions.size

                val sb = StringBuilder()
                sb.append("Total: $sessionCount session(s), ${focusMins} min focus, $checkCount phone check(s).\n\n")

                report.sessions.forEachIndexed { idx, session ->
                    val sessionFocus = session.focusMs / 60000
                    val startTime = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
                        .format(Instant.ofEpochMilli(session.startAt).atZone(zoneId))

                    sb.append("${idx + 1}. [${session.subject}] $startTime · ${sessionFocus}m focus (${session.status})\n")
                }

                binding.tvSelectedDaySessionsSummary.text = sb.toString().trim()
            }
        }
    }

    private fun updateReport() {
        reportJob?.cancel()

        val periodType = selectedPeriodType
        val anchorDate = selectedDate

        reportJob = lifecycleScope.launch {
            val (periodStart, periodEnd) = calculatePeriodBounds(periodType, anchorDate, zoneId)
            updateSubtitle(periodType, anchorDate, zoneId)

            try {
                val reportData = withContext(Dispatchers.IO) {
                    repository.getReport(periodType, periodStart, periodEnd, zoneId)
                }
                renderReport(reportData)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(this@ReportActivity, "Failed to load report", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateSubtitle(periodType: PeriodType, anchorDate: LocalDate, zoneId: ZoneId) {
        val formatterDay = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)

        val text = when (periodType) {
            PeriodType.DAY -> anchorDate.format(formatterDay)
            PeriodType.WEEK -> {
                val start = anchorDate.minusDays(6)
                "${start.format(formatterDay)} – ${anchorDate.format(formatterDay)}"
            }
            PeriodType.MONTH -> {
                val zdt = anchorDate.atStartOfDay(zoneId)
                val start = zdt.withDayOfMonth(1)
                val end = zdt.withDayOfMonth(zdt.toLocalDate().lengthOfMonth())
                "${start.format(formatterDay)} – ${end.format(formatterDay)}"
            }
        }

        binding.tvReportSubtitle.text = text
    }

    private fun calculatePeriodBounds(
        periodType: PeriodType,
        anchorDate: LocalDate,
        zoneId: ZoneId
    ): Pair<Long, Long> {
        val anchorZdt = anchorDate.atStartOfDay(zoneId)

        return when (periodType) {
            PeriodType.DAY -> {
                val start = anchorZdt.toInstant().toEpochMilli()
                val end = anchorZdt.plusDays(1).toInstant().toEpochMilli() - 1
                start to end
            }
            PeriodType.WEEK -> {
                val start = anchorZdt.minusDays(6).toInstant().toEpochMilli()
                val end = anchorZdt.plusDays(1).toInstant().toEpochMilli() - 1
                start to end
            }
            PeriodType.MONTH -> {
                val start = anchorZdt.withDayOfMonth(1).toInstant().toEpochMilli()
                val lastDay = anchorZdt.toLocalDate().lengthOfMonth()
                val end = anchorZdt.withDayOfMonth(lastDay).plusDays(1).toInstant().toEpochMilli() - 1
                start to end
            }
        }
    }

    private fun renderReport(report: PeriodReportData) {
        val summary = report.summary

        val totalMinutes = summary.totalFocusMs / (60 * 1000)
        val hours = totalMinutes / 60
        val mins = totalMinutes % 60
        binding.tvTotalFocusValue.text = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"

        binding.tvChecksPerHourValue.text = String.format(Locale.ENGLISH, "%.1f", summary.checksPerFocusHour)
        binding.tvCompletionRateValue.text = "${summary.goalCompletionRate.toInt()}%"

        val longestMins = summary.longestFocusMs / (60 * 1000)
        binding.tvLongestFocusValue.text = "${longestMins} min"

        binding.tvPhoneChecksValue.text = "${summary.totalCheckCount} phone checks"
        val phoneUseMins = summary.totalPhoneUseMs / (60 * 1000)
        binding.tvPhoneUsageValue.text = "${phoneUseMins}m phone usage"

        if (report.topAppUsages.isEmpty()) {
            binding.tvMostUsedAppsContent.text = "No non-allowed apps used."
        } else {
            val topAppsText = report.topAppUsages.take(3).joinToString(separator = "\n") { app ->
                val appMins = app.totalDurationMs / (60 * 1000)
                "• ${app.appLabel}: ${appMins}m"
            }
            binding.tvMostUsedAppsContent.text = topAppsText
        }

        when (report.periodType) {
            PeriodType.DAY -> {
                binding.cardChartContainer.visibility = View.GONE
                binding.cardDayTimeline.visibility = View.VISIBLE
                renderDayTimeline(report)
            }
            PeriodType.WEEK -> {
                binding.cardChartContainer.visibility = View.VISIBLE
                binding.cardDayTimeline.visibility = View.GONE
                binding.tvChartTitle.text = "Daily Focus Time"
                binding.tvChartSubtitle.text = "Focus minutes per day"
                FocusBarChartBinder.bindChart(binding.barChartFocusByDay, report.dailyStats)
            }
            PeriodType.MONTH -> {
                binding.cardChartContainer.visibility = View.VISIBLE
                binding.cardDayTimeline.visibility = View.GONE
                binding.tvChartTitle.text = "Weekly Focus Time"
                binding.tvChartSubtitle.text = "Focus minutes per week"
                FocusBarChartBinder.bindWeeklyChart(binding.barChartFocusByDay, report.weeklyStats)
            }
        }
    }

    private fun renderDayTimeline(report: PeriodReportData) {
        if (report.sessions.isEmpty()) {
            binding.tvDayTimelineContent.text = "No sessions recorded on this day."
            binding.tvEarlyStopReasonsSummary.visibility = View.GONE
            return
        }

        val sb = StringBuilder()
        report.sessions.forEachIndexed { index, session ->
            val startTime = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
                .format(Instant.ofEpochMilli(session.startAt).atZone(zoneId))

            val endTime = if (session.endAt != null) {
                DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
                    .format(Instant.ofEpochMilli(session.endAt).atZone(zoneId))
            } else "Ongoing"

            val focusMins = session.focusMs / (60 * 1000)
            val phoneMins = session.phoneUseMs / (60 * 1000)

            sb.append("${index + 1}. [${session.subject}] $startTime - $endTime\n")
            sb.append("   Focus: ${focusMins}m | Phone: ${phoneMins}m | Checks: ${session.checkCount} (${session.status})\n")
            if (!session.stopMemo.isNullOrBlank()) {
                sb.append("   Memo: \"${session.stopMemo}\"\n")
            }
            sb.append("\n")
        }

        binding.tvDayTimelineContent.text = sb.toString().trim()

        if (report.earlyStopReasons.isNotEmpty()) {
            val reasonsText = report.earlyStopReasons.joinToString { "${it.reasonCode} (${it.count})" }
            binding.tvEarlyStopReasonsSummary.text = "Early Stop reasons: $reasonsText"
            binding.tvEarlyStopReasonsSummary.visibility = View.VISIBLE
        } else {
            binding.tvEarlyStopReasonsSummary.visibility = View.GONE
        }
    }
}
