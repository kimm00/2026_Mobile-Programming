package com.example.focustrace.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.focustrace.data.repo.SessionRepository
import com.example.focustrace.data.repo.SessionStatus
import com.example.focustrace.data.repo.StopReason
import com.example.focustrace.databinding.ActivitySessionBinding
import com.example.focustrace.detection.FocusService
import com.example.focustrace.sessionRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

class SessionActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SESSION_ID = "extra_session_id"
    }

    private lateinit var binding: ActivitySessionBinding

    private val repository: SessionRepository by lazy {
        sessionRepository
    }

    private var sessionId: Long = -1L

    private val earlyStopLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->

            if (result.resultCode != RESULT_OK) {
                return@registerForActivityResult
            }

            val reasonCode = result.data
                ?.getStringExtra(EarlyStopActivity.EXTRA_STOP_REASON)

            val stopMemo = result.data
                ?.getStringExtra(EarlyStopActivity.EXTRA_STOP_MEMO)

            val stopReason = StopReason.entries.firstOrNull {
                it.name == reasonCode
            }

            if (stopReason == null) {
                Toast.makeText(
                    this,
                    "Invalid stop reason",
                    Toast.LENGTH_SHORT
                ).show()
                return@registerForActivityResult
            }

            endSession(
                status = SessionStatus.EARLY_STOP,
                reason = stopReason,
                memo = stopMemo
            )
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySessionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1L)

        if (sessionId <= 0L) {
            Toast.makeText(
                this,
                "Session ID not found",
                Toast.LENGTH_SHORT
            ).show()
            finish()
            return
        }

        setupListeners()
        observeActiveSession()
    }

    private fun setupListeners() {
        binding.btnEarlyStop.setOnClickListener {
            earlyStopLauncher.launch(
                EarlyStopActivity.createIntent(this)
            )
        }

        binding.btnFinish.setOnClickListener {
            endSession(
                status = SessionStatus.COMPLETED
            )
        }
    }

    private fun observeActiveSession() {
        lifecycleScope.launch {
            repository.observeSession(sessionId).collect { detail ->
                if (detail != null) {
                    val session = detail.session
                    binding.tvSessionSubject.text = session.subject
                    val planText = if (!session.plan.isNullOrBlank()) "${session.plan} · " else ""
                    binding.tvSessionGoal.text = "$planText${session.goalMinutes} min target"
                }
            }
        }
    }

    private fun endSession(
        status: SessionStatus,
        reason: StopReason? = null,
        memo: String? = null
    ) {
        binding.btnEarlyStop.isEnabled = false
        binding.btnFinish.isEnabled = false

        lifecycleScope.launch {
            try {
                val saved = waitForDetectionToStop()

                if (!saved) {
                    throw IllegalStateException(
                        "Phone usage saving failed"
                    )
                }

                repository.endSession(
                    sessionId = sessionId,
                    status = status,
                    stopReason = reason,
                    stopMemo = memo
                )

                val intent = Intent(this@SessionActivity, FinishActivity::class.java).apply {
                    putExtra(FinishActivity.EXTRA_SESSION_ID, sessionId)
                }
                startActivity(intent)

                finish()
            } catch (e: Exception) {
                Toast.makeText(
                    this@SessionActivity,
                    "Failed to finish session: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()

                binding.btnEarlyStop.isEnabled = true
                binding.btnFinish.isEnabled = true
            }
        }
    }

    private suspend fun waitForDetectionToStop(): Boolean {
        return withTimeout(15_000L) {
            suspendCancellableCoroutine { continuation ->

                val receiver = object : ResultReceiver(
                    Handler(Looper.getMainLooper())
                ) {
                    override fun onReceiveResult(
                        resultCode: Int,
                        resultData: Bundle?
                    ) {
                        if (continuation.isActive) {
                            continuation.resume(
                                resultCode == FocusService.RESULT_OK
                            )
                        }
                    }
                }

                try {
                    FocusService.stopService(
                        context = this@SessionActivity,
                        sessionId = sessionId,
                        receiver = receiver
                    )
                } catch (e: Exception) {
                    if (continuation.isActive) {
                        continuation.resume(false)
                    }
                }
            }
        }
    }
}
