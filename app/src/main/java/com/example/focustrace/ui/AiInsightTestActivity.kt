package com.example.focustrace.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.focustrace.R
import com.example.focustrace.ai.AiFocusInput
import com.example.focustrace.ai.AiInsightSampleData
import com.example.focustrace.ai.AiInsightService
import com.example.focustrace.ai.RoomAiFocusInputBuilder
import com.example.focustrace.data.db.AppDatabase
import com.example.focustrace.data.db.entity.SessionEntity
import com.example.focustrace.data.repo.SessionRepository
import com.example.focustrace.databinding.ActivityAiInsightTestBinding
import com.example.focustrace.sample.SampleData
import com.example.focustrace.sessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AiInsightTestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAiInsightTestBinding
    private val aiService by lazy { AiInsightService() }
    private val repository: SessionRepository by lazy { sessionRepository }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityAiInsightTestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.aiInsightRoot) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupListeners()
        runInsightGeneration()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnStartFocusInsight.setOnClickListener {
            startActivity(Intent(this, PrepareActivity::class.java))
        }

        binding.btnSeedDemoData.setOnClickListener {
            seedDemoSessions()
        }

        binding.btnGenerateInsights.setOnClickListener {
            runInsightGeneration()
        }
    }

    private suspend fun getSelectedInput(): AiFocusInput {
        return when (binding.rgSampleData.checkedRadioButtonId) {
            R.id.rbInsufficient -> AiInsightSampleData.SAMPLE_INSUFFICIENT
            R.id.rbHighInterruption -> AiInsightSampleData.SAMPLE_HIGH_INTERRUPTION
            R.id.rbRealRoomDb -> {
                val sessions = repository.getSessionsByPeriod(0L, System.currentTimeMillis())
                RoomAiFocusInputBuilder.buildFromSessions(sessions)
            }
            else -> AiInsightSampleData.SAMPLE_SUFFICIENT
        }
    }

    private fun runInsightGeneration() {
        binding.progressLoading.visibility = View.VISIBLE
        binding.tvStatusMessage.text = "Status: Generating Insights..."

        lifecycleScope.launch {
            try {
                val input = withContext(Dispatchers.IO) { getSelectedInput() }
                val result = aiService.generateInsights(input, forceFallback = false)

                binding.tvRecommendedDurationChip.text = "${result.recommendedDurationMinutes} min duration"

                if (result.insights.isNotEmpty()) {
                    binding.tvRecommendedStep.text = result.insights[0]
                    if (result.insights.size > 1) {
                        binding.tvSupportingInsight.text = result.insights[1]
                    }
                    if (result.insights.size > 2) {
                        binding.tvCompletionObservation.text = result.insights[2]
                    }
                }

                binding.tvStatusMessage.text = "Status: ${result.statusMessage}"
            } catch (e: Exception) {
                binding.tvStatusMessage.text = "Error: ${e.message}"
            } finally {
                binding.progressLoading.visibility = View.GONE
            }
        }
    }

    private fun seedDemoSessions() {
        binding.progressLoading.visibility = View.VISIBLE
        binding.tvStatusMessage.text = "Status: Seeding sample sessions into Room DB..."

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val dao = AppDatabase.get(applicationContext).sessionDao()
                    SampleData.SESSIONS.forEach { session ->
                        val entity = SessionEntity(
                            id = session.id,
                            subject = session.subject,
                            plan = session.plan,
                            goalMinutes = session.goalMinutes,
                            startAt = session.startAt,
                            endAt = session.endAt,
                            status = session.status,
                            stopReason = session.stopReason,
                            stopMemo = session.stopMemo,
                            reflection = session.reflection,
                            photoUri = session.photoUri,
                            focusMs = session.focusMs,
                            phoneUseMs = session.phoneUseMs,
                            checkCount = session.checkCount,
                            unlockOnlyCount = session.unlockOnlyCount,
                            longestFocusMs = session.longestFocusMs,
                            isSample = false
                        )
                        dao.insertSession(entity)
                    }
                }

                binding.tvStatusMessage.text = "Status: Successfully seeded demo sessions."
                binding.rbRealRoomDb.isChecked = true
                runInsightGeneration()
            } catch (e: Exception) {
                binding.tvStatusMessage.text = "Failed to seed demo data: ${e.message}"
            } finally {
                binding.progressLoading.visibility = View.GONE
            }
        }
    }
}
