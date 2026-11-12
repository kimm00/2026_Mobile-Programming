package com.example.focustrace.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.focustrace.R
import com.example.focustrace.data.db.entity.AllowedAppEntity
import com.example.focustrace.data.repo.SessionRepository
import com.example.focustrace.databinding.ActivityPrepareBinding
import com.example.focustrace.detection.FocusService
import com.example.focustrace.sessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PrepareActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPrepareBinding

    private val repository: SessionRepository by lazy {
        sessionRepository
    }

    private var currentAllowedApps = listOf<AllowedAppEntity>()

    private var pendingSubject: String? = null
    private var pendingPlan: String? = null
    private var pendingGoalMinutes: Int? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        val subject = pendingSubject
        val plan = pendingPlan
        val goalMinutes = pendingGoalMinutes
        if (subject != null && goalMinutes != null) {
            pendingSubject = null
            pendingPlan = null
            pendingGoalMinutes = null
            performStartSession(subject, plan, goalMinutes)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityPrepareBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupAppBar()
        setupDurationPickers()
        setupAllowListCard()
        setupStartButton()

        observeAllowedApps()
    }

    private fun setupAppBar() {
        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun setupDurationPickers() {
        binding.npHours.apply {
            minValue = 0
            maxValue = 12
            value = 0
            setOnValueChangedListener { _, _, _ -> updateDurationSummary() }
        }

        binding.npMinutes.apply {
            minValue = 0
            maxValue = 59
            value = 45
            setOnValueChangedListener { _, _, _ -> updateDurationSummary() }
        }

        updateDurationSummary()
    }

    private fun updateDurationSummary() {
        val hours = binding.npHours.value
        val mins = binding.npMinutes.value
        binding.tvDurationSummary.text = "Selected: ${hours} h ${mins} min"
    }

    private fun setupAllowListCard() {
        binding.btnAllowList.setOnClickListener {
            showAllowListDialog()
        }
    }

    private fun observeAllowedApps() {
        lifecycleScope.launch {
            repository.observeAllowedApps().collect { allowedApps ->
                currentAllowedApps = allowedApps
                val count = allowedApps.size
                binding.tvAllowedAppsSummary.text = when {
                    count == 0 -> "No allowed apps selected"
                    count == 1 -> "${allowedApps[0].appLabel} · 1 app"
                    count == 2 -> "${allowedApps[0].appLabel}, ${allowedApps[1].appLabel} · 2 apps"
                    else -> "${allowedApps[0].appLabel}, ${allowedApps[1].appLabel} · $count apps"
                }
            }
        }
    }

    private fun showAllowListDialog() {
        lifecycleScope.launch {
            val installedApps = withContext(Dispatchers.IO) {
                getInstalledAppsForAllowList()
            }

            if (installedApps.isEmpty()) {
                Toast.makeText(this@PrepareActivity, "No launchable study apps found", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val appLabels = installedApps.map { it.second }.toTypedArray()
            val booleanChecked = installedApps.map { (pkg, _) ->
                currentAllowedApps.any { it.packageName == pkg }
            }.toBooleanArray()

            AlertDialog.Builder(this@PrepareActivity)
                .setTitle("Allowed apps")
                .setMultiChoiceItems(appLabels, booleanChecked) { _, which, isChecked ->
                    booleanChecked[which] = isChecked
                }
                .setPositiveButton("OK") { _, _ ->
                    lifecycleScope.launch {
                        for (i in installedApps.indices) {
                            val (pkg, label) = installedApps[i]
                            val isChecked = booleanChecked[i]
                            repository.setAllowed(pkg, label, isChecked)
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun getInstalledAppsForAllowList(): List<Pair<String, String>> {
        val pm = packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(mainIntent, 0)
        }

        val currentPkg = packageName
        val appList = mutableListOf<Pair<String, String>>()

        for (ri in resolveInfos) {
            val pkgName = ri.activityInfo.packageName
            if (pkgName == currentPkg || pkgName.startsWith("com.android.systemui")) continue
            val label = ri.loadLabel(pm).toString()
            if (label.isNotBlank()) {
                appList.add(pkgName to label)
            }
        }
        return appList.distinctBy { it.first }.sortedBy { it.second }
    }

    private fun setupStartButton() {
        binding.btnSession.setOnClickListener {
            binding.tilSubject.error = null
            binding.tilPlan.error = null

            val subjectText = binding.etSubject.text?.toString()?.trim().orEmpty()
            val planText = binding.etPlan.text?.toString()?.trim().orEmpty().ifBlank { null }

            val hours = binding.npHours.value
            val minutes = binding.npMinutes.value
            val goalMinutes = (hours * 60) + minutes

            var isValid = true

            if (subjectText.isBlank()) {
                binding.tilSubject.error = "Subject cannot be blank"
                isValid = false
            }

            if (goalMinutes < 1) {
                Toast.makeText(this, "Target duration must be at least 1 minute", Toast.LENGTH_SHORT).show()
                isValid = false
            }

            if (!isValid) return@setOnClickListener

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                pendingSubject = subjectText
                pendingPlan = planText
                pendingGoalMinutes = goalMinutes
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                performStartSession(subjectText, planText, goalMinutes)
            }
        }
    }

    private fun performStartSession(subjectText: String, planText: String?, goalMinutes: Int) {
        binding.btnSession.isEnabled = false

        lifecycleScope.launch {
            try {
                val sessionId = repository.startSession(
                    subject = subjectText,
                    plan = planText,
                    goalMinutes = goalMinutes
                )

                FocusService.startService(
                    context = this@PrepareActivity,
                    sessionId = sessionId,
                    goalMinutes = goalMinutes
                )

                val intent = Intent(
                    this@PrepareActivity,
                    SessionActivity::class.java
                ).apply {
                    putExtra(
                        SessionActivity.EXTRA_SESSION_ID,
                        sessionId
                    )
                }

                startActivity(intent)
                finish()
            } catch (e: Exception) {
                Toast.makeText(
                    this@PrepareActivity,
                    "Failed to start session",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                binding.btnSession.isEnabled = true
            }
        }
    }
}
