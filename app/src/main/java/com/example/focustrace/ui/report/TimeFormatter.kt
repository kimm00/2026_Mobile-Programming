package com.example.focustrace.ui.report

import java.util.Locale

/**
 * Common formatter for duration and metrics across FocusTrace reports.
 */
object TimeFormatter {

    /**
     * Formats a duration in milliseconds to standard concise format:
     * 38s, 1m, 1m 20s, 1h 2m 3s, 0s
     */
    fun formatDuration(ms: Long): String {
        val totalSecs = (ms.coerceAtLeast(0L)) / 1000L
        if (totalSecs == 0L) return "0s"

        val hours = totalSecs / 3600L
        val mins = (totalSecs % 3600L) / 60L
        val secs = totalSecs % 60L

        return buildString {
            if (hours > 0L) {
                append("${hours}h")
            }
            if (mins > 0L) {
                if (isNotEmpty()) append(" ")
                append("${mins}m")
            }
            if (secs > 0L) {
                if (isNotEmpty()) append(" ")
                append("${secs}s")
            }
        }
    }

    /**
     * Formats phone checks per focus hour with 1 decimal place.
     * Displays "—" when total focus time is 0.
     */
    fun formatChecksPerHour(checksPerFocusHour: Double, totalFocusMs: Long): String {
        if (totalFocusMs <= 0L) return "—"
        return String.format(Locale.ENGLISH, "%.1f", checksPerFocusHour)
    }
}
