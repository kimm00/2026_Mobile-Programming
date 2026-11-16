package com.example.focustrace.ui.report

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.focustrace.data.db.AppDatabase
import com.example.focustrace.data.model.PeriodReportData
import com.example.focustrace.data.model.PeriodType
import com.example.focustrace.data.repo.ReportRepository
import com.example.focustrace.data.repo.RoomReportRepository
import com.example.focustrace.databinding.FragmentReportWeekBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/**
 * Fragment displaying weekly focus report, daily chart, top app usage, and early stop reasons.
 */
class ReportWeekFragment : Fragment() {

    private var _binding: FragmentReportWeekBinding? = null
    private val binding get() = _binding!!

    private val zoneId: ZoneId = ZoneId.systemDefault()
    private var selectedDate: LocalDate = LocalDate.now(zoneId)
    private var reportJob: Job? = null

    private val repository: ReportRepository by lazy {
        RoomReportRepository(AppDatabase.get(requireContext().applicationContext))
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReportWeekBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupNavigation()
    }

    override fun onResume() {
        super.onResume()
        updateReport()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        reportJob?.cancel()
        _binding = null
    }

    private fun setupNavigation() {
        binding.btnPrevPeriod.setOnClickListener {
            selectedDate = selectedDate.minusWeeks(1)
            updateReport()
        }

        binding.btnNextPeriod.setOnClickListener {
            selectedDate = selectedDate.plusWeeks(1)
            updateReport()
        }
    }

    private fun updateReport() {
        reportJob?.cancel()

        val anchorDate = selectedDate
        binding.tvReportSubtitle.text = ReportPeriodUtils.formatPeriodSubtitle(PeriodType.WEEK, anchorDate, zoneId)

        reportJob = lifecycleScope.launch {
            val (periodStart, periodEnd) = ReportPeriodUtils.calculatePeriodBounds(PeriodType.WEEK, anchorDate, zoneId)
            try {
                val reportData = withContext(Dispatchers.IO) {
                    repository.getReport(PeriodType.WEEK, periodStart, periodEnd, zoneId)
                }
                _binding?.let { renderReport(reportData) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isAdded) {
                    Toast.makeText(requireContext(), "Failed to load report", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun renderReport(report: PeriodReportData) {
        val summary = report.summary

        // Summary Card Headline & Footer
        val sessionCount = report.sessions.size
        binding.tvSummaryHeadline.text = when {
            sessionCount == 0 -> "No focus sessions recorded this week."
            sessionCount == 1 -> "You stayed focused across 1 session this week."
            else -> "You stayed focused across $sessionCount sessions this week."
        }

        binding.tvTotalFocusValue.text = TimeFormatter.formatDuration(summary.totalFocusMs)
        binding.tvChecksPerHourValue.text = TimeFormatter.formatChecksPerHour(summary.checksPerFocusHour, summary.totalFocusMs)
        binding.tvCompletionRateValue.text = "${summary.goalCompletionRate.toInt()}%"
        binding.tvLongestFocusValue.text = TimeFormatter.formatDuration(summary.longestFocusMs)

        binding.tvSummaryFooter.text =
            "$sessionCount sessions · ${summary.completedSessionCount} targets completed · ${summary.totalCheckCount} checks"

        // Chart Card
        val totalMins = summary.totalFocusMs / (60 * 1000)
        binding.tvChartTitle.text = "Focus time by day"
        binding.tvChartSubtitle.text = "$totalMins minutes focused across the week"
        FocusBarChartBinder.bindChart(binding.barChartFocusByDay, report.dailyStats)

        // Render What Pulled You Away App List
        AppUsageViewHelper.bindAppUsages(
            binding.layoutAppUsagesContainer,
            report.topAppUsages,
            requireContext().packageManager
        )

        // Early Stop Reasons Card
        if (report.earlyStopReasons.isNotEmpty()) {
            binding.cardEarlyStops.visibility = View.VISIBLE
            val totalEarlyStops = report.earlyStopReasons.sumOf { it.count }
            binding.tvEarlyStopsSubtitle.text = "$totalEarlyStops early stops this week"
            binding.tvEarlyStopReasonsDetails.text = report.earlyStopReasons.joinToString(" · ") {
                "${it.reasonCode} ${it.count}"
            }
        } else {
            binding.cardEarlyStops.visibility = View.GONE
        }
    }
}
