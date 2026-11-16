package com.example.focustrace.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.focustrace.MainActivity
import com.example.focustrace.R
import com.example.focustrace.databinding.ActivityOnboardingBinding
import com.example.focustrace.detection.UsageStatsHelper

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private var isBatteryLaterDismissed = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        updatePermissionStatuses()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.onboardingRoot) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnGrantUsage.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        binding.btnGrantNotifications.setOnClickListener {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        binding.btnOpenBatterySettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }

        binding.btnBatteryLater.setOnClickListener {
            isBatteryLaterDismissed = true
            updatePermissionStatuses()
        }

        binding.btnContinueOnboarding.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatuses()
    }

    private fun updatePermissionStatuses() {
        val usageGranted = UsageStatsHelper.hasUsageStatsPermission(this)
        binding.tvStatusUsage.text = if (usageGranted) "Allowed ✓" else "Not allowed"
        binding.btnGrantUsage.isEnabled = !usageGranted

        val notificationGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        binding.tvStatusNotifications.text = if (notificationGranted) "Allowed ✓" else "Not allowed"
        binding.btnGrantNotifications.isEnabled = !notificationGranted

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        val batteryGranted = powerManager.isIgnoringBatteryOptimizations(packageName)
        binding.tvStatusBattery.text = if (batteryGranted) "Allowed ✓" else "Not allowed"
        val showBatteryButtons = !batteryGranted && !isBatteryLaterDismissed
        binding.btnOpenBatterySettings.visibility = if (showBatteryButtons) View.VISIBLE else View.GONE
        binding.btnBatteryLater.visibility = if (showBatteryButtons) View.VISIBLE else View.GONE

        val canContinue = usageGranted && notificationGranted
        binding.btnContinueOnboarding.isEnabled = canContinue
        binding.btnContinueOnboarding.alpha = if (canContinue) 1.0f else 0.4f

        val count = listOf(usageGranted, notificationGranted, batteryGranted).count { it }
        binding.tvProgressCount.text = "$count of 3 completed"

        val activeColor = ContextCompat.getColor(this, R.color.focus_brand_green)
        val inactiveColor = ContextCompat.getColor(this, R.color.focus_border_green)
        binding.viewProgress1.setBackgroundColor(if (count >= 1) activeColor else inactiveColor)
        binding.viewProgress2.setBackgroundColor(if (count >= 2) activeColor else inactiveColor)
        binding.viewProgress3.setBackgroundColor(if (count >= 3) activeColor else inactiveColor)
    }
}
