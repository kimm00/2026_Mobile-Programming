package com.example.focustrace.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import com.example.focustrace.databinding.ActivityUsagePermissionBinding
import com.example.focustrace.detection.UsageStatsHelper

class UsagePermissionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUsagePermissionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityUsagePermissionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnGrantPermission.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        if (UsageStatsHelper.hasUsageStatsPermission(this)) {
            startActivity(Intent(this, PrepareActivity::class.java))
            finish()
        }
    }
}
