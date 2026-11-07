package com.example.focustrace.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.focustrace.R

class SessionActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session)

        findViewById<Button>(R.id.btn_early_stop).setOnClickListener {
            startActivity(Intent(this, EarlyStopActivity::class.java))
        }

        findViewById<Button>(R.id.btn_finish).setOnClickListener {
            startActivity(Intent(this, FinishActivity::class.java))
        }
    }
}
