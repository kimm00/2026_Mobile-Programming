package com.example.focustrace.ui.report

import com.example.focustrace.data.model.PeriodType
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Utility functions for computing report date period bounds and formatted subtitles.
 */
object ReportPeriodUtils {

    /**
     * Calculates the start and end epoch milliseconds for the given period.
     */
    fun calculatePeriodBounds(
        periodType: PeriodType,
        anchorDate: LocalDate,
        zoneId: ZoneId
    ): Pair<Long, Long> {
        val anchorZdt = anchorDate.atStartOfDay(zoneId)

        return when (periodType) {
            PeriodType.DAY -> {
                val start = anchorZdt.toInstant().toEpochMilli()
                val end = anchorZdt.plusDays(1).toInstant().toEpochMilli() - 1
                start to end
            }
            PeriodType.WEEK -> {
                val start = anchorZdt.minusDays(6).toInstant().toEpochMilli()
                val end = anchorZdt.plusDays(1).toInstant().toEpochMilli() - 1
                start to end
            }
            PeriodType.MONTH -> {
                val start = anchorZdt.withDayOfMonth(1).toInstant().toEpochMilli()
                val lastDay = anchorZdt.toLocalDate().lengthOfMonth()
                val end = anchorZdt.withDayOfMonth(lastDay).plusDays(1).toInstant().toEpochMilli() - 1
                start to end
            }
        }
    }

    /**
     * Formats period subtitle text (e.g. "Nov 8, 2026" or "Nov 2, 2026 – Nov 8, 2026").
     */
    fun formatPeriodSubtitle(
        periodType: PeriodType,
        anchorDate: LocalDate,
        zoneId: ZoneId
    ): String {
        val formatterDay = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)

        return when (periodType) {
            PeriodType.DAY -> anchorDate.format(formatterDay)
            PeriodType.WEEK -> {
                val start = anchorDate.minusDays(6)
                "${start.format(formatterDay)} – ${anchorDate.format(formatterDay)}"
            }
            PeriodType.MONTH -> {
                val zdt = anchorDate.atStartOfDay(zoneId)
                val start = zdt.withDayOfMonth(1)
                val end = zdt.withDayOfMonth(zdt.toLocalDate().lengthOfMonth())
                "${start.format(formatterDay)} – ${end.format(formatterDay)}"
            }
        }
    }
}
