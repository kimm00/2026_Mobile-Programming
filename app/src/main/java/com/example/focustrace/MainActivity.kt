package com.example.focustrace

import android.app.AppOpsManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.focustrace.ai.AiInsightService
import com.example.focustrace.ai.RoomAiFocusInputBuilder
import com.example.focustrace.data.model.PeriodType
import com.example.focustrace.data.repo.ReportRepository
import com.example.focustrace.databinding.ActivityMainBinding
import com.example.focustrace.databinding.BottomSheetProfileBinding
import com.example.focustrace.ui.AiInsightTestActivity
import com.example.focustrace.ui.PrepareActivity
import com.example.focustrace.ui.ReportActivity
import com.example.focustrace.ui.UsagePermissionActivity
import com.example.focustrace.ui.report.FocusBarChartBinder
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val zoneId: ZoneId = ZoneId.systemDefault()

    private val reportRepo: ReportRepository by lazy {
        reportRepository
    }

    private val aiInsightService by lazy {
        AiInsightService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.mainRoot) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupHeaderAndGreeting()
        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        loadTodaySummary()
        loadWeeklyProgress()
        loadAiRecommendation()
    }

    private fun setupHeaderAndGreeting() {
        val now = LocalTime.now(zoneId)
        val greetingStr = when (now.hour) {
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }
        binding.tvGreeting.text = greetingStr

        val todayDate = LocalDate.now(zoneId)
        val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH)
        binding.tvCurrentDate.text = todayDate.format(dateFormatter)
    }

    private fun setupClickListeners() {
        binding.btnProfile.setOnClickListener {
            showProfileBottomSheet()
        }

        binding.btnStartFocus.setOnClickListener {
            if (isUsagePermissionGranted()) {
                startActivity(Intent(this, PrepareActivity::class.java))
            } else {
                startActivity(Intent(this, UsagePermissionActivity::class.java))
            }
        }

        binding.btnAiInsightCard.setOnClickListener {
            startActivity(Intent(this, AiInsightTestActivity::class.java))
        }

        binding.btnNavHome.setOnClickListener {
            // Already on Home
        }

        binding.btnNavReport.setOnClickListener {
            startActivity(Intent(this, ReportActivity::class.java))
        }

        binding.btnNavJournal.setOnClickListener {
            startActivity(Intent(this, com.example.focustrace.ui.JournalActivity::class.java))
        }

        binding.btnNotifications.setOnClickListener {
            Toast.makeText(this, "Notifications feature coming soon", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadTodaySummary() {
        lifecycleScope.launch {
            try {
                val today = LocalDate.now(zoneId)
                val startOfDay = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
                val endOfDay = today.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1

                val report = withContext(Dispatchers.IO) {
                    reportRepo.getReport(PeriodType.DAY, startOfDay, endOfDay, zoneId)
                }

                val totalMinutes = report.summary.totalFocusMs / 60000
                val hours = totalMinutes / 60
                val mins = totalMinutes % 60
                val formattedFocusTime = if (hours > 0L) "${hours}h ${mins}m" else "${mins}m"

                binding.tvTodayFocusTotal.text = formattedFocusTime
                binding.tvTodayCheckCount.text = report.summary.totalCheckCount.toString()

                val longestMins = report.summary.longestFocusMs / 60000
                binding.tvTodayLongestFocus.text = "${longestMins} min"
            } catch (e: Exception) {
                // Fallback on error
                binding.tvTodayFocusTotal.text = "0m"
                binding.tvTodayCheckCount.text = "0"
                binding.tvTodayLongestFocus.text = "0 min"
            }
        }
    }

    private fun loadWeeklyProgress() {
        lifecycleScope.launch {
            try {
                val today = LocalDate.now(zoneId)
                val startOfWeek = today.minusDays(6).atStartOfDay(zoneId).toInstant().toEpochMilli()
                val endOfWeek = today.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1

                val report = withContext(Dispatchers.IO) {
                    reportRepo.getReport(PeriodType.WEEK, startOfWeek, endOfWeek, zoneId)
                }

                val totalMinutes = report.summary.totalFocusMs / 60000
                val hours = totalMinutes / 60
                val mins = totalMinutes % 60
                binding.tvWeeklyTotal.text = "${hours}h ${mins}m"

                FocusBarChartBinder.bindChart(binding.chartWeeklyFocus, report.dailyStats)

                val startLabel = today.minusDays(6).format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH))
                val endLabel = today.format(DateTimeFormatter.ofPattern("d", Locale.ENGLISH))
                binding.tvWeeklyChartSubtext.text = "$startLabel–$endLabel · focus minutes per day"
            } catch (e: Exception) {
                binding.tvWeeklyTotal.text = "0h 0m"
            }
        }
    }

    private fun loadAiRecommendation() {
        lifecycleScope.launch {
            try {
                val sessions = withContext(Dispatchers.IO) {
                    sessionRepository.getSessionsByPeriod(0L, System.currentTimeMillis())
                }
                val input = RoomAiFocusInputBuilder.buildFromSessions(sessions)
                val insight = aiInsightService.generateInsights(input)

                binding.tvRecommendedDuration.text = "Recommended: ${insight.recommendedDurationMinutes}-min session"

                if (insight.insights.isNotEmpty()) {
                    binding.tvAiInsightRecommendation.text = insight.insights[0]
                    if (insight.insights.size > 1) {
                        binding.tvAiInsightSupporting.text = insight.insights[1]
                    }
                }
            } catch (e: Exception) {
                binding.tvRecommendedDuration.text = "Recommended: 45-min session"
            }
        }
    }

    private fun showProfileBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val sheetBinding = BottomSheetProfileBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        // Existing profile info (or guest default)
        sheetBinding.tvProfileName.text = "Maya"
        sheetBinding.tvProfileEmail.text = "maya@example.com"
        sheetBinding.tvProfileAvatar.text = "M"

        sheetBinding.tvSignOutLabel.text = "개발용: 로그인 화면 보기"

        sheetBinding.btnProfilePermissions.setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, UsagePermissionActivity::class.java))
        }

        sheetBinding.btnProfileSignOut.setOnClickListener {
            dialog.dismiss()
            val intent = Intent(this, com.example.focustrace.ui.LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            finish()
        }

        dialog.show()
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
