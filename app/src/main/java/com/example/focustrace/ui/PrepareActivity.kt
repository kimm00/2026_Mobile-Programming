package com.example.focustrace.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.focustrace.data.db.entity.AllowedAppEntity
import com.example.focustrace.data.repo.SessionRepository
import com.example.focustrace.databinding.ActivityPrepareBinding
import com.example.focustrace.databinding.DialogAddSubjectBinding
import com.example.focustrace.detection.FocusService
import com.example.focustrace.sessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
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

    private val defaultSubjects = listOf("Programming", "Math", "English")
    private val addSubjectOption = "+ Add subject"
    private val subjectList = mutableListOf<String>()
    private val customSubjects = mutableListOf<String>()
    private lateinit var subjectAdapter: ArrayAdapter<String>
    private var selectedSubjectPosition = 0
    private var savedSubjectName: String? = null

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

        if (savedInstanceState != null) {
            savedSubjectName = savedInstanceState.getString(KEY_SELECTED_SUBJECT_NAME)
            savedInstanceState.getStringArrayList(KEY_CUSTOM_SUBJECTS)?.let { restored ->
                customSubjects.clear()
                customSubjects.addAll(restored)
            }
        }

        setupAppBar()
        setupSubjectSpinner()
        setupDurationPickers()

        if (savedInstanceState != null) {
            binding.npHours.value = savedInstanceState.getInt(KEY_NP_HOURS, 0)
            binding.npMinutes.value = savedInstanceState.getInt(KEY_NP_MINUTES, 0)
            updateDurationSummary()
        }

        setupAllowListCard()
        setupStartButton()

        observeAllowedApps()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val currentSelected = subjectList.getOrNull(selectedSubjectPosition)?.takeIf { it != addSubjectOption }
            ?: savedSubjectName
        outState.putString(KEY_SELECTED_SUBJECT_NAME, currentSelected)
        outState.putStringArrayList(KEY_CUSTOM_SUBJECTS, ArrayList(customSubjects))
        outState.putInt(KEY_NP_HOURS, binding.npHours.value)
        outState.putInt(KEY_NP_MINUTES, binding.npMinutes.value)
    }

    override fun onStart() {
        super.onStart()
        checkRunningSession()
    }

    private fun checkRunningSession() {
        lifecycleScope.launch {
            val runningSession = repository.observeRunningSession().firstOrNull()
            if (runningSession != null) {
                FocusService.startService(
                    context = this@PrepareActivity,
                    sessionId = runningSession.id,
                    goalMinutes = runningSession.goalMinutes
                )
                val intent = Intent(this@PrepareActivity, SessionActivity::class.java).apply {
                    putExtra(SessionActivity.EXTRA_SESSION_ID, runningSession.id)
                }
                startActivity(intent)
                finish()
            }
        }
    }

    private fun setupAppBar() {
        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun setupSubjectSpinner() {
        subjectAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            subjectList
        )
        binding.spinnerSubject.adapter = subjectAdapter

        binding.spinnerSubject.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (subjectList.getOrNull(position) == addSubjectOption) {
                    showAddSubjectDialog()
                } else {
                    selectedSubjectPosition = position
                    savedSubjectName = subjectList.getOrNull(position)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        observeSubjects()
    }

    private fun observeSubjects() {
        lifecycleScope.launch {
            repository.observeSubjects().collect { pastSubjects ->
                val targetSubject = savedSubjectName ?: subjectList.getOrNull(selectedSubjectPosition)
                val combined = (defaultSubjects + pastSubjects + customSubjects).distinct()

                subjectList.clear()
                subjectList.addAll(combined)
                subjectList.add(addSubjectOption)

                subjectAdapter.notifyDataSetChanged()

                val newPos = if (targetSubject != null && subjectList.contains(targetSubject)) {
                    subjectList.indexOf(targetSubject)
                } else {
                    0
                }
                selectedSubjectPosition = newPos
                savedSubjectName = subjectList.getOrNull(newPos)?.takeIf { it != addSubjectOption }
                binding.spinnerSubject.setSelection(newPos)
            }
        }
    }

    private fun showAddSubjectDialog() {
        val dialogBinding = DialogAddSubjectBinding.inflate(layoutInflater)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setPositiveButton("Add", null)
            .setNegativeButton("Cancel") { _, _ ->
                binding.spinnerSubject.setSelection(selectedSubjectPosition)
            }
            .setOnCancelListener {
                binding.spinnerSubject.setSelection(selectedSubjectPosition)
            }
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val newSubject = dialogBinding.etNewSubject.text?.toString()?.trim().orEmpty()
            if (newSubject.isBlank()) {
                dialogBinding.tilNewSubject.error = "Subject cannot be blank"
                return@setOnClickListener
            }
            dialogBinding.tilNewSubject.error = null

            val existingIndex = subjectList.indexOfFirst { it.equals(newSubject, ignoreCase = true) }
            if (existingIndex != -1 && subjectList[existingIndex] != addSubjectOption) {
                selectedSubjectPosition = existingIndex
                savedSubjectName = subjectList[existingIndex]
                binding.spinnerSubject.setSelection(existingIndex)
            } else {
                if (!customSubjects.contains(newSubject)) {
                    customSubjects.add(newSubject)
                }
                val addIndex = subjectList.indexOf(addSubjectOption)
                if (addIndex != -1) {
                    subjectList.add(addIndex, newSubject)
                    selectedSubjectPosition = addIndex
                } else {
                    subjectList.add(newSubject)
                    selectedSubjectPosition = subjectList.size - 1
                }
                savedSubjectName = newSubject
                subjectAdapter.notifyDataSetChanged()
                binding.spinnerSubject.setSelection(selectedSubjectPosition)
            }

            dialog.dismiss()
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
            value = 0
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

    private data class CandidateSpec(
        val title: String,
        val knownPkgs: List<String>,
        val pkgKw: List<String>,
        val labelKw: List<String>
    )

    private data class CandidateApp(
        val id: String,
        val displayTitle: String,
        val packageName: String,
        val isInstalled: Boolean,
        var isChecked: Boolean
    )

    private fun getCandidateApps(): List<CandidateApp> {
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

        val installedPackages = resolveInfos.map { it.activityInfo.packageName }.toSet()

        fun findInstalledPackage(
            knownPackages: List<String>,
            pkgKeywords: List<String>,
            labelKeywords: List<String>
        ): Pair<String, Boolean> {
            for (pkg in knownPackages) {
                if (pkg in installedPackages) return pkg to true
            }
            for (ri in resolveInfos) {
                val pkg = ri.activityInfo.packageName
                val label = ri.loadLabel(pm).toString().lowercase(java.util.Locale.ENGLISH)
                if (pkgKeywords.any { pkg.contains(it, ignoreCase = true) } ||
                    labelKeywords.any { label.contains(it, ignoreCase = true) }) {
                    return pkg to true
                }
            }
            return knownPackages.first() to false
        }

        val candidatesSpec = listOf(
            CandidateSpec(
                "Dictionary",
                listOf("com.naver.android.dic", "com.diotek.diodict4.eng.samsung", "com.google.android.apps.translate"),
                listOf("dic", "dictionary"),
                listOf("dictionary", "사전")
            ),
            CandidateSpec(
                "Calculator",
                listOf("com.google.android.calculator", "com.sec.android.app.popupcalculator", "com.apple.calculator", "com.android.calculator2"),
                listOf("calculator"),
                listOf("calculator", "계산기")
            ),
            CandidateSpec(
                "Papago",
                listOf("com.naver.labs.translator"),
                listOf("papago", "translator"),
                listOf("papago", "파파고")
            ),
            CandidateSpec(
                "Calendar",
                listOf("com.google.android.calendar", "com.samsung.android.calendar", "com.android.calendar"),
                listOf("calendar"),
                listOf("calendar", "달력", "캘린더")
            ),
            CandidateSpec(
                "Notion",
                listOf("notion.id"),
                listOf("notion"),
                listOf("notion")
            ),
            CandidateSpec(
                "YouTube",
                listOf("com.google.android.youtube"),
                listOf("youtube"),
                listOf("youtube")
            )
        )

        return candidatesSpec.map { spec ->
            val (actualPkg, installed) = findInstalledPackage(spec.knownPkgs, spec.pkgKw, spec.labelKw)
            val currentlyAllowed = currentAllowedApps.any { it.packageName == actualPkg }
            CandidateApp(
                id = spec.title.lowercase(java.util.Locale.ENGLISH),
                displayTitle = spec.title,
                packageName = actualPkg,
                isInstalled = installed,
                isChecked = installed && currentlyAllowed
            )
        }
    }

    private fun showAllowListDialog() {
        lifecycleScope.launch {
            val candidates = withContext(Dispatchers.IO) {
                getCandidateApps()
            }

            val dialogBinding = com.example.focustrace.databinding.DialogAllowListBinding.inflate(layoutInflater)
            val container = dialogBinding.layoutCandidateContainer

            val dialog = AlertDialog.Builder(this@PrepareActivity)
                .setView(dialogBinding.root)
                .create()

            fun updateOkButtonText() {
                val checkedCount = candidates.count { it.isChecked }
                dialogBinding.btnDialogOk.text = if (checkedCount > 0) {
                    "OK · $checkedCount app${if (checkedCount > 1) "s" else ""}"
                } else {
                    "OK"
                }
            }

            candidates.forEach { candidate ->
                val itemBinding = com.example.focustrace.databinding.ItemAllowListCandidateBinding.inflate(
                    layoutInflater, container, false
                )

                itemBinding.tvCandidateTitle.text = candidate.displayTitle
                itemBinding.cbCandidate.isChecked = candidate.isChecked

                if (!candidate.isInstalled) {
                    itemBinding.cbCandidate.isEnabled = false
                    itemBinding.cbCandidate.isChecked = false
                    itemBinding.tvCandidateTitle.alpha = 0.5f
                    itemBinding.tvCandidateSubtitle.text = "Not installed"
                    itemBinding.tvCandidateSubtitle.visibility = View.VISIBLE
                    itemBinding.root.isEnabled = false
                } else {
                    itemBinding.root.setOnClickListener {
                        candidate.isChecked = !candidate.isChecked
                        itemBinding.cbCandidate.isChecked = candidate.isChecked
                        updateOkButtonText()
                    }
                }

                container.addView(itemBinding.root)
            }

            updateOkButtonText()

            dialogBinding.btnDialogCancel.setOnClickListener {
                dialog.dismiss()
            }

            dialogBinding.btnDialogOk.setOnClickListener {
                dialog.dismiss()
                lifecycleScope.launch {
                    val selectedPackageNames = candidates.filter { it.isChecked }.map { it.packageName }.toSet()

                    for (candidate in candidates) {
                        if (candidate.isInstalled) {
                            repository.setAllowed(candidate.packageName, candidate.displayTitle, candidate.isChecked)
                        }
                    }

                    val extraAppsToUnset = currentAllowedApps.filter { it.packageName !in selectedPackageNames }
                    for (app in extraAppsToUnset) {
                        repository.setAllowed(app.packageName, app.appLabel, false)
                    }
                }
            }

            dialog.show()
        }
    }

    private fun setupStartButton() {
        binding.btnSession.setOnClickListener {
            binding.tilPlan.error = null

            val selectedItem = binding.spinnerSubject.selectedItem?.toString()
            if (selectedItem == null || selectedItem == addSubjectOption) {
                Toast.makeText(this, "Please select a subject", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val subjectText = selectedItem
            val planText = binding.etPlan.text?.toString()?.trim().orEmpty().ifBlank { null }

            val hours = binding.npHours.value
            val minutes = binding.npMinutes.value
            val goalMinutes = (hours * 60) + minutes

            var isValid = true

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
                val running = repository.observeRunningSession().firstOrNull()
                if (running != null) {
                    FocusService.startService(
                        context = this@PrepareActivity,
                        sessionId = running.id,
                        goalMinutes = running.goalMinutes
                    )
                    val intent = Intent(
                        this@PrepareActivity,
                        SessionActivity::class.java
                    ).apply {
                        putExtra(
                            SessionActivity.EXTRA_SESSION_ID,
                            running.id
                        )
                    }
                    startActivity(intent)
                    finish()
                    return@launch
                }

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

    private companion object {
        private const val KEY_SELECTED_SUBJECT_NAME = "key_selected_subject_name"
        private const val KEY_CUSTOM_SUBJECTS = "key_custom_subjects"
        private const val KEY_NP_HOURS = "key_np_hours"
        private const val KEY_NP_MINUTES = "key_np_minutes"
    }
}
