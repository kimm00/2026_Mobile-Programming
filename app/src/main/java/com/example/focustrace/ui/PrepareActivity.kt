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

        val startButton = findViewById<Button>(R.id.btn_session)

        startButton.setOnClickListener {
            startButton.isEnabled = false

            lifecycleScope.launch {
                try {
                    val sessionId = repository.startSession(
                        subject = "Study",
                        plan = null,
                        goalMinutes = 25
                    )

                    val intent = Intent(
                        this@PrepareActivity,
                        SessionActivity::class.java
                    ).apply {
                        putExtra(SessionActivity.EXTRA_SESSION_ID, sessionId)
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
