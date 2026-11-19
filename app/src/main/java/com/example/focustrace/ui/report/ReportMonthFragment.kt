package com.example.focustrace.ui.report

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.focustrace.data.model.FocusSummary
import com.example.focustrace.data.model.PeriodReportData
import com.example.focustrace.data.model.PeriodType
import com.example.focustrace.databinding.FragmentReportMonthBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.Locale

/**
 * Fragment displaying monthly focus report, weekly chart, checks per hour chart, time of day phone usage chart, top app usage, and early stop reasons.
 */
class ReportMonthFragment : Fragment() {

    private var _binding: FragmentReportMonthBinding? = null
    private val binding get() = _binding!!

    private val zoneId: ZoneId = ZoneId.systemDefault()
    private val viewModel: ReportViewModel by lazy {
        ViewModelProvider(requireActivity())[ReportViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReportMonthBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupNavigation()
        observeViewModel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupNavigation() {
        binding.btnPrevPeriod.setOnClickListener {
            viewModel.navigatePeriod(PeriodType.MONTH, false)
        }

        binding.btnNextPeriod.setOnClickListener {
            viewModel.navigatePeriod(PeriodType.MONTH, true)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { state ->
                    binding.tvReportSubtitle.text = ReportPeriodUtils.formatPeriodSubtitle(PeriodType.MONTH, state.monthAnchorDate, zoneId)
                    state.monthReport?.let { renderReport(it) }
                }
            }
        }
    }

    private fun renderReport(report: PeriodReportData) {
        val summary = report.summary
        val prev = report.previousSummary

        // Summary Card Headline & Footer
        val sessionCount = report.sessions.size
        binding.tvSummaryHeadline.text = when {
            sessionCount == 0 -> "No focus sessions recorded this month."
            sessionCount == 1 -> "You stayed focused across 1 session this month."
            else -> "You stayed focused across $sessionCount sessions this month."
        }

        binding.tvTotalFocusValue.text = TimeFormatter.formatDuration(summary.totalFocusMs)
        binding.tvChecksPerHourValue.text = TimeFormatter.formatChecksPerHour(summary.checksPerFocusHour, summary.totalFocusMs)
        binding.tvCompletionRateValue.text = "${summary.goalCompletionRate.toInt()}%"
        binding.tvLongestFocusValue.text = TimeFormatter.formatDuration(summary.longestFocusMs)

        binding.tvSummaryFooter.text =
            "$sessionCount sessions · ${summary.completedSessionCount} targets completed · ${summary.totalCheckCount} checks"

        // Render Previous Period Comparison Lines
        renderComparisonLines(summary, prev, "last month")

        // Chart 1: Focus time by week
        val totalMins = summary.totalFocusMs / (60 * 1000)
        binding.tvChartTitle.text = "Focus time by week"
        binding.tvChartSubtitle.text = "$totalMins minutes focused across the month"
        FocusBarChartBinder.bindWeeklyChart(binding.barChartFocusByDay, report.weeklyStats)

        // Chart 2: Checks per focus hour
        binding.tvChecksPerHourChartSubtitle.text =
            "${TimeFormatter.formatChecksPerHour(summary.checksPerFocusHour, summary.totalFocusMs)} checks per focus hour this month"
        FocusBarChartBinder.bindWeeklyChecksPerHourChart(binding.barChartChecksPerHour, report.weeklyStats, report.dailyStats)

        // Chart 3: Phone usage by time of day
        val totalPhoneUseMins = report.hourlyPhoneUsage.sumOf { it.usageMs } / (60 * 1000)
        binding.tvTimeOfDayChartSubtitle.text = "$totalPhoneUseMins min of phone use during study sessions"
        FocusBarChartBinder.bindTimeOfDayPhoneUsageChart(binding.barChartTimeOfDay, report.hourlyPhoneUsage)

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
            binding.tvEarlyStopsSubtitle.text = "$totalEarlyStops early stops this month"
            binding.tvEarlyStopReasonsDetails.text = report.earlyStopReasons.joinToString(" · ") {
                "${it.reasonCode} ${it.count}"
            }
        } else {
            binding.cardEarlyStops.visibility = View.GONE
        }
    }

    private fun renderComparisonLines(
        curr: FocusSummary,
        prev: FocusSummary?,
        periodName: String
    ) {
        if (prev == null) {
            binding.tvTotalFocusComparison.visibility = View.GONE
            binding.tvChecksPerHourComparison.visibility = View.GONE
            binding.tvCompletionRateComparison.visibility = View.GONE
            binding.tvLongestFocusComparison.visibility = View.GONE
            return
        }

        val redColor = Color.parseColor("#E57373")
        val blueColor = Color.parseColor("#64B5F6")

        // Total focus time comparison
        val diffFocusMs = curr.totalFocusMs - prev.totalFocusMs
        binding.tvTotalFocusComparison.apply {
            visibility = View.VISIBLE
            if (diffFocusMs >= 0) {
                text = "▲ ${TimeFormatter.formatDuration(diffFocusMs)} vs $periodName"
                setTextColor(redColor)
            } else {
                text = "▼ ${TimeFormatter.formatDuration(-diffFocusMs)} vs $periodName"
                setTextColor(blueColor)
            }
        }

        // Checks per hour comparison
        val diffCph = curr.checksPerFocusHour - prev.checksPerFocusHour
        binding.tvChecksPerHourComparison.apply {
            visibility = View.VISIBLE
            if (diffCph >= 0) {
                text = "▲ ${String.format(Locale.ENGLISH, "%.1f", diffCph)} vs $periodName"
                setTextColor(blueColor)
            } else {
                text = "▼ ${String.format(Locale.ENGLISH, "%.1f", -diffCph)} vs $periodName"
                setTextColor(redColor)
            }
        }

        // Completion rate comparison
        val diffRate = curr.goalCompletionRate - prev.goalCompletionRate
        binding.tvCompletionRateComparison.apply {
            visibility = View.VISIBLE
            if (diffRate >= 0) {
                text = "▲ ${diffRate.toInt()}% vs $periodName"
                setTextColor(redColor)
            } else {
                text = "▼ ${(-diffRate).toInt()}% vs $periodName"
                setTextColor(blueColor)
            }
        }

        // Longest focus comparison
        val diffLongestMs = curr.longestFocusMs - prev.longestFocusMs
        binding.tvLongestFocusComparison.apply {
            visibility = View.VISIBLE
            if (diffLongestMs >= 0) {
                text = "▲ ${TimeFormatter.formatDuration(diffLongestMs)} vs $periodName"
                setTextColor(redColor)
            } else {
                text = "▼ ${TimeFormatter.formatDuration(-diffLongestMs)} vs $periodName"
                setTextColor(blueColor)
            }
        }
    }
}
