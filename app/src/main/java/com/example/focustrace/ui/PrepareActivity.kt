package com.example.focustrace.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.focustrace.R

class PrepareActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_prepare)

        findViewById<Button>(R.id.btn_session).setOnClickListener {
            startActivity(Intent(this, SessionActivity::class.java))
        }
    }
}
