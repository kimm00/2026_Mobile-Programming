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
 * UI helper to render app usages list (What pulled you away) dynamically matching Figma design specs.
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
                setTextColor(Color.parseColor("#708264"))
            }
            container.addView(emptyView)
            return
        }

        val density = container.resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        val textColor = Color.parseColor("#45593A")
        val maxDistractionMs = appUsages.filter { !it.isAllowed }.maxOfOrNull { it.totalDurationMs }?.coerceAtLeast(1L) ?: 1L

        appUsages.forEachIndexed { index, stat ->
            val row = LinearLayout(container.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setPadding(0, dp(6), 0, dp(6))
            }

            // Rank Badge Circle (1, 2, 3...)
            val rankView = TextView(container.context).apply {
                text = (index + 1).toString()
                textSize = 12f
                setTextColor(textColor)
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(if (stat.isAllowed) Color.parseColor("#E0E0E0") else Color.parseColor("#F6AE8E"))
                }
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply {
                    marginEnd = dp(10)
                }
            }

            // App Icon
            val iconView = ImageView(container.context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply {
                    marginEnd = dp(10)
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

            // Details Column (Name & Duration)
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
                textSize = 14f
                setTextColor(textColor)
                setTypeface(null, Typeface.BOLD)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }

            val truncatedMs = (stat.totalDurationMs / 1000L) * 1000L
            val formattedDuration = TimeFormatter.formatDuration(truncatedMs)

            val durationView = TextView(container.context).apply {
                text = if (stat.isAllowed) {
                    "$formattedDuration · not counted"
                } else {
                    formattedDuration
                }
                textSize = 12f
                setTextColor(if (stat.isAllowed) Color.parseColor("#708264") else textColor)
            }

            textColumn.addView(nameView)
            textColumn.addView(durationView)

            row.addView(rankView)
            row.addView(iconView)
            row.addView(textColumn)

            // Progress Bar Track for Distraction Apps
            if (!stat.isAllowed) {
                val trackWidthDp = 72
                val fillRatio = (stat.totalDurationMs.toDouble() / maxDistractionMs).coerceIn(0.1, 1.0)
                val fillWidthDp = (trackWidthDp * fillRatio).toInt().coerceAtLeast(6)

                val barTrack = LinearLayout(container.context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(dp(trackWidthDp), dp(8)).apply {
                        marginStart = dp(8)
                    }
                    background = GradientDrawable().apply {
                        cornerRadius = dp(4).toFloat()
                        setColor(Color.parseColor("#C2E3A4"))
                    }
                    clipToOutline = true

                    val barFill = View(container.context).apply {
                        layoutParams = LinearLayout.LayoutParams(dp(fillWidthDp), LinearLayout.LayoutParams.MATCH_PARENT)
                        background = GradientDrawable().apply {
                            cornerRadius = dp(4).toFloat()
                            setColor(Color.parseColor("#F6AE8E"))
                        }
                    }
                    addView(barFill)
                }
                row.addView(barTrack)
            } else {
                // Allowed Badge
                val allowedBadge = TextView(container.context).apply {
                    text = "Allowed"
                    textSize = 11f
                    setTextColor(textColor)
                    setTypeface(null, Typeface.BOLD)
                    setPadding(dp(8), dp(3), dp(8), dp(3))

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
