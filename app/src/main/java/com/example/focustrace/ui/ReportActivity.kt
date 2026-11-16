package com.example.focustrace.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Menu
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.focustrace.MainActivity
import com.example.focustrace.R
import com.example.focustrace.data.db.AppDatabase
import com.example.focustrace.data.model.PeriodType
import com.example.focustrace.data.model.ReportSession
import com.example.focustrace.data.repo.ReportRepository
import com.example.focustrace.data.repo.RoomReportRepository
import com.example.focustrace.databinding.ActivityReportBinding
import com.example.focustrace.ui.report.CalendarSessionAdapter
import com.example.focustrace.ui.report.ReportPagerAdapter
import com.example.focustrace.ui.report.TimeFormatter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
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

    private var selectedCalendarDate: LocalDate = LocalDate.now(zoneId)
    private lateinit var calendarAdapter: CalendarSessionAdapter

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
        val darkGreen = ContextCompat.getColor(this, R.color.focus_text_dark)
        val white = Color.WHITE

        fun updateViewMode(isOverview: Boolean) {
            if (isOverview) {
                binding.btnToggleOverview.setBackgroundResource(R.drawable.bg_pill_selected)
                binding.btnToggleOverview.setTextColor(white)
                binding.btnToggleOverview.setTypeface(null, Typeface.BOLD)

                binding.btnToggleCalendar.background = null
                binding.btnToggleCalendar.setTextColor(darkGreen)
                binding.btnToggleCalendar.setTypeface(null, Typeface.NORMAL)

                binding.layoutOverviewContainer.visibility = View.VISIBLE
                binding.layoutCalendarContainer.visibility = View.GONE
            } else {
                binding.btnToggleCalendar.setBackgroundResource(R.drawable.bg_pill_selected)
                binding.btnToggleCalendar.setTextColor(white)
                binding.btnToggleCalendar.setTypeface(null, Typeface.BOLD)

                binding.btnToggleOverview.background = null
                binding.btnToggleOverview.setTextColor(darkGreen)
                binding.btnToggleOverview.setTypeface(null, Typeface.NORMAL)

                binding.layoutOverviewContainer.visibility = View.GONE
                binding.layoutCalendarContainer.visibility = View.VISIBLE
                loadCalendarDaySessions(selectedCalendarDate)
                loadMonthSessionDots(YearMonth.from(selectedCalendarDate))
            }
        }

        binding.btnToggleOverview.setOnClickListener { updateViewMode(true) }
        binding.btnToggleCalendar.setOnClickListener { updateViewMode(false) }

        // Default: Overview selected
        updateViewMode(true)
    }

    private fun setupPeriodTabs() {
        val adapter = ReportPagerAdapter(this)
        binding.viewPagerReport.adapter = adapter

        val darkGreen = ContextCompat.getColor(this, R.color.focus_text_dark)

        fun updatePeriodTab(position: Int) {
            val tabs = listOf(binding.btnTabDay, binding.btnTabWeek, binding.btnTabMonth)
            tabs.forEachIndexed { idx, tab ->
                if (idx == position) {
                    tab.setBackgroundResource(R.drawable.bg_period_tab_selected)
                    tab.setTextColor(darkGreen)
                    tab.setTypeface(null, Typeface.BOLD)
                } else {
                    tab.background = null
                    tab.setTextColor(darkGreen)
                    tab.setTypeface(null, Typeface.NORMAL)
                }
            }
        }

        binding.btnTabDay.setOnClickListener {
            binding.viewPagerReport.setCurrentItem(0, false)
            updatePeriodTab(0)
        }
        binding.btnTabWeek.setOnClickListener {
            binding.viewPagerReport.setCurrentItem(1, false)
            updatePeriodTab(1)
        }
        binding.btnTabMonth.setOnClickListener {
            binding.viewPagerReport.setCurrentItem(2, false)
            updatePeriodTab(2)
        }

        binding.viewPagerReport.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updatePeriodTab(position)
            }
        })

        // Default period tab: Week (index 1)
        binding.viewPagerReport.setCurrentItem(1, false)
        updatePeriodTab(1)
    }

    private fun setupCalendarView() {
        calendarAdapter = CalendarSessionAdapter(
            zoneId = zoneId,
            onSessionClick = { session ->
                val intent = Intent(this, FinishActivity::class.java).apply {
                    putExtra(FinishActivity.EXTRA_SESSION_ID, session.id)
                }
                startActivity(intent)
            },
            onSessionLongClick = { session, anchorView ->
                showDeletePopupMenu(session, anchorView)
                true
            }
        )

        binding.rvCalendarSessions.layoutManager = LinearLayoutManager(this)
        binding.rvCalendarSessions.adapter = calendarAdapter

        binding.customCalendarView.onDateSelectedListener = { date ->
            selectedCalendarDate = date
            loadCalendarDaySessions(selectedCalendarDate)
        }

        binding.customCalendarView.onMonthChangedListener = { yearMonth ->
            loadMonthSessionDots(yearMonth)
        }

        selectedCalendarDate = LocalDate.now(zoneId)
        binding.customCalendarView.setSelectedDate(selectedCalendarDate)

        loadCalendarDaySessions(selectedCalendarDate)
        loadMonthSessionDots(YearMonth.from(selectedCalendarDate))
    }

    private fun showDeletePopupMenu(session: ReportSession, anchorView: View) {
        val popup = PopupMenu(this, anchorView)
        popup.menu.add(Menu.NONE, 1, Menu.NONE, "Delete")
        popup.setOnMenuItemClickListener { menuItem ->
            if (menuItem.itemId == 1) {
                confirmAndDeleteSession(session)
                true
            } else {
                false
            }
        }
        popup.show()
    }

    private fun confirmAndDeleteSession(session: ReportSession) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Session")
            .setMessage("Are you sure you want to delete session '${session.subject}'?")
            .setPositiveButton("Delete") { _, _ ->
                performSessionDeletion(session.id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performSessionDeletion(sessionId: Long) {
        lifecycleScope.launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    repository.deleteSession(sessionId)
                }

                if (success) {
                    Toast.makeText(this@ReportActivity, "Session deleted", Toast.LENGTH_SHORT).show()
                    loadCalendarDaySessions(selectedCalendarDate)
                    loadMonthSessionDots(YearMonth.from(selectedCalendarDate))
                    refreshOverviewReports()
                } else {
                    Toast.makeText(this@ReportActivity, "Failed to delete session", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ReportActivity, "Error deleting session: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun refreshOverviewReports() {
        val currentItem = binding.viewPagerReport.currentItem
        val adapter = ReportPagerAdapter(this)
        binding.viewPagerReport.adapter = adapter
        binding.viewPagerReport.setCurrentItem(currentItem, false)
    }

    private fun loadMonthSessionDots(yearMonth: YearMonth) {
        lifecycleScope.launch {
            val startOfMonth = yearMonth.atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            val endOfMonth = yearMonth.atEndOfMonth().plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()

            val report = withContext(Dispatchers.IO) {
                repository.getReport(PeriodType.MONTH, startOfMonth, endOfMonth, zoneId)
            }

            val sessionDates = report.sessions.map {
                Instant.ofEpochMilli(it.startAt).atZone(zoneId).toLocalDate()
            }.toSet()

            binding.customCalendarView.setDatesWithSessions(sessionDates)
        }
    }

    private fun loadCalendarDaySessions(date: LocalDate) {
        lifecycleScope.launch {
            val dateStr = date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH))
            binding.tvSelectedDayTitle.text = dateStr

            val startOfDay = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val endOfDay = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()

            val report = withContext(Dispatchers.IO) {
                repository.getReport(PeriodType.DAY, startOfDay, endOfDay, zoneId)
            }

            if (report.sessions.isEmpty()) {
                binding.layoutSelectedDayMetrics.visibility = View.GONE
                binding.tvCalendarEmptyState.visibility = View.VISIBLE
                binding.rvCalendarSessions.visibility = View.GONE
                calendarAdapter.submitList(emptyList())
            } else {
                binding.tvSelectedDayTotalFocus.text = TimeFormatter.formatDuration(report.summary.totalFocusMs)
                binding.tvSelectedDaySessionCount.text = report.sessions.size.toString()
                binding.tvSelectedDayPhoneChecks.text = report.summary.totalCheckCount.toString()

                binding.layoutSelectedDayMetrics.visibility = View.VISIBLE
                binding.tvCalendarEmptyState.visibility = View.GONE
                binding.rvCalendarSessions.visibility = View.VISIBLE
                calendarAdapter.submitList(report.sessions)
            }
        }
    }
}
