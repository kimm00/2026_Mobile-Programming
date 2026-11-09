package com.example.focustrace.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import android.util.Log
import com.example.focustrace.R
import com.example.focustrace.ai.AiFocusInput
import com.example.focustrace.ai.AiInsightSampleData
import com.example.focustrace.ai.AiInsightService
import com.example.focustrace.ai.DefaultFirebaseAiClient
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.launch

class AiInsightTestActivity : AppCompatActivity() {

    private val aiService by lazy { AiInsightService() }
    private val rawAiClient by lazy { DefaultFirebaseAiClient() }

    private lateinit var rgSampleData: RadioGroup
    private lateinit var rbInsufficient: RadioButton
    private lateinit var rbHighInterruption: RadioButton
    private lateinit var swForceFallback: MaterialSwitch
    private lateinit var btnTestMinimalCall: Button
    private lateinit var btnGenerate: Button
    private lateinit var progressLoading: ProgressBar
    private lateinit var cardResult: MaterialCardView
    private lateinit var chipSource: Chip
    private lateinit var tvDuration: TextView
    private lateinit var tvInsights: TextView
    private lateinit var tvStatusMessage: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_ai_insight_test)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        bindViews()

        btnTestMinimalCall.setOnClickListener {
            runMinimalCallTest()
        }

        btnGenerate.setOnClickListener {
            runInsightGeneration()
        }
    }

    private fun bindViews() {
        rgSampleData = findViewById(R.id.rgSampleData)
        rbInsufficient = findViewById(R.id.rbInsufficient)
        rbHighInterruption = findViewById(R.id.rbHighInterruption)
        swForceFallback = findViewById(R.id.swForceFallback)
        btnTestMinimalCall = findViewById(R.id.btnTestMinimalCall)
        btnGenerate = findViewById(R.id.btnGenerateInsights)
        progressLoading = findViewById(R.id.progressLoading)
        cardResult = findViewById(R.id.cardResult)
        chipSource = findViewById(R.id.chipSource)
        tvDuration = findViewById(R.id.tvRecommendedDuration)
        tvInsights = findViewById(R.id.tvInsights)
        tvStatusMessage = findViewById(R.id.tvStatusMessage)
    }

    private fun getSelectedInput(): AiFocusInput {
        return when (rgSampleData.checkedRadioButtonId) {
            R.id.rbInsufficient -> AiInsightSampleData.SAMPLE_INSUFFICIENT
            R.id.rbHighInterruption -> AiInsightSampleData.SAMPLE_HIGH_INTERRUPTION
            else -> AiInsightSampleData.SAMPLE_SUFFICIENT
        }
    }

    private fun runMinimalCallTest() {
        btnTestMinimalCall.isEnabled = false
        btnGenerate.isEnabled = false
        progressLoading.visibility = View.VISIBLE
        cardResult.visibility = View.GONE

        lifecycleScope.launch {
            val modelName = "gemini-3.5-flash-lite"
            val prompt = "Reply with OK"
            try {
                Log.d("AiTest", "Starting minimal test call: Model=$modelName, Prompt='$prompt'")
                val responseText = rawAiClient.testMinimalCall(modelName = modelName, prompt = prompt)

                cardResult.visibility = View.VISIBLE
                chipSource.text = "Source: Gemini Minimal Call"
                tvDuration.text = "Minimal Call OK"
                tvInsights.text = responseText
                tvStatusMessage.text = "Success ($modelName via Server): Response received successfully."
            } catch (e: Exception) {
                val exceptionClass = e.javaClass.name
                val errorMsg = e.message ?: "No detailed error message"
                Log.e("AiTest", "Minimal Call FAILED! Exception: $exceptionClass | Message: $errorMsg", e)

                cardResult.visibility = View.VISIBLE
                chipSource.text = "Source: Minimal Call Failure"
                tvDuration.text = "Call Failed"
                tvInsights.text = "Exception: $exceptionClass\nMessage: $errorMsg"
                tvStatusMessage.text = "Failed ($modelName): [$exceptionClass] $errorMsg"
            } finally {
                btnTestMinimalCall.isEnabled = true
                btnGenerate.isEnabled = true
                progressLoading.visibility = View.GONE
            }
        }
    }

    private fun runInsightGeneration() {
        val input = getSelectedInput()
        val forceFallback = swForceFallback.isChecked

        btnTestMinimalCall.isEnabled = false
        btnGenerate.isEnabled = false
        progressLoading.visibility = View.VISIBLE
        cardResult.visibility = View.GONE

        lifecycleScope.launch {
            val result = aiService.generateInsights(input, forceFallback = forceFallback)

            btnTestMinimalCall.isEnabled = true
            btnGenerate.isEnabled = true
            progressLoading.visibility = View.GONE
            cardResult.visibility = View.VISIBLE

            tvDuration.text = "${result.recommendedDurationMinutes} Minutes"

            val formattedInsights = result.insights.mapIndexed { index, insight ->
                "${index + 1}. $insight"
            }.joinToString("\n\n")
            tvInsights.text = formattedInsights

            chipSource.text = if (result.source == "AI") {
                "Source: Gemini (${result.modelName ?: "AI"})"
            } else {
                "Source: Rule Fallback Engine"
            }

            tvStatusMessage.text = result.statusMessage ?: ""
        }
    }
}
