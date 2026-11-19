package com.example.focustrace.ui.report

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.focustrace.R
import com.example.focustrace.data.model.PeriodReportData
import com.example.focustrace.data.model.PeriodType
import com.example.focustrace.databinding.FragmentReportDayBinding
import com.example.focustrace.databinding.ItemCalendarSessionBinding
import com.example.focustrace.ui.FinishActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Fragment displaying daily focus report, top summary, sessions card, and app usage matching Figma specs.
 */
class ReportDayFragment : Fragment() {

    private var _binding: FragmentReportDayBinding? = null
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
        _binding = FragmentReportDayBinding.inflate(inflater, container, false)
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
            viewModel.navigatePeriod(PeriodType.DAY, false)
        }

        binding.btnNextPeriod.setOnClickListener {
            viewModel.navigatePeriod(PeriodType.DAY, true)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { state ->
                    binding.tvReportSubtitle.text = ReportPeriodUtils.formatPeriodSubtitle(PeriodType.DAY, state.dayAnchorDate, zoneId)
                    state.dayReport?.let { renderReport(it) }
                }
            }
        }
    }

    private fun renderReport(report: PeriodReportData) {
        val summary = report.summary

        // Summary Card Headline & Footer
        val sessionCount = report.sessions.size
        binding.tvSummaryHeadline.text = when {
            sessionCount == 0 -> "No focus sessions recorded today."
            sessionCount == 1 -> "You stayed focused across 1 session today."
            else -> "You stayed focused across $sessionCount sessions today."
        }

        binding.tvTotalFocusValue.text = TimeFormatter.formatDuration(summary.totalFocusMs)
        binding.tvChecksPerHourValue.text = TimeFormatter.formatChecksPerHour(summary.checksPerFocusHour, summary.totalFocusMs)
        binding.tvCompletionRateValue.text = "${summary.goalCompletionRate.toInt()}%"
        binding.tvLongestFocusValue.text = TimeFormatter.formatDuration(summary.longestFocusMs)

        binding.tvSummaryFooter.text =
            "$sessionCount sessions · ${summary.completedSessionCount} target completed · ${summary.totalCheckCount} checks"

        // Render Sessions Card
        renderSessionsCard(report)

        // Render What Pulled You Away App List
        AppUsageViewHelper.bindAppUsages(
            binding.layoutAppUsagesContainer,
            report.topAppUsages,
            requireContext().packageManager
        )
    }

    private fun renderSessionsCard(report: PeriodReportData) {
        val container = binding.layoutDaySessionsContainer
        container.removeAllViews()

        if (report.sessions.isEmpty()) {
            val emptyTv = TextView(requireContext()).apply {
                text = "No focus sessions recorded today."
                textSize = 13f
                setTextColor(Color.parseColor("#777777"))
            }
            container.addView(emptyTv)
            return
        }

        val inflater = LayoutInflater.from(requireContext())
        val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

        report.sessions.forEachIndexed { index, session ->
            val itemBinding = ItemCalendarSessionBinding.inflate(inflater, container, false)
            val startStr = timeFormatter.format(Instant.ofEpochMilli(session.startAt).atZone(zoneId))
            val endStr = if (session.endAt != null) {
                timeFormatter.format(Instant.ofEpochMilli(session.endAt).atZone(zoneId))
            } else "Ongoing"

            itemBinding.tvSessionSubjectAndTime.text = "${session.subject} · $startStr–$endStr"
            itemBinding.tvSessionFocusTime.text = "${TimeFormatter.formatDuration(session.focusMs)} focus"
            itemBinding.tvSessionChecksBadge.text = "${session.checkCount} checks"

            val longestStr = TimeFormatter.formatDuration(session.longestFocusMs)
            val phoneStr = TimeFormatter.formatDuration(session.phoneUseMs)
            itemBinding.tvSessionSubDetails.text = "$longestStr longest · $phoneStr phone use"

            when (session.status) {
                "COMPLETED" -> {
                    itemBinding.tvSessionStatusTag.text = "Completed · ${session.goalMinutes}-minute target"
                    itemBinding.tvSessionStatusTag.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_badge_completed)
                }
                "EARLY_STOP" -> {
                    val reasonStr = session.stopReason?.let { " ($it)" } ?: ""
                    itemBinding.tvSessionStatusTag.text = "Early stop$reasonStr · ${session.goalMinutes}-minute target"
                    itemBinding.tvSessionStatusTag.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_badge_early_stop)
                }
                else -> {
                    itemBinding.tvSessionStatusTag.text = "${session.status} · ${session.goalMinutes}-minute target"
                    itemBinding.tvSessionStatusTag.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_badge_early_stop)
                }
            }

            itemBinding.sessionItemDivider.visibility = if (index == report.sessions.size - 1) View.GONE else View.VISIBLE

            itemBinding.root.setOnClickListener {
                val intent = Intent(requireContext(), FinishActivity::class.java).apply {
                    putExtra(FinishActivity.EXTRA_SESSION_ID, session.id)
                }
                startActivity(intent)
            }

            container.addView(itemBinding.root)
        }
    }
}
