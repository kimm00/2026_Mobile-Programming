package com.example.focustrace.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.focustrace.R
import com.example.focustrace.data.db.AppDatabase
import com.example.focustrace.data.repo.RoomSessionRepository
import com.example.focustrace.detection.FocusService
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class PrepareActivity : AppCompatActivity() {

    private val repository by lazy {
        RoomSessionRepository(
            AppDatabase.get(applicationContext).sessionDao()
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_prepare)

        val tilSubject = findViewById<TextInputLayout>(R.id.til_subject)
        val etSubject = findViewById<TextInputEditText>(R.id.et_subject)
        val tilGoalMinutes = findViewById<TextInputLayout>(R.id.til_goal_minutes)
        val etGoalMinutes = findViewById<TextInputEditText>(R.id.et_goal_minutes)
        val startButton = findViewById<Button>(R.id.btn_session)

        startButton.setOnClickListener {
            tilSubject.error = null
            tilGoalMinutes.error = null

            val subjectText = etSubject.text?.toString()?.trim().orEmpty()
            val goalMinutesText = etGoalMinutes.text?.toString()?.trim().orEmpty()
            val goalMinutes = goalMinutesText.toIntOrNull()

            var isValid = true

            if (subjectText.isBlank()) {
                tilSubject.error = "Subject cannot be blank"
                isValid = false
            }

            if (goalMinutes == null || goalMinutes !in 1..360) {
                tilGoalMinutes.error = "Goal minutes must be between 1 and 360"
                isValid = false
            }

            if (!isValid) return@setOnClickListener

            startButton.isEnabled = false

            lifecycleScope.launch {
                try {
                    val sessionId = repository.startSession(
                        subject = subjectText,
                        plan = null,
                        goalMinutes = goalMinutes!!
                    )

                    FocusService.startService(
                        context = this@PrepareActivity,
                        sessionId = sessionId
                    )

                    val intent = Intent(
                        this@PrepareActivity,
                        SessionActivity::class.java
                    ).apply {
                        putExtra(
                            SessionActivity.EXTRA_SESSION_ID,
                            sessionId
                        )
                    }

                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(
                        this@PrepareActivity,
                        "Failed to start session",
                        Toast.LENGTH_SHORT
                    ).show()
                } finally {
                    startButton.isEnabled = true
                }
            }
        }
    }
}
