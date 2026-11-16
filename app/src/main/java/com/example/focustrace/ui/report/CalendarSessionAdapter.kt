package com.example.focustrace.ui.report

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.focustrace.data.model.ReportSession
import com.example.focustrace.databinding.ItemCalendarSessionBinding
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Adapter for displaying calendar sessions on the selected day.
 * Supports long click callback for context menu (e.g. Delete).
 */
class CalendarSessionAdapter(
    private val zoneId: ZoneId = ZoneId.systemDefault(),
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
        holder.bind(getItem(position), position + 1)
    }

    inner class ViewHolder(
        private val binding: ItemCalendarSessionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(session: ReportSession, index: Int) {
            binding.tvSessionSubject.text = "$index. [${session.subject}]"
            binding.tvSessionStatusBadge.text = session.status

            val startTimeStr = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
                .format(Instant.ofEpochMilli(session.startAt).atZone(zoneId))

            val focusStr = TimeFormatter.formatDuration(session.focusMs)
            val phoneStr = TimeFormatter.formatDuration(session.phoneUseMs)

            binding.tvSessionTimeDetails.text =
                "$startTimeStr · $focusStr focus | Phone: $phoneStr | Checks: ${session.checkCount}"

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
