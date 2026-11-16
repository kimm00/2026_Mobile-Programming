package com.example.focustrace.ui.report

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.example.focustrace.R
import com.example.focustrace.databinding.ItemCalendarDayCellBinding
import com.example.focustrace.databinding.ViewCustomCalendarBinding
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Custom Calendar view matching Figma specs:
 * - Today's date is solid dark green (#88C26C) circle with white text.
 * - Selected date is green outline circle (#88C26C).
 * - Days with sessions have a small green dot below the date number.
 */
class CustomCalendarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding = ViewCustomCalendarBinding.inflate(LayoutInflater.from(context), this, true)

    private val zoneId: ZoneId = ZoneId.systemDefault()
    private val todayDate: LocalDate = LocalDate.now(zoneId)

    private var displayedYearMonth: YearMonth = YearMonth.now(zoneId)
    private var selectedDate: LocalDate = LocalDate.now(zoneId)
    private var datesWithSessions: Set<LocalDate> = emptySet()

    var onDateSelectedListener: ((LocalDate) -> Unit)? = null
    var onMonthChangedListener: ((YearMonth) -> Unit)? = null

    init {
        orientation = VERTICAL

        binding.btnPrevMonth.setOnClickListener {
            displayedYearMonth = displayedYearMonth.minusMonths(1)
            render()
            onMonthChangedListener?.invoke(displayedYearMonth)
        }

        binding.btnNextMonth.setOnClickListener {
            displayedYearMonth = displayedYearMonth.plusMonths(1)
            render()
            onMonthChangedListener?.invoke(displayedYearMonth)
        }

        render()
    }

    fun setSelectedDate(date: LocalDate) {
        selectedDate = date
        if (YearMonth.from(date) != displayedYearMonth) {
            displayedYearMonth = YearMonth.from(date)
            onMonthChangedListener?.invoke(displayedYearMonth)
        }
        render()
    }

    fun setDatesWithSessions(dates: Set<LocalDate>) {
        datesWithSessions = dates
        render()
    }

    fun getSelectedDate(): LocalDate = selectedDate

    fun getDisplayedYearMonth(): YearMonth = displayedYearMonth

    fun render() {
        val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
        binding.tvMonthTitle.text = displayedYearMonth.format(monthFormatter)

        val grid = binding.layoutMonthGrid
        grid.removeAllViews()

        val firstDayOfMonth = displayedYearMonth.atDay(1)
        val daysInMonth = displayedYearMonth.lengthOfMonth()

        // Sunday = 0, Monday = 1 ... Saturday = 6
        val firstDayOfWeekIndex = when (firstDayOfMonth.dayOfWeek) {
            DayOfWeek.SUNDAY -> 0
            DayOfWeek.MONDAY -> 1
            DayOfWeek.TUESDAY -> 2
            DayOfWeek.WEDNESDAY -> 3
            DayOfWeek.THURSDAY -> 4
            DayOfWeek.FRIDAY -> 5
            DayOfWeek.SATURDAY -> 6
        }

        val inflater = LayoutInflater.from(context)
        var currentDay = 1
        val totalCells = firstDayOfWeekIndex + daysInMonth
        val rows = (totalCells + 6) / 7

        for (row in 0 until rows) {
            val rowLayout = LinearLayout(context).apply {
                orientation = HORIZONTAL
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            }

            for (col in 0 until 7) {
                val cellIndex = row * 7 + col
                val cellBinding = ItemCalendarDayCellBinding.inflate(inflater, rowLayout, false)
                cellBinding.root.layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)

                if (cellIndex < firstDayOfWeekIndex || currentDay > daysInMonth) {
                    cellBinding.tvDayNumber.text = ""
                    cellBinding.tvDayNumber.background = null
                    cellBinding.vSessionDot.visibility = INVISIBLE
                    cellBinding.root.isClickable = false
                } else {
                    val date = displayedYearMonth.atDay(currentDay)
                    cellBinding.tvDayNumber.text = currentDay.toString()

                    when {
                        date == todayDate -> {
                            cellBinding.tvDayNumber.background = ContextCompat.getDrawable(context, R.drawable.bg_calendar_today)
                            cellBinding.tvDayNumber.setTextColor(Color.WHITE)
                            cellBinding.tvDayNumber.setTypeface(null, Typeface.BOLD)
                        }
                        date == selectedDate -> {
                            cellBinding.tvDayNumber.background = ContextCompat.getDrawable(context, R.drawable.bg_calendar_selected)
                            cellBinding.tvDayNumber.setTextColor(Color.parseColor("#45593A"))
                            cellBinding.tvDayNumber.setTypeface(null, Typeface.BOLD)
                        }
                        else -> {
                            cellBinding.tvDayNumber.background = null
                            cellBinding.tvDayNumber.setTextColor(Color.parseColor("#45593A"))
                            cellBinding.tvDayNumber.setTypeface(null, Typeface.NORMAL)
                        }
                    }

                    if (datesWithSessions.contains(date)) {
                        cellBinding.vSessionDot.visibility = VISIBLE
                    } else {
                        cellBinding.vSessionDot.visibility = INVISIBLE
                    }

                    cellBinding.root.setOnClickListener {
                        selectedDate = date
                        render()
                        onDateSelectedListener?.invoke(date)
                    }

                    currentDay++
                }

                rowLayout.addView(cellBinding.root)
            }

            grid.addView(rowLayout)
        }
    }
}
