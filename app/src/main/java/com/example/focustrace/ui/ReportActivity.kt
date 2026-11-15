package com.example.focustrace.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.focustrace.MainActivity
import com.example.focustrace.data.db.AppDatabase
import com.example.focustrace.data.model.PeriodType
import com.example.focustrace.data.repo.ReportRepository
import com.example.focustrace.data.repo.RoomReportRepository
import com.example.focustrace.databinding.ActivityReportBinding
import com.example.focustrace.ui.report.ReportPagerAdapter
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupAppBarAndNavigation()
        setupViewModeToggle()
        setupPeriodTabs()
        setupCalendarView()
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
        val adapter = ReportPagerAdapter(this)
        binding.viewPagerReport.adapter = adapter

        TabLayoutMediator(binding.tabLayoutPeriod, binding.viewPagerReport) { tab, position ->
            tab.text = when (position) {
                0 -> "Day"
                1 -> "Week"
                2 -> "Month"
                else -> ""
            }
        }.attach()

        // Default period tab: Week (index 1)
        binding.viewPagerReport.setCurrentItem(1, false)
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
}
