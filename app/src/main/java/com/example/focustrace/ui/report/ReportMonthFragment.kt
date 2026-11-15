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
import com.example.focustrace.databinding.FragmentReportMonthBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * Fragment displaying monthly focus report, weekly chart, phone checks, and top app usage.
 */
class ReportMonthFragment : Fragment() {

    private var _binding: FragmentReportMonthBinding? = null
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
        _binding = FragmentReportMonthBinding.inflate(inflater, container, false)
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
            selectedDate = selectedDate.minusMonths(1)
            updateReport()
        }

        binding.btnNextPeriod.setOnClickListener {
            selectedDate = selectedDate.plusMonths(1)
            updateReport()
        }
    }

    private fun updateReport() {
        reportJob?.cancel()

        val anchorDate = selectedDate
        binding.tvReportSubtitle.text = ReportPeriodUtils.formatPeriodSubtitle(PeriodType.MONTH, anchorDate, zoneId)

        reportJob = lifecycleScope.launch {
            val (periodStart, periodEnd) = ReportPeriodUtils.calculatePeriodBounds(PeriodType.MONTH, anchorDate, zoneId)
            try {
                val reportData = withContext(Dispatchers.IO) {
                    repository.getReport(PeriodType.MONTH, periodStart, periodEnd, zoneId)
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

        binding.tvChartTitle.text = "Weekly Focus Time"
        binding.tvChartSubtitle.text = "Focus minutes per week"
        FocusBarChartBinder.bindWeeklyChart(binding.barChartFocusByDay, report.weeklyStats)
    }
}
