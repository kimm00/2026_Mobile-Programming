package com.example.focustrace.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.focustrace.MainActivity
import com.example.focustrace.data.db.entity.SessionEntity
import com.example.focustrace.data.repo.SessionRepository
import com.example.focustrace.databinding.ActivityJournalBinding
import com.example.focustrace.databinding.DialogWriteReflectionBinding
import com.example.focustrace.databinding.ItemJournalCardBinding
import com.example.focustrace.sessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class JournalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityJournalBinding
    private val repository: SessionRepository by lazy { sessionRepository }
    private val zoneId = ZoneId.systemDefault()

    private var reflectionSessions = listOf<SessionEntity>()
    private lateinit var journalAdapter: JournalAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityJournalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.journalRoot) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupRecyclerView()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        loadReflections()
    }

    private fun setupRecyclerView() {
        journalAdapter = JournalAdapter(
            onItemClick = { session -> showReflectionDetailDialog(session) }
        )
        binding.rvReflections.apply {
            layoutManager = LinearLayoutManager(this@JournalActivity)
            adapter = journalAdapter
        }
    }

    private fun setupListeners() {
        binding.btnWriteReflection.setOnClickListener {
            showWriteReflectionDialog()
        }

        binding.btnNavHome.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        binding.btnNavReport.setOnClickListener {
            startActivity(Intent(this, ReportActivity::class.java))
            finish()
        }

        binding.btnNavJournal.setOnClickListener {
            // Already on Journal
        }
    }

    private fun loadReflections() {
        lifecycleScope.launch {
            val sessions = withContext(Dispatchers.IO) {
                repository.getSessionsByPeriod(0L, Long.MAX_VALUE).filter {
                    !it.reflection.isNullOrBlank() || !it.stopMemo.isNullOrBlank()
                }.sortedByDescending { it.startAt }
            }

            reflectionSessions = sessions
            if (sessions.isEmpty()) {
                binding.layoutEmptyState.visibility = View.VISIBLE
                binding.rvReflections.visibility = View.GONE
            } else {
                binding.layoutEmptyState.visibility = View.GONE
                binding.rvReflections.visibility = View.VISIBLE
                journalAdapter.submitList(sessions)
            }
        }
    }

    private fun showWriteReflectionDialog() {
        lifecycleScope.launch {
            val lastSession = withContext(Dispatchers.IO) {
                repository.getSessionsByPeriod(0L, Long.MAX_VALUE).firstOrNull()
            }

            if (lastSession == null) {
                Toast.makeText(this@JournalActivity, "No completed session found to attach reflection", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val dialogBinding = DialogWriteReflectionBinding.inflate(layoutInflater)
            dialogBinding.tvDialogSubtitle.text = "Session: ${lastSession.subject}"

            AlertDialog.Builder(this@JournalActivity)
                .setView(dialogBinding.root)
                .setPositiveButton("Save") { _, _ ->
                    val text = dialogBinding.etReflection.text?.toString()?.trim().orEmpty()
                    if (text.isNotBlank()) {
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO) {
                                repository.updateSessionReflection(lastSession.id, text)
                            }
                            loadReflections()
                            Toast.makeText(this@JournalActivity, "Reflection saved", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun showReflectionDetailDialog(session: SessionEntity) {
        val dateStr = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH)
            .format(Instant.ofEpochMilli(session.startAt).atZone(zoneId).toLocalDate())
        val text = session.reflection ?: session.stopMemo.orEmpty()

        AlertDialog.Builder(this)
            .setTitle(session.subject)
            .setMessage("$dateStr\n\nReflection:\n$text")
            .setPositiveButton("OK", null)
            .setNeutralButton("Delete") { _, _ ->
                showDeleteConfirmDialog(session)
            }
            .show()
    }

    private fun showDeleteConfirmDialog(session: SessionEntity) {
        AlertDialog.Builder(this)
            .setTitle("Delete this reflection?")
            .setMessage("It will be removed from your Journal. The session record stays.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        repository.updateSessionReflection(session.id, null)
                    }
                    loadReflections()
                    Toast.makeText(this@JournalActivity, "Reflection deleted", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private inner class JournalAdapter(
        private val onItemClick: (SessionEntity) -> Unit
    ) : RecyclerView.Adapter<JournalAdapter.ViewHolder>() {

        private var items = listOf<SessionEntity>()

        fun submitList(newItems: List<SessionEntity>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val itemBinding = ItemJournalCardBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(itemBinding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        inner class ViewHolder(
            private val itemBinding: ItemJournalCardBinding
        ) : RecyclerView.ViewHolder(itemBinding.root) {

            fun bind(session: SessionEntity) {
                val date = Instant.ofEpochMilli(session.startAt).atZone(zoneId).toLocalDate()
                val dateStr = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH).format(date)
                val focusMins = session.focusMs / 60000

                itemBinding.tvJournalDate.text = dateStr
                itemBinding.tvJournalFocusTag.text = "⏱ $focusMins min focus"
                itemBinding.tvJournalSubject.text = session.subject
                itemBinding.tvJournalBody.text = session.reflection ?: session.stopMemo.orEmpty()

                itemBinding.root.setOnClickListener {
                    onItemClick(session)
                }
            }
        }
    }
}
