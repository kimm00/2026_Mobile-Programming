package com.example.focustrace.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.focustrace.data.repo.SessionRepository
import com.example.focustrace.data.repo.SessionStatus
import com.example.focustrace.data.repo.StopReason
import com.example.focustrace.databinding.ActivitySessionBinding
import com.example.focustrace.detection.FocusService
import com.example.focustrace.sessionRepository
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.util.Locale

class SessionActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SESSION_ID = "extra_session_id"
    }

    private lateinit var binding: ActivitySessionBinding

    private val repository: SessionRepository by lazy {
        sessionRepository
    }

    private var sessionId: Long = -1L
    private var isEnding = false

    private var autoFinishJob: Job? = null

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
        setupBackPressHandler()
        observeActiveSession()
    }

    private fun setupListeners() {
        binding.btnEarlyStop.setOnClickListener {
            earlyStopLauncher.launch(
                EarlyStopActivity.createIntent(this).apply {
                    putExtra(SessionActivity.EXTRA_SESSION_ID, sessionId)
                }
            )
        }

        binding.btnFinish.setOnClickListener {
            endSession(
                status = SessionStatus.COMPLETED
            )
        }
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (isEnding || !binding.btnEarlyStop.isEnabled) {
                        return
                    }
                    showBackPressDialog()
                }
            }
        )
    }

    private fun showBackPressDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Session in progress")
            .setPositiveButton("Keep going") { dialog, _ ->
                dialog.dismiss()
            }
            .setNegativeButton("End early") { _, _ ->
                earlyStopLauncher.launch(
                    EarlyStopActivity.createIntent(this)
                )
            }
            .show()
    }

    private fun observeActiveSession() {
        lifecycleScope.launch {
            repository.observeSession(sessionId).collect { detail ->
                if (detail == null) return@collect

                val session = detail.session

                binding.tvSessionSubject.text = session.subject

                val planText = if (!session.plan.isNullOrBlank()) {
                    "${session.plan} / "
                } else {
                    ""
                }

                binding.tvSessionGoal.text =
                    "$planText${session.goalMinutes} min target"

                if (session.status != SessionStatus.RUNNING.name) {
                    autoFinishJob?.cancel()
                    return@collect
                }

                if (isEnding || autoFinishJob != null) {
                    return@collect
                }

                val totalMs = session.goalMinutes * 60_000L
                val deadline = session.startAt + totalMs

                autoFinishJob = lifecycleScope.launch {
                    var lastDisplayedSeconds = -1L

                    while (!isEnding) {
                        // Calculate from the original deadline to avoid cumulative drift.
                        val remainingMs = (deadline - System.currentTimeMillis())
                            .coerceIn(0L, totalMs)

                        // Round up so 00:00 is shown only when the session has ended.
                        val remainingSeconds = (remainingMs + 999L) / 1000L

                        if (remainingSeconds != lastDisplayedSeconds) {
                            binding.tvRemainingTime.text = String.format(
                                Locale.US,
                                "%02d:%02d",
                                remainingSeconds / 60L,
                                remainingSeconds % 60L
                            )
                            lastDisplayedSeconds = remainingSeconds
                        }

                        binding.countdownRing.setRemainingFraction(
                            remainingMs.toFloat() / totalMs
                        )

                        if (remainingMs == 0L) {
                            endSession(status = SessionStatus.COMPLETED)
                            break
                        }

                        delay(50L)
                    }
                }
            }
        }
    }

    private fun endSession(
        status: SessionStatus,
        reason: StopReason? = null,
        memo: String? = null
    ) {
        if (isEnding) return

        isEnding = true
        autoFinishJob?.cancel()
        autoFinishJob = null

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

                isEnding = false
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
