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
        updateSaveButtonState()
    }

    private fun setupListeners() {
        // Single choice selection listener
        binding.rgReasons.setOnCheckedChangeListener { _, _ ->
            updateSaveButtonState()
        }

        // Save & Continue button click listener
        binding.btnSave.setOnClickListener {
            val reasonCode = getSelectedReasonCode() ?: return@setOnClickListener
            val rawNote = binding.etNote.text?.toString()?.trim()
            val optionalNote = if (rawNote.isNullOrEmpty()) null else {
                if (rawNote.length > 300) rawNote.substring(0, 300) else rawNote
            }

            // Log collected values for testing
            Log.d(TAG, "Early Stop Saved - Reason: $reasonCode, Note: ${optionalNote ?: "(none)"}")

            // Set result for calling activity
            val resultIntent = Intent().apply {
                putExtra(EXTRA_STOP_REASON, reasonCode)
                putExtra(EXTRA_STOP_MEMO, optionalNote)
            }
            setResult(RESULT_OK, resultIntent)
            finish()
        }
    }

    private fun updateSaveButtonState() {
        binding.btnSave.isEnabled = getSelectedReasonCode() != null
    }

    private fun getSelectedReasonCode(): String? {
        return when (binding.rgReasons.checkedRadioButtonId) {
            R.id.rbDistracted -> REASON_DISTRACTED
            R.id.rbTired -> REASON_TIRED
            R.id.rbInterrupted -> REASON_INTERRUPTED
            R.id.rbTaskCompleted -> REASON_TASK_COMPLETED
            R.id.rbOther -> REASON_OTHER
            else -> null
        }
    }
}
