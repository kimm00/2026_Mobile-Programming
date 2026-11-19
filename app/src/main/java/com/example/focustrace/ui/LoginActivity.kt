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
    private var isFormVisible: Boolean = false
    private var isSignUpMode: Boolean = false

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

        if (savedInstanceState != null) {
            isFormVisible = savedInstanceState.getBoolean(KEY_IS_FORM_VISIBLE, false)
            isSignUpMode = savedInstanceState.getBoolean(KEY_IS_SIGN_UP_MODE, false)
        }

        setupDebugButton()
        setupAuthListeners()
        applyAuthMode()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_IS_FORM_VISIBLE, isFormVisible)
        outState.putBoolean(KEY_IS_SIGN_UP_MODE, isSignUpMode)
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
            if (!isFormVisible) {
                isFormVisible = true
                isSignUpMode = false
                applyAuthMode()
            } else {
                Toast.makeText(
                    this,
                    "Firebase Auth 이메일 로그인 연동 예정 (담당자 C 구역)",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        binding.tvToggleAuthMode.setOnClickListener {
            isSignUpMode = !isSignUpMode
            applyAuthMode()
        }

        binding.btnGoOnboarding.setOnClickListener {
            startActivity(Intent(this, OnboardingActivity::class.java))
        }
    }

    private fun applyAuthMode() {
        if (isFormVisible) {
            binding.layoutEmailForm.visibility = View.VISIBLE
            binding.tvToggleAuthMode.visibility = View.VISIBLE
            if (isSignUpMode) {
                binding.tvFormTitle.text = "Create your account"
                binding.tilConfirmPassword.visibility = View.VISIBLE
                binding.tvPasswordHint.visibility = View.VISIBLE
                binding.tvLoginEmailLabel.text = "Create account"
                binding.tvToggleAuthMode.text = "Already have an account? Sign in"
            } else {
                binding.tvFormTitle.text = "Welcome back"
                binding.tilConfirmPassword.visibility = View.GONE
                binding.tvPasswordHint.visibility = View.GONE
                binding.tvLoginEmailLabel.text = "Sign in"
                binding.tvToggleAuthMode.text = "New here? Create an account"
            }
        } else {
            binding.layoutEmailForm.visibility = View.GONE
            binding.tvToggleAuthMode.visibility = View.GONE
            binding.tvLoginEmailLabel.text = "Sign in with email"
        }
    }

    companion object {
        private const val KEY_IS_FORM_VISIBLE = "key_is_form_visible"
        private const val KEY_IS_SIGN_UP_MODE = "key_is_sign_up_mode"
    }
}
