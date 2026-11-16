package com.example.focustrace.ui.report

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.focustrace.R
import com.example.focustrace.data.model.ReportSession
import com.example.focustrace.databinding.ItemCalendarSessionBinding
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Adapter for displaying calendar sessions matching the Figma & design spec.
 * Supports long click callback for context menu (e.g. Delete).
 */
class CalendarSessionAdapter(
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val onSessionClick: ((ReportSession) -> Unit)? = null,
    private val onSessionLongClick: (ReportSession, View) -> Boolean
) : ListAdapter<ReportSession, CalendarSessionAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCalendarSessionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position == itemCount - 1)
    }

    inner class ViewHolder(
        private val binding: ItemCalendarSessionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(session: ReportSession, isLast: Boolean) {
            val context = binding.root.context
            val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

            val startStr = timeFormatter.format(Instant.ofEpochMilli(session.startAt).atZone(zoneId))
            val endStr = if (session.endAt != null) {
                timeFormatter.format(Instant.ofEpochMilli(session.endAt).atZone(zoneId))
            } else "Ongoing"

            // Line 1: Subject · Start-End Time
            binding.tvSessionSubjectAndTime.text = "${session.subject} · $startStr–$endStr"

            // Line 2: Focus Time + Checks Badge
            binding.tvSessionFocusTime.text = "${TimeFormatter.formatDuration(session.focusMs)} focus"
            binding.tvSessionChecksBadge.text = "${session.checkCount} checks"

            // Line 3: Longest Focus · Phone Use
            val longestStr = TimeFormatter.formatDuration(session.longestFocusMs)
            val phoneStr = TimeFormatter.formatDuration(session.phoneUseMs)
            binding.tvSessionSubDetails.text = "$longestStr longest · $phoneStr phone use"

            // Line 4: Status Target Tag
            when (session.status) {
                "COMPLETED" -> {
                    binding.tvSessionStatusTag.text = "Completed · ${session.goalMinutes}-minute target"
                    binding.tvSessionStatusTag.background = ContextCompat.getDrawable(context, R.drawable.bg_badge_completed)
                }
                "EARLY_STOP" -> {
                    val reasonStr = session.stopReason?.let { " ($it)" } ?: ""
                    binding.tvSessionStatusTag.text = "Early stop$reasonStr · ${session.goalMinutes}-minute target"
                    binding.tvSessionStatusTag.background = ContextCompat.getDrawable(context, R.drawable.bg_badge_early_stop)
                }
                else -> {
                    binding.tvSessionStatusTag.text = "${session.status} · ${session.goalMinutes}-minute target"
                    binding.tvSessionStatusTag.background = ContextCompat.getDrawable(context, R.drawable.bg_badge_early_stop)
                }
            }

            // Hide bottom divider for last item
            binding.sessionItemDivider.visibility = if (isLast) View.GONE else View.VISIBLE

            binding.root.setOnClickListener {
                onSessionClick?.invoke(session)
            }

            binding.root.setOnLongClickListener { view ->
                onSessionLongClick(session, view)
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<ReportSession>() {
        override fun areItemsTheSame(oldItem: ReportSession, newItem: ReportSession): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ReportSession, newItem: ReportSession): Boolean {
            return oldItem == newItem
        }
    }
}
