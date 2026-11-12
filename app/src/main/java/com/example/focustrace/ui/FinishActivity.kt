package com.example.focustrace.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.focustrace.MainActivity
import com.example.focustrace.data.db.entity.SessionEntity
import com.example.focustrace.data.repo.SessionRepository
import com.example.focustrace.databinding.ActivityFinishBinding
import com.example.focustrace.databinding.BottomSheetPhotoSourceBinding
import com.example.focustrace.sessionRepository
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FinishActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SESSION_ID = "extra_session_id"
    }

    private lateinit var binding: ActivityFinishBinding
    private val repository: SessionRepository by lazy { sessionRepository }

    private var targetSessionId: Long = -1L
    private var currentSession: SessionEntity? = null

    private val cameraFlowLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            binding.tvPhotoStatusNote.text = "Photo Attached (CameraX UI Flow Preview)"
            Toast.makeText(this, "Photo attached to session record (Preview UI)", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityFinishBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.finishRoot) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        targetSessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1L)

        setupListeners()
        loadSessionData()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnSaveReflection.setOnClickListener {
            val reflectionText = binding.etReflection.text?.toString()?.trim().orEmpty()
            val sid = targetSessionId
            if (sid > 0L && reflectionText.isNotBlank()) {
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        repository.updateSessionReflection(sid, reflectionText)
                    }
                    Toast.makeText(this@FinishActivity, "Reflection saved to Journal", Toast.LENGTH_SHORT).show()
                }
            } else if (reflectionText.isBlank()) {
                Toast.makeText(this, "Reflection text cannot be blank", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnPhotoCard.setOnClickListener {
            showPhotoSourceSheet()
        }

        binding.btnDone.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            finish()
        }
    }

    private fun loadSessionData() {
        lifecycleScope.launch {
            if (targetSessionId <= 0L) {
                // If not passed in Intent, fetch most recent session
                val recentSessions = withContext(Dispatchers.IO) {
                    repository.getSessionsByPeriod(0L, System.currentTimeMillis())
                }
                val recent = recentSessions.firstOrNull { it.status != "RUNNING" }
                if (recent != null) {
                    targetSessionId = recent.id
                }
            }

            val sid = targetSessionId
            if (sid > 0L) {
                repository.observeSession(sid).collect { detail ->
                    if (detail != null) {
                        bindSessionDetail(detail.session, detail.events.flatMap { it.usages })
                    }
                }
            } else {
                binding.tvHeroSubtext.text = "No session data found"
            }
        }
    }

    private fun bindSessionDetail(
        session: SessionEntity,
        usages: List<com.example.focustrace.data.db.entity.AppUsageEntity>
    ) {
        currentSession = session

        val focusMins = session.focusMs / 60000
        val phoneUseMins = session.phoneUseMs / 60000
        val phoneUseSecs = (session.phoneUseMs % 60000) / 1000

        binding.tvHeroHeading.text = if (session.status == "COMPLETED") "Nice work!" else "Session Ended"
        binding.tvHeroSubtext.text = "You stayed focused for $focusMins minutes."

        binding.tvStatusBadge.text = when (session.status) {
            "COMPLETED" -> "Completed"
            "EARLY_STOP" -> "Early Stopped (${session.stopReason ?: "User choice"})"
            else -> session.status
        }

        val planText = if (!session.plan.isNullOrBlank()) " · Plan: ${session.plan}" else ""
        binding.tvSessionMeta.text = "Subject: ${session.subject}$planText"

        binding.tvMetricFocus.text = "${focusMins}m"
        binding.tvMetricChecks.text = session.checkCount.toString()

        val longestMins = session.longestFocusMs / 60000
        binding.tvMetricLongest.text = "${longestMins} min"
        binding.tvMetricPhoneUse.text = if (phoneUseMins > 0) "${phoneUseMins}m ${phoneUseSecs}s" else "${phoneUseSecs}s"

        // Reflection
        if (!session.reflection.isNullOrBlank()) {
            binding.etReflection.setText(session.reflection)
        } else if (!session.stopMemo.isNullOrBlank()) {
            binding.etReflection.setText(session.stopMemo)
        }

        // Timeline Summary
        if (session.checkCount == 0) {
            binding.tvTimelineSummary.text = "No phone interruptions recorded during this session."
        } else {
            binding.tvTimelineSummary.text = "Total ${session.checkCount} phone check(s) and ${session.unlockOnlyCount} unlock-only event(s)."
        }

        // Top Distractions
        val countedUsages = usages.filter { it.isCounted && !it.isAllowed }
        if (countedUsages.isEmpty()) {
            binding.tvDistractionsContent.text = "Nothing pulled you away this time."
        } else {
            val appUsageMap = countedUsages.groupBy { it.appLabel }
                .mapValues { entry -> entry.value.sumOf { it.durationMs } }
                .toList()
                .sortedByDescending { it.second }

            val sb = StringBuilder()
            appUsageMap.take(3).forEachIndexed { index, pair ->
                val mins = pair.second / 60000
                val secs = (pair.second % 60000) / 1000
                val timeStr = if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
                sb.append("${index + 1}. ${pair.first}: $timeStr\n")
            }
            binding.tvDistractionsContent.text = sb.toString().trim()
        }

        // Session Summary
        binding.tvAiSummaryContent.text = if (session.status == "COMPLETED") {
            "You completed your $focusMins-minute session as planned for ${session.subject}."
        } else {
            "Session stopped early (${session.stopReason ?: "User choice"}). Try shorter intervals next time."
        }
    }

    private fun showPhotoSourceSheet() {
        val sheetDialog = BottomSheetDialog(this)
        val sheetBinding = BottomSheetPhotoSourceBinding.inflate(layoutInflater)
        sheetDialog.setContentView(sheetBinding.root)

        sheetBinding.btnOptionCamera.setOnClickListener {
            sheetDialog.dismiss()
            cameraFlowLauncher.launch(Intent(this, CameraActivity::class.java))
        }

        sheetBinding.btnOptionGallery.setOnClickListener {
            sheetDialog.dismiss()
            binding.tvPhotoStatusNote.text = "Photo Attached (Gallery Preview)"
            Toast.makeText(this, "Gallery photo selected (Preview UI)", Toast.LENGTH_SHORT).show()
        }

        sheetBinding.btnCancelPhoto.setOnClickListener {
            sheetDialog.dismiss()
        }

        sheetDialog.show()
    }
}
