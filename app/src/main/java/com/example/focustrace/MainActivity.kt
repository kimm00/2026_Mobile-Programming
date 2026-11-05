package com.example.focustrace

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.focustrace.detection.FocusService
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var tvLog: TextView
    private lateinit var btnToggleService: Button

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Toast.makeText(this, "Notification permission granted: $granted", Toast.LENGTH_SHORT).show()
        refreshLog()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        tvLog = findViewById(R.id.tvLog)
        btnToggleService = findViewById(R.id.btnToggleService)

        findViewById<Button>(R.id.btnNotificationPerm).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Notification permission already granted", Toast.LENGTH_SHORT).show()
                } else {
                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                Toast.makeText(this, "Notification permission not required below Android 13", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnUsageSettings).setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                startActivity(intent)
                Toast.makeText(this, "Please enable Usage Access for FocusTrace", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to open usage settings: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnBatteryOpt).setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to request battery exemption: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        btnToggleService.setOnClickListener {
            if (FocusService.instance == null) {
                val intent = Intent(this, FocusService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                Toast.makeText(this, "FocusService Started", Toast.LENGTH_SHORT).show()
                btnToggleService.text = "Stop Service"
            } else {
                val intent = Intent(this, FocusService::class.java)
                stopService(intent)
                Toast.makeText(this, "FocusService Stopped", Toast.LENGTH_SHORT).show()
                btnToggleService.text = "Start Service"
            }
            refreshLog()
        }

        findViewById<Button>(R.id.btnRefreshLog).setOnClickListener {
            refreshLog()
        }

        findViewById<Button>(R.id.btnClearLog).setOnClickListener {
            clearLog()
        }

        findViewById<Button>(R.id.btnShareLog).setOnClickListener {
            shareLog()
        }

        val btnTogglePeriodic = findViewById<Button>(R.id.btnTogglePeriodic)
        btnTogglePeriodic.text = if (FocusService.isPeriodicCheckEnabled) "Periodic: ON" else "Periodic: OFF"
        btnTogglePeriodic.setOnClickListener {
            FocusService.isPeriodicCheckEnabled = !FocusService.isPeriodicCheckEnabled
            val stateStr = if (FocusService.isPeriodicCheckEnabled) "ON" else "OFF"
            btnTogglePeriodic.text = "Periodic: $stateStr"
            Toast.makeText(this, "15s Periodic Check is now $stateStr", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshLog()
        val btnTogglePeriodic = findViewById<Button>(R.id.btnTogglePeriodic)
        btnTogglePeriodic.text = if (FocusService.isPeriodicCheckEnabled) "Periodic: ON" else "Periodic: OFF"
        if (FocusService.instance != null) {
            btnToggleService.text = "Stop Service"
        } else {
            btnToggleService.text = "Start Service"
        }
    }

    private fun refreshLog() {
        try {
            val logFile = File(filesDir, "focus_test_log.txt")
            if (logFile.exists()) {
                val text = logFile.readText()
                tvLog.text = if (text.isNotEmpty()) text else "(Log file is empty)"
            } else {
                tvLog.text = "(No logs yet)"
            }
        } catch (e: Exception) {
            tvLog.text = "Error reading log: ${e.message}"
        }
    }

    private fun clearLog() {
        try {
            val logFile = File(filesDir, "focus_test_log.txt")
            if (logFile.exists()) {
                logFile.writeText("")
            }
            refreshLog()
            Toast.makeText(this, "Log cleared", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to clear log: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareLog() {
        try {
            val logFile = File(filesDir, "focus_test_log.txt")
            if (!logFile.exists() || logFile.length() == 0L) {
                Toast.makeText(this, "Log file is empty", Toast.LENGTH_SHORT).show()
                return
            }
            val uri: Uri = FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                logFile
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share Focus Test Log"))
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to share log: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
