package com.example.focustrace.ui.report

import android.graphics.Color
import com.example.focustrace.data.model.DailyFocusStat
import com.example.focustrace.data.model.HourlyPhoneUsageStat
import com.example.focustrace.data.model.WeeklyFocusStat
import com.example.focustrace.domain.ReportCalculator
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * UI Binder responsible for setting up and rendering MPAndroidChart BarChart for Focus Report.
 * Keeps chart rendering logic cleanly separated from domain aggregation logic.
 */
object FocusBarChartBinder {

    private const val GREEN_PRIMARY = "#4CAF50"
    private const val GREEN_DARK = "#2E7D32"
    private const val CORAL_PRIMARY = "#F6AE8E"
    private const val TEXT_DARK = "#45593A"

    /**
     * Binds calculated daily focus statistics to the MPAndroidChart BarChart (for Week view).
     */
    fun bindChart(chart: BarChart, dailyStats: List<DailyFocusStat>) {
        if (dailyStats.isEmpty()) {
            chart.clear()
            return
        }

        val entries = ArrayList<BarEntry>()
        val labels = ArrayList<String>()

        dailyStats.forEachIndexed { index, stat ->
            val minutes = stat.focusMs / (60 * 1000f)
            entries.add(BarEntry(index.toFloat(), minutes))

            val dayOfWeekStr = stat.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
            labels.add("${stat.date.monthValue}/${stat.date.dayOfMonth} $dayOfWeekStr")
        }

        renderBarChart(chart, entries, labels)
    }

    /**
     * Binds calculated weekly focus statistics to the MPAndroidChart BarChart (for Month view).
     */
    fun bindWeeklyChart(chart: BarChart, weeklyStats: List<WeeklyFocusStat>) {
        if (weeklyStats.isEmpty()) {
            chart.clear()
            return
        }

        val entries = ArrayList<BarEntry>()
        val labels = ArrayList<String>()

        weeklyStats.forEachIndexed { index, stat ->
            val minutes = stat.focusMs / (60 * 1000f)
            entries.add(BarEntry(index.toFloat(), minutes))
            labels.add(stat.weekLabel)
        }

        renderBarChart(chart, entries, labels)
    }

    /**
     * Binds checks per focus hour by day for Week view (Coral bars).
     */
    fun bindChecksPerHourChart(chart: BarChart, dailyStats: List<DailyFocusStat>) {
        if (dailyStats.isEmpty()) {
            chart.clear()
            return
        }

        val entries = ArrayList<BarEntry>()
        val labels = ArrayList<String>()

        dailyStats.forEachIndexed { index, stat ->
            val cph = if (stat.focusMs > 0L) {
                (stat.checkCount.toDouble() / stat.focusMs) * ReportCalculator.MILLIS_PER_HOUR
            } else {
                0.0
            }
            entries.add(BarEntry(index.toFloat(), cph.toFloat()))

            val dayOfWeekStr = stat.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
            labels.add(dayOfWeekStr)
        }

        renderCoralBarChart(chart, entries, labels) { value ->
            if (value > 0f) String.format(Locale.ENGLISH, "%.1f", value) else ""
        }
    }

    /**
     * Binds checks per focus hour by week for Month view (Coral bars).
     */
    fun bindWeeklyChecksPerHourChart(
        chart: BarChart,
        weeklyStats: List<WeeklyFocusStat>,
        dailyStats: List<DailyFocusStat>
    ) {
        if (weeklyStats.isEmpty()) {
            chart.clear()
            return
        }

        val entries = ArrayList<BarEntry>()
        val labels = ArrayList<String>()

        weeklyStats.forEachIndexed { index, week ->
            val daysInWeek = dailyStats.filter { !it.date.isBefore(week.startDate) && !it.date.isAfter(week.endDate) }
            val totalFocus = daysInWeek.sumOf { it.focusMs }
            val totalChecks = daysInWeek.sumOf { it.checkCount }

            val cph = if (totalFocus > 0L) {
                (totalChecks.toDouble() / totalFocus) * ReportCalculator.MILLIS_PER_HOUR
            } else {
                0.0
            }
            entries.add(BarEntry(index.toFloat(), cph.toFloat()))
            labels.add(week.weekLabel)
        }

        renderCoralBarChart(chart, entries, labels) { value ->
            if (value > 0f) String.format(Locale.ENGLISH, "%.1f", value) else ""
        }
    }

    /**
     * Binds phone usage grouped into 3-hour blocks (00-03..21-24) for Week & Month view.
     */
    fun bindTimeOfDayPhoneUsageChart(
        chart: BarChart,
        hourlyPhoneUsage: List<HourlyPhoneUsageStat>
    ) {
        if (hourlyPhoneUsage.isEmpty()) {
            chart.clear()
            return
        }

        val blockLabels = listOf("00–03", "03–06", "06–09", "09–12", "12–15", "15–18", "18–21", "21–24")
        val blockUsageMs = LongArray(8)

        hourlyPhoneUsage.forEach { stat ->
            val blockIndex = (stat.hourOfDay / 3).coerceIn(0, 7)
            blockUsageMs[blockIndex] += stat.usageMs
        }

        val entries = ArrayList<BarEntry>()
        blockUsageMs.forEachIndexed { index, ms ->
            val mins = ms / 60_000f
            entries.add(BarEntry(index.toFloat(), mins))
        }

        renderCoralBarChart(chart, entries, blockLabels) { value ->
            if (value > 0f) "${value.toInt()}" else "0"
        }
    }

    private fun renderBarChart(chart: BarChart, entries: List<BarEntry>, labels: List<String>) {
        val dataSet = BarDataSet(entries, "Focus Time").apply {
            color = Color.parseColor(GREEN_PRIMARY)
            valueTextColor = Color.parseColor(GREEN_DARK)
            valueTextSize = 10f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val ms = (value * 60_000f).toLong()
                    return if (ms > 0L) TimeFormatter.formatDuration(ms) else ""
                }
            }
        }

        val barData = BarData(dataSet).apply {
            barWidth = 0.5f
        }

        chart.apply {
            data = barData
            description.isEnabled = false
            legend.isEnabled = false
            setDrawGridBackground(false)
            setFitBars(true)

            // Configure X-Axis
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                valueFormatter = IndexAxisValueFormatter(labels)
                textColor = Color.parseColor(GREEN_DARK)
                textSize = 10f
            }

            // Configure Left Y-Axis
            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = Color.parseColor("#E0E0E0")
                axisMinimum = 0f
                textColor = Color.parseColor("#616161")
                textSize = 10f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val ms = (value * 60_000f).toLong()
                        return if (ms > 0L) TimeFormatter.formatDuration(ms) else "0s"
                    }
                }
            }

            // Disable Right Y-Axis
            axisRight.isEnabled = false

            animateY(500)
            invalidate()
        }
    }

    private fun renderCoralBarChart(
        chart: BarChart,
        entries: List<BarEntry>,
        labels: List<String>,
        valueFormatterFunc: (Float) -> String
    ) {
        val dataSet = BarDataSet(entries, "Metrics").apply {
            color = Color.parseColor(CORAL_PRIMARY)
            valueTextColor = Color.parseColor(TEXT_DARK)
            valueTextSize = 10f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return valueFormatterFunc(value)
                }
            }
        }

        val barData = BarData(dataSet).apply {
            barWidth = 0.5f
        }

        chart.apply {
            data = barData
            description.isEnabled = false
            legend.isEnabled = false
            setDrawGridBackground(false)
            setFitBars(true)

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                valueFormatter = IndexAxisValueFormatter(labels)
                textColor = Color.parseColor(TEXT_DARK)
                textSize = 10f
            }

            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = Color.parseColor("#E0E0E0")
                axisMinimum = 0f
                textColor = Color.parseColor("#616161")
                textSize = 10f
            }

            axisRight.isEnabled = false

            animateY(500)
            invalidate()
        }
    }
}
