package com.example.focustrace.ui.report

import android.graphics.Color
import com.example.focustrace.data.model.DailyFocusStat
import com.example.focustrace.data.model.WeeklyFocusStat
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
            // Convert focus duration to minutes as Float (0f for zero-focus days so they remain visible on X-axis)
            val minutes = stat.focusMs / (60 * 1000f)
            entries.add(BarEntry(index.toFloat(), minutes))

            // Format date label (e.g., "3/1 Sun" or "Sun")
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

    private fun renderBarChart(chart: BarChart, entries: List<BarEntry>, labels: List<String>) {
        val dataSet = BarDataSet(entries, "Focus Minutes").apply {
            color = Color.parseColor(GREEN_PRIMARY)
            valueTextColor = Color.parseColor(GREEN_DARK)
            valueTextSize = 11f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return if (value > 0f) "${value.toInt()}m" else "0"
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
            }

            // Disable Right Y-Axis
            axisRight.isEnabled = false

            animateY(500)
            invalidate()
        }
    }
}
