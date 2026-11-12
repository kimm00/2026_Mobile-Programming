package com.example.focustrace.ui

import android.app.AppOpsManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import com.example.focustrace.databinding.ActivityUsagePermissionBinding

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
        if (isUsagePermissionGranted()) {
            startActivity(Intent(this, PrepareActivity::class.java))
            finish()
        }
    }

    private fun isUsagePermissionGranted(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
