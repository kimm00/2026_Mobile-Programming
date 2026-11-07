package com.example.focustrace.ui.report

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.focustrace.R
import com.example.focustrace.data.model.PeriodType
import com.example.focustrace.domain.ReportCalculator
import com.example.focustrace.sample.SampleData
import com.github.mikephil.charting.charts.BarChart
import java.util.Locale

/**
 * Standalone activity for testing F-05 Focus Report metrics calculation and MPAndroidChart rendering.
 */
class ReportTestActivity : AppCompatActivity() {

    private val calculator = ReportCalculator(zoneId = SampleData.TEST_ZONE_ID)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report_test)

        // Calculate Week 1 report metrics (2026-03-01 to 2026-03-08)
        val weekStart = SampleData.getEpochMs(0, 0, 0)
        val weekEnd = SampleData.getEpochMs(7, 0, 0)

        val report = calculator.calculateReport(
            periodType = PeriodType.WEEK,
            periodStart = weekStart,
            periodEnd = weekEnd,
            sessions = SampleData.SESSIONS,
            phoneEvents = SampleData.PHONE_EVENTS,
            appUsages = SampleData.APP_USAGES
        )

        val summary = report.summary

        // Bind Summary Metrics
        val tvTotalFocusValue = findViewById<TextView>(R.id.tvTotalFocusValue)
        val tvChecksPerHourValue = findViewById<TextView>(R.id.tvChecksPerHourValue)
        val tvCompletionRateValue = findViewById<TextView>(R.id.tvCompletionRateValue)
        val tvLongestFocusValue = findViewById<TextView>(R.id.tvLongestFocusValue)
        val tvEarlyStopReasonsSummary = findViewById<TextView>(R.id.tvEarlyStopReasonsSummary)
        val barChart = findViewById<BarChart>(R.id.barChartFocusByDay)

        val focusMinutes = summary.totalFocusMs / (60 * 1000)
        val hours = focusMinutes / 60
        val mins = focusMinutes % 60
        tvTotalFocusValue.text = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"

        tvChecksPerHourValue.text = String.format(Locale.US, "%.2f / hr", summary.checksPerFocusHour)
        tvCompletionRateValue.text = String.format(Locale.US, "%.1f%%", summary.goalCompletionRate)

        val longestMins = summary.longestFocusMs / (60 * 1000)
        tvLongestFocusValue.text = "${longestMins} min"

        // Format Early Stop Reasons
        if (report.earlyStopReasons.isNotEmpty()) {
            val reasonsStr = report.earlyStopReasons.joinToString(", ") { "${it.reasonCode}: ${it.count}" }
            tvEarlyStopReasonsSummary.text = reasonsStr
        } else {
            tvEarlyStopReasonsSummary.text = "None"
        }

        // Bind MPAndroidChart BarChart for Focus time by day
        FocusBarChartBinder.bindChart(barChart, report.dailyStats)
    }
}
