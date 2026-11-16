package com.example.focustrace.ui.report

import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.focustrace.data.model.AppUsageStat

/**
 * UI helper to render app usages list (distractions & allowed apps) dynamically.
 */
object AppUsageViewHelper {

    fun bindAppUsages(
        container: LinearLayout,
        appUsages: List<AppUsageStat>,
        packageManager: PackageManager
    ) {
        container.removeAllViews()

        if (appUsages.isEmpty()) {
            val emptyView = TextView(container.context).apply {
                text = "No non-allowed apps used."
                textSize = 13f
                setTextColor(Color.parseColor("#777777"))
            }
            container.addView(emptyView)
            return
        }

        val density = container.resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        val textColor = Color.parseColor("#45593A")

        appUsages.forEach { stat ->
            val row = LinearLayout(container.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setPadding(0, dp(8), 0, dp(8))
            }

            val iconView = ImageView(container.context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply {
                    marginEnd = dp(12)
                }
                scaleType = ImageView.ScaleType.FIT_CENTER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }

            val appIcon = try {
                packageManager.getApplicationIcon(stat.packageName)
            } catch (e: Exception) {
                packageManager.defaultActivityIcon
            }
            iconView.setImageDrawable(appIcon)

            val textColumn = LinearLayout(container.context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            val nameView = TextView(container.context).apply {
                text = stat.appLabel.ifBlank { stat.packageName }
                textSize = 15f
                setTextColor(textColor)
                setTypeface(null, Typeface.BOLD)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }

            // Truncate sub-seconds for UI display: durationMs / 1000L * 1000L
            val truncatedMs = (stat.totalDurationMs / 1000L) * 1000L
            val formattedDuration = TimeFormatter.formatDuration(truncatedMs)

            val durationView = TextView(container.context).apply {
                text = if (stat.isAllowed) {
                    "$formattedDuration · not counted"
                } else {
                    formattedDuration
                }
                textSize = 13f
                setTextColor(if (stat.isAllowed) Color.parseColor("#777777") else textColor)
            }

            textColumn.addView(nameView)
            textColumn.addView(durationView)

            row.addView(iconView)
            row.addView(textColumn)

            if (stat.isAllowed) {
                val allowedBadge = TextView(container.context).apply {
                    text = "Allowed"
                    textSize = 12f
                    setTextColor(textColor)
                    setTypeface(null, Typeface.BOLD)
                    setPadding(dp(10), dp(4), dp(10), dp(4))

                    background = GradientDrawable().apply {
                        cornerRadius = dp(12).toFloat()
                        setColor(Color.parseColor("#E2E7DE"))
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        marginStart = dp(8)
                    }
                }
                row.addView(allowedBadge)
            }

            container.addView(row)
        }
    }
}
