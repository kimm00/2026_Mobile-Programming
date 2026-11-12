package com.example.focustrace.ui

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.focustrace.MainActivity
import com.example.focustrace.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.loginRoot) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupDebugButton()
        setupAuthListeners()
    }

    private fun setupDebugButton() {
        val isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (isDebuggable) {
            binding.btnDebugGoHome.visibility = View.VISIBLE
            binding.btnDebugGoHome.setOnClickListener {
                Toast.makeText(this, "개발용: 인증을 건너뛰고 홈으로 이동합니다.", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, MainActivity::class.java))
            }
        } else {
            binding.btnDebugGoHome.visibility = View.GONE
        }
    }

    private fun setupAuthListeners() {
        binding.btnLoginGoogle.setOnClickListener {
            // Placeholder notice for Firebase Auth / Google Sign-In Integration (Track C)
            Toast.makeText(
                this,
                "Firebase Auth & Google 로그인 연동 예정 (담당자 C 구역)",
                Toast.LENGTH_LONG
            ).show()
        }

        binding.btnLoginEmail.setOnClickListener {
            // Toggle email form or show notice
            if (binding.layoutEmailForm.visibility == View.GONE) {
                binding.layoutEmailForm.visibility = View.VISIBLE
            } else {
                Toast.makeText(
                    this,
                    "Firebase Auth 이메일 로그인 연동 예정 (담당자 C 구역)",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        binding.btnGoOnboarding.setOnClickListener {
            startActivity(Intent(this, OnboardingActivity::class.java))
        }
    }
}
