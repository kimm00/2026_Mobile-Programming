package com.example.focustrace.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.focustrace.R

class FinishActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_finish)

        findViewById<Button>(R.id.btn_report).setOnClickListener {
            startActivity(Intent(this, ReportActivity::class.java))
        }
    }
}
