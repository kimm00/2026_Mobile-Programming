package com.example.focustrace.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.focustrace.R
import com.example.focustrace.databinding.ActivityEarlyStopBinding
import androidx.lifecycle.lifecycleScope
import com.example.focustrace.sessionRepository
import com.example.focustrace.data.repo.SessionStatus
import kotlinx.coroutines.launch

/**
 * F-04: Early Stop Reason Selection UI
 *
 * Displays a single-choice list of reasons and an optional note field
 * when ending a focus session early.
 */
class EarlyStopActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "EarlyStopActivity"

        // Result Intent Extras
        const val EXTRA_STOP_REASON = "extra_stop_reason"
        const val EXTRA_STOP_MEMO = "extra_stop_memo"

        // Reason Codes
        const val REASON_DISTRACTED = "DISTRACTED"
        const val REASON_TIRED = "TIRED"
        const val REASON_INTERRUPTED = "INTERRUPTED"
        const val REASON_TASK_COMPLETED = "TASK_COMPLETED"
        const val REASON_OTHER = "OTHER"

        /**
         * Creates an Intent to launch EarlyStopActivity.
         */
        fun createIntent(context: Context): Intent {
            return Intent(context, EarlyStopActivity::class.java)
        }
    }

    private lateinit var binding: ActivityEarlyStopBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityEarlyStopBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.earlyStopRoot) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupListeners()
        observeSessionEnd()
    }

    private fun setupListeners() {
        // "Continue Session" button dismisses sheet without stopping session
        binding.btnContinueSession.setOnClickListener {
            finish()
        }

        // "End Session" button saves reason & note and finishes with RESULT_OK
        binding.btnSave.setOnClickListener {
            val reasonCode = getSelectedReasonCode() ?: REASON_DISTRACTED
            val rawNote = binding.etNote.text?.toString()?.trim()
            val optionalNote = if (rawNote.isNullOrEmpty()) null else {
                if (rawNote.length > 300) rawNote.substring(0, 300) else rawNote
            }

            Log.d(TAG, "Early Stop Saved - Reason: $reasonCode, Note: ${optionalNote ?: "(none)"}")

            val resultIntent = Intent().apply {
                putExtra(EXTRA_STOP_REASON, reasonCode)
                putExtra(EXTRA_STOP_MEMO, optionalNote)
            }
            setResult(RESULT_OK, resultIntent)
            finish()
        }
    }

    private fun getSelectedReasonCode(): String? {
        return when (binding.rgReasons.checkedRadioButtonId) {
            R.id.rbTaskCompleted -> REASON_TASK_COMPLETED
            R.id.rbDistracted -> REASON_DISTRACTED
            R.id.rbInterrupted -> REASON_INTERRUPTED
            R.id.rbTired -> REASON_TIRED
            R.id.rbOther -> REASON_OTHER
            else -> REASON_DISTRACTED
        }
    }

    private fun observeSessionEnd() {
        val sessionId = intent.getLongExtra(
            SessionActivity.EXTRA_SESSION_ID,
            -1L
        )

        if (sessionId <= 0L) return

        lifecycleScope.launch {
            sessionRepository.observeSession(sessionId).collect { detail ->
                val session = detail?.session ?: return@collect

                // Close this screen when the session has already ended.
                if (session.status != SessionStatus.RUNNING.name &&
                    !isFinishing
                ) {
                    setResult(RESULT_CANCELED)
                    finish()
                }
            }
        }
    }
}
