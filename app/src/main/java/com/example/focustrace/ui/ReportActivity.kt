package com.example.focustrace.ui

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.focustrace.R
import com.example.focustrace.data.model.PeriodType
import com.example.focustrace.domain.ReportCalculator
import com.example.focustrace.sample.SampleData
import com.example.focustrace.ui.report.FocusBarChartBinder
import com.github.mikephil.charting.charts.BarChart
import java.util.Locale

class ReportActivity : AppCompatActivity() {

    private val calculator = ReportCalculator(
        zoneId = SampleData.TEST_ZONE_ID
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)

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

        val focusMinutes = summary.totalFocusMs / 60000
        val hours = focusMinutes / 60
        val minutes = focusMinutes % 60

        findViewById<TextView>(R.id.tvTotalFocusValue).text =
            if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"

        findViewById<TextView>(R.id.tvChecksPerHourValue).text =
            String.format(Locale.US, "%.2f / hr", summary.checksPerFocusHour)

        findViewById<TextView>(R.id.tvCompletionRateValue).text =
            String.format(Locale.US, "%.1f%%", summary.goalCompletionRate)

        findViewById<TextView>(R.id.tvLongestFocusValue).text =
            "${summary.longestFocusMs / 60000} min"

        val reasons = report.earlyStopReasons

        findViewById<TextView>(R.id.tvEarlyStopReasonsSummary).text =
            if (reasons.isNotEmpty()) {
                reasons.joinToString(", ") { "${it.reasonCode}: ${it.count}" }
            } else {
                "None"
            }

        val barChart = findViewById<BarChart>(R.id.barChartFocusByDay)
        FocusBarChartBinder.bindChart(barChart, report.dailyStats)
    }
}