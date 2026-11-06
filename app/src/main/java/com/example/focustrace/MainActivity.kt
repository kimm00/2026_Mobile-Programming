package com.example.focustrace

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.focustrace.detection.FocusService
import com.example.focustrace.detection.TestLogger
import com.example.focustrace.detection.UsageStatsHelper

class MainActivity : AppCompatActivity() {

    private lateinit var tvLogView: TextView
    private lateinit var scrollViewLog: ScrollView
    private lateinit var btnTogglePolling: Button

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show()
            }
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

        tvLogView = findViewById(R.id.tvLogView)
        scrollViewLog = findViewById(R.id.scrollViewLog)
        btnTogglePolling = findViewById(R.id.btnTogglePolling)

        updatePollingButtonText()

        findViewById<Button>(R.id.btnUsageStats).setOnClickListener {
            UsageStatsHelper.openUsageStatsSettings(this)
        }

        findViewById<Button>(R.id.btnNotificationPerm).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                Toast.makeText(this, "Notification permission not required on API < 33", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnBatteryOpt).setOnClickListener {
            openBatteryOptimizationSettings()
        }

        findViewById<Button>(R.id.btnStartService).setOnClickListener {
            FocusService.startService(this)
            Toast.makeText(this, "FocusService started", Toast.LENGTH_SHORT).show()
            loadLogAndScrollToBottom()
        }

        findViewById<Button>(R.id.btnStopService).setOnClickListener {
            FocusService.stopService(this)
            Toast.makeText(this, "FocusService stopped", Toast.LENGTH_SHORT).show()
            loadLogAndScrollToBottom()
        }

        btnTogglePolling.setOnClickListener {
            val isEnabled = FocusService.toggleRefocusPolling(this)
            updatePollingButtonText()
            Toast.makeText(this, "Refocus Polling ${if (isEnabled) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
            loadLogAndScrollToBottom()
        }

        findViewById<Button>(R.id.btnRefreshLog).setOnClickListener {
            loadLogAndScrollToBottom()
        }

        findViewById<Button>(R.id.btnClearLog).setOnClickListener {
            TestLogger.clearLog(this)
            loadLogAndScrollToBottom()
            Toast.makeText(this, "Log cleared", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnShareLog).setOnClickListener {
            shareLogFile()
        }
    }

    override fun onResume() {
        super.onResume()
        updatePollingButtonText()
        loadLogAndScrollToBottom()
    }

    private fun updatePollingButtonText() {
        btnTogglePolling.text = if (FocusService.isRefocusCheckEnabled) "Polling: ON" else "Polling: OFF"
    }

    private fun loadLogAndScrollToBottom() {
        val logContent = TestLogger.readLog(this)
        tvLogView.text = logContent
        scrollViewLog.post {
            scrollViewLog.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    private fun openBatteryOptimizationSettings() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            startActivity(fallbackIntent)
        }
    }

    private fun shareLogFile() {
        val logFile = TestLogger.getLogFile(this)
        if ((!logFile.exists() || logFile.length() == 0L)) {
            Toast.makeText(this, "Log file is empty.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val uri: Uri = FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                logFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share FocusTrace Test Log"))
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to share log file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
