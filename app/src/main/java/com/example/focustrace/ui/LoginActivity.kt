package com.example.focustrace.ui

import android.Manifest
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.focustrace.MainActivity
import com.example.focustrace.authRepository
import com.example.focustrace.data.repo.AuthResult
import com.example.focustrace.databinding.ActivityLoginBinding
import com.example.focustrace.detection.UsageStatsHelper
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private var isFormVisible: Boolean = false
    private var isSignUpMode: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (authRepository.isSignedIn()) {
            goToNextScreen()
            return
        }
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
        setupTextWatchers()
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
                if (validateInputs()) {
                    performAuth()
                }
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

    private fun performAuth() {
        val email = binding.etEmail.text?.toString()?.trim().orEmpty()
        val password = binding.etPassword.text?.toString().orEmpty()

        binding.btnLoginEmail.isEnabled = false
        binding.tvToggleAuthMode.isEnabled = false
        binding.tvLoginEmailLabel.text = "Please wait…"

        lifecycleScope.launch {
            val result = if (isSignUpMode) {
                authRepository.signUpWithEmail(email, password)
            } else {
                authRepository.signInWithEmail(email, password)
            }

            when (result) {
                is AuthResult.Success -> {
                    goToNextScreen()
                }
                is AuthResult.Error -> {
                    Snackbar.make(binding.root, result.message, Snackbar.LENGTH_LONG).show()
                    binding.btnLoginEmail.isEnabled = true
                    binding.tvToggleAuthMode.isEnabled = true
                    applyAuthMode()
                }
            }
        }
    }

    private fun goToNextScreen() {
        val usageGranted = UsageStatsHelper.hasUsageStatsPermission(this)
        val notificationGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        val targetActivity = if (usageGranted && notificationGranted) {
            MainActivity::class.java
        } else {
            OnboardingActivity::class.java
        }

        startActivity(Intent(this, targetActivity))
        finish()
    }

    private fun setupTextWatchers() {
        binding.etEmail.doAfterTextChanged { binding.tilEmail.error = null }
        binding.etPassword.doAfterTextChanged { binding.tilPassword.error = null }
        binding.etConfirmPassword.doAfterTextChanged { binding.tilConfirmPassword.error = null }
    }

    private fun validateInputs(): Boolean {
        clearErrors()

        val email = binding.etEmail.text?.toString()?.trim().orEmpty()
        val password = binding.etPassword.text?.toString().orEmpty()
        val confirmPassword = binding.etConfirmPassword.text?.toString().orEmpty()

        var isValid = true

        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = "Enter a valid email."
            isValid = false
        }

        if (isSignUpMode) {
            if (password.length < 8) {
                binding.tilPassword.error = "Use at least 8 characters."
                isValid = false
            }
            if (confirmPassword != password) {
                binding.tilConfirmPassword.error = "Passwords don't match."
                isValid = false
            }
        } else {
            if (password.isEmpty()) {
                binding.tilPassword.error = "Enter your password."
                isValid = false
            }
        }

        return isValid
    }

    private fun clearErrors() {
        binding.tilEmail.error = null
        binding.tilPassword.error = null
        binding.tilConfirmPassword.error = null
    }

    private fun applyAuthMode() {
        clearErrors()
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
